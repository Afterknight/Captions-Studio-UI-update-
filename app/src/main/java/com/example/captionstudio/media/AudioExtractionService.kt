package com.example.captionstudio.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SpeechSegment(
  val startMs: Long,
  val endMs: Long,
  val peakEnergy: Float,
)

data class ExtractedAudioTrack(
  val sampleRateHz: Int = 16000,
  val pcmMono16k: ShortArray,
  val durationMs: Long,
  val energyEnvelope10ms: FloatArray,
  val speechSegments: List<SpeechSegment>,
  val waveformBins: List<Float>,
) {
  val hasSpokenAudio: Boolean
    get() = pcmMono16k.isNotEmpty() && speechSegments.isNotEmpty()
}

/**
 * Offline MediaExtractor + MediaCodec decoder that extracts an imported video's audio track
 * into 16kHz 16-bit mono PCM alongside a 10ms RMS speech-energy envelope and VAD segments.
 */
object AudioExtractionService {

  private const val TARGET_SAMPLE_RATE = 16000
  private const val MAX_DECODE_DURATION_MS = 180_000L // Cap safely at 3 min for ordinary devices
  private const val ENVELOPE_FRAME_MS = 10

  suspend fun extractAudioTrack(
    context: Context,
    videoUri: Uri,
    onProgress: (Float) -> Unit = {},
  ): ExtractedAudioTrack? =
    withContext(Dispatchers.IO) {
      val extractor = MediaExtractor()
      var codec: MediaCodec? = null
      try {
        extractor.setDataSource(context, videoUri, null)
        var audioTrackIndex = -1
        var trackFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
          val fmt = extractor.getTrackFormat(i)
          val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
          if (mime.startsWith("audio/")) {
            audioTrackIndex = i
            trackFormat = fmt
            break
          }
        }
        if (audioTrackIndex < 0 || trackFormat == null) return@withContext null

        extractor.selectTrack(audioTrackIndex)
        val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
        val trackDurationUs =
          if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
            trackFormat.getLong(MediaFormat.KEY_DURATION)
          } else 0L
        val maxUs = MAX_DECODE_DURATION_MS * 1000L

        var sampleRate =
          if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          } else 44100
        var channelCount =
          if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
          } else 1
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(trackFormat, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val monoAccumulator = ShortListBuffer(initialCapacity = TARGET_SAMPLE_RATE * 10)
        var sawInputEos = false
        var sawOutputEos = false
        var lastReportedProgress = 0f

        while (!sawOutputEos) {
          if (!sawInputEos) {
            val inIdx = codec.dequeueInputBuffer(10_000L)
            if (inIdx >= 0) {
              val inputBuf = codec.getInputBuffer(inIdx)
              if (inputBuf != null) {
                val sampleSize = extractor.readSampleData(inputBuf, 0)
                val sampleTimeUs = extractor.sampleTime
                if (sampleSize < 0 || sampleTimeUs > maxUs) {
                  codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                  sawInputEos = true
                } else {
                  codec.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, 0)
                  extractor.advance()
                  if (trackDurationUs > 0L) {
                    val p = (sampleTimeUs.toFloat() / min(trackDurationUs, maxUs)).coerceIn(0f, 1f)
                    if (p - lastReportedProgress >= 0.05f) {
                      lastReportedProgress = p
                      onProgress(p)
                    }
                  }
                }
              }
            }
          }

          val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
          when {
            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              val outFmt = codec.outputFormat
              if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
              }
              if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channelCount = max(1, outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
              }
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
              ) {
                pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
              }
            }

            outIdx >= 0 -> {
              val outBuf = codec.getOutputBuffer(outIdx)
              if (outBuf != null && bufferInfo.size > 0) {
                outBuf.position(bufferInfo.offset)
                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                val monoChunk = decodeToMono16k(outBuf, sampleRate, channelCount, pcmEncoding)
                monoAccumulator.appendAll(monoChunk)
              }
              codec.releaseOutputBuffer(outIdx, false)
              if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                sawOutputEos = true
              }
            }
          }
        }

        val pcm16k = monoAccumulator.toShortArray()
        if (pcm16k.isEmpty()) return@withContext null

        val durationMs = (pcm16k.size * 1000L) / TARGET_SAMPLE_RATE
        val envelope = computeEnergyEnvelope10ms(pcm16k)
        val segments = detectSpeechSegments(envelope)
        val waveform = downsampleWaveform(envelope, 48)
        onProgress(1f)

        ExtractedAudioTrack(
          sampleRateHz = TARGET_SAMPLE_RATE,
          pcmMono16k = pcm16k,
          durationMs = durationMs,
          energyEnvelope10ms = envelope,
          speechSegments = segments,
          waveformBins = waveform,
        )
      } catch (_: Exception) {
        null
      } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor.release() }
      }
    }

  private fun decodeToMono16k(
    buffer: ByteBuffer,
    sourceSampleRate: Int,
    channelCount: Int,
    pcmEncoding: Int,
  ): ShortArray {
    val ordered = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
    val monoSource: ShortArray =
      if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
        val floatCount = ordered.remaining() / 4
        val frameCount = floatCount / max(1, channelCount)
        ShortArray(frameCount) { fIdx ->
          var acc = 0f
          for (ch in 0 until channelCount) {
            acc += ordered.getFloat()
          }
          ((acc / channelCount).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
      } else {
        val shortCount = ordered.remaining() / 2
        val frameCount = shortCount / max(1, channelCount)
        ShortArray(frameCount) { _ ->
          var acc = 0L
          for (ch in 0 until channelCount) {
            acc += ordered.getShort().toLong()
          }
          (acc / channelCount).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
      }

    if (sourceSampleRate == TARGET_SAMPLE_RATE || monoSource.size <= 1) {
      return monoSource
    }

    val ratio = sourceSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
    val outLen = max(1, (monoSource.size / ratio).toInt())
    return ShortArray(outLen) { outIdx ->
      val srcPos = outIdx * ratio
      val i0 = srcPos.toInt().coerceIn(0, monoSource.lastIndex)
      val i1 = min(i0 + 1, monoSource.lastIndex)
      val frac = srcPos - i0
      val interpolated = monoSource[i0] * (1.0 - frac) + monoSource[i1] * frac
      interpolated.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
  }

  /**
   * Computes normalized RMS speech energy at 10ms resolution (160 samples per frame at 16kHz).
   */
  fun computeEnergyEnvelope10ms(pcm16k: ShortArray): FloatArray {
    val samplesPerFrame = (TARGET_SAMPLE_RATE * ENVELOPE_FRAME_MS) / 1000 // 160
    if (pcm16k.isEmpty()) return FloatArray(0)
    val frameCount = max(1, (pcm16k.size + samplesPerFrame - 1) / samplesPerFrame)
    val out = FloatArray(frameCount)
    for (f in 0 until frameCount) {
      val start = f * samplesPerFrame
      val end = min(pcm16k.size, start + samplesPerFrame)
      var sumSq = 0.0
      for (i in start until end) {
        val norm = pcm16k[i] / 32768.0
        sumSq += norm * norm
      }
      val count = max(1, end - start)
      out[f] = sqrt(sumSq / count).toFloat()
    }
    return out
  }

  /**
   * Adaptive voice-activity detector grouping 10ms frames into continuous spoken segments
   * while splitting natural pauses >= 360ms (and trimming silent lead/tail).
   */
  fun detectSpeechSegments(
    envelope10ms: FloatArray,
    maxSegmentMs: Long = 15_000L,
  ): List<SpeechSegment> {
    if (envelope10ms.isEmpty()) return emptyList()
    val sorted = envelope10ms.sortedArray()
    val noiseFloor = sorted[(sorted.size * 0.20f).toInt().coerceIn(0, sorted.lastIndex)]
    val peak = sorted[(sorted.size * 0.96f).toInt().coerceIn(0, sorted.lastIndex)]
    if (peak < 0.004f) return emptyList()

    val threshold = max(0.010f, max(noiseFloor * 2.4f, peak * 0.14f))
    val minSilenceGapFrames = 36 // 360ms natural pause triggers a chunk boundary
    val padFrames = 12 // 120ms pre/post roll

    val segments = mutableListOf<SpeechSegment>()
    var activeStart = -1
    var lastVoicedFrame = -1
    var peakInSeg = 0f

    for (i in envelope10ms.indices) {
      val e = envelope10ms[i]
      if (e >= threshold) {
        if (activeStart < 0) {
          activeStart = max(0, i - padFrames)
          peakInSeg = e
        } else if (e > peakInSeg) {
          peakInSeg = e
        }
        lastVoicedFrame = i
      } else if (activeStart >= 0) {
        val silenceRun = i - lastVoicedFrame
        val currentDurMs = (i - activeStart) * ENVELOPE_FRAME_MS.toLong()
        if (silenceRun >= minSilenceGapFrames || currentDurMs >= maxSegmentMs) {
          val segStartMs = activeStart * ENVELOPE_FRAME_MS.toLong()
          val segEndMs = min(envelope10ms.size, lastVoicedFrame + padFrames) * ENVELOPE_FRAME_MS.toLong()
          if (segEndMs - segStartMs >= 220L) {
            segments.add(SpeechSegment(segStartMs, segEndMs, peakInSeg))
          }
          activeStart = -1
          lastVoicedFrame = -1
          peakInSeg = 0f
        }
      }
    }

    if (activeStart >= 0 && lastVoicedFrame >= 0) {
      val segStartMs = activeStart * ENVELOPE_FRAME_MS.toLong()
      val segEndMs = min(envelope10ms.size, lastVoicedFrame + padFrames) * ENVELOPE_FRAME_MS.toLong()
      if (segEndMs - segStartMs >= 220L) {
        segments.add(SpeechSegment(segStartMs, segEndMs, peakInSeg))
      }
    }
    return segments
  }

  /**
   * Encodes a slice of [pcm16k] into a valid 44-byte RIFF/WAV byte array.
   * Optionally downsamples 2x to 8kHz to keep long Gemini audio payloads compact.
   */
  fun encodeWavBytes(
    pcm16k: ShortArray,
    startMs: Long = 0L,
    endMs: Long = (pcm16k.size * 1000L) / TARGET_SAMPLE_RATE,
    downsampleTo8k: Boolean = false,
  ): ByteArray {
    val startIdx = ((startMs * TARGET_SAMPLE_RATE) / 1000L).toInt().coerceIn(0, pcm16k.size)
    val endIdx = ((endMs * TARGET_SAMPLE_RATE) / 1000L).toInt().coerceIn(startIdx, pcm16k.size)
    val step = if (downsampleTo8k) 2 else 1
    val outRate = if (downsampleTo8k) 8000 else TARGET_SAMPLE_RATE
    val sampleCount = max(0, (endIdx - startIdx) / step)
    val dataBytes = sampleCount * 2

    val out = ByteArrayOutputStream(44 + dataBytes)
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + dataBytes)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // PCM chunk size
    header.putShort(1) // PCM format
    header.putShort(1) // Mono
    header.putInt(outRate)
    header.putInt(outRate * 2) // Byte rate
    header.putShort(2) // Block align
    header.putShort(16) // Bits per sample
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataBytes)
    out.write(header.array())

    val dataBuf = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    var i = startIdx
    var written = 0
    while (i < endIdx && written < sampleCount) {
      dataBuf.putShort(pcm16k[i])
      i += step
      written++
    }
    out.write(dataBuf.array())
    return out.toByteArray()
  }

  private fun downsampleWaveform(envelope: FloatArray, binCount: Int): List<Float> {
    if (envelope.isEmpty() || binCount <= 0) return emptyList()
    val maxVal = envelope.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
    val step = max(1f, envelope.size.toFloat() / binCount)
    return List(binCount) { bin ->
      val start = (bin * step).toInt().coerceIn(0, envelope.lastIndex)
      val end = ((bin + 1) * step).toInt().coerceIn(start + 1, envelope.size)
      var peak = 0f
      for (i in start until end) {
        peak = max(peak, envelope[i])
      }
      (peak / maxVal).coerceIn(0.05f, 1f)
    }
  }

  private class ShortListBuffer(initialCapacity: Int) {
    private var data = ShortArray(max(256, initialCapacity))
    private var size = 0

    fun appendAll(chunk: ShortArray) {
      if (chunk.isEmpty()) return
      if (size + chunk.size > data.size) {
        val newCap = max(data.size * 2, size + chunk.size)
        data = data.copyOf(newCap)
      }
      System.arraycopy(chunk, 0, data, size, chunk.size)
      size += chunk.size
    }

    fun toShortArray(): ShortArray = data.copyOf(size)
  }
}

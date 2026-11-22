package com.example.captionstudio.media

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import com.example.captionstudio.ai.GeminiApiClient
import com.example.captionstudio.ai.GeminiApiResult
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.engine.AudioSpeechAligner
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class AutoTranscribeUiState(
  val isRunning: Boolean = false,
  val stageLabel: String = "",
  val progressFraction: Float = 0f,
  val waveformBins: List<Float> = emptyList(),
  val segments: List<VoskTextSegment> = emptyList(),
  val lastSummary: String? = null,
  val lastError: String? = null,
)

sealed interface AutoTranscribeResult {
  data class Success(
    val transcript: String,
    val wordTimings: List<WordTiming>,
    val waveformBins: List<Float>,
    val engineLabel: String,
    val segments: List<VoskTextSegment> = emptyList(),
  ) : AutoTranscribeResult

  data class NoSpeechDetected(
    val waveformBins: List<Float>,
    val message: String,
  ) : AutoTranscribeResult

  data class Unavailable(
    val waveformBins: List<Float>,
    val reason: String,
  ) : AutoTranscribeResult
}

/**
 * Coordinates automatic transcription of imported video audio tracks in both Offline Mode
 * ([VoskVideoTranscriptionService] with embedded Kaldi/Vosk ASR + 10ms acoustic alignment,
 * plus an Android 13+ system SpeechRecognizer fallback) and Online AI Mode (Gemini audio-to-word-timings).
 */
class VideoAutoTranscriber(private val context: Context) {

  val voskService = VoskVideoTranscriptionService(context)

  private val _state = MutableStateFlow(AutoTranscribeUiState())
  val state: StateFlow<AutoTranscribeUiState> = _state.asStateFlow()

  fun clearError() {
    voskService.clearError()
    _state.value = _state.value.copy(lastError = null)
  }

  fun cancel() {
    voskService.cancel()
  }

  suspend fun transcribeVideo(
    videoUri: Uri,
    languageTag: String,
    useOnlineGemini: Boolean,
    geminiApiKey: String? = null,
    geminiModelName: String = "",
  ): AutoTranscribeResult {
    _state.value =
      AutoTranscribeUiState(
        isRunning = true,
        stageLabel = "Decoding audio track from video...",
        progressFraction = 0.08f,
      )

    val extracted =
      AudioExtractionService.extractAudioTrack(context, videoUri) { p ->
        _state.value =
          _state.value.copy(
            stageLabel = "Extracting 16kHz speech waveform...",
            progressFraction = 0.08f + p * 0.25f,
          )
      }

    if (extracted == null) {
      val msg = "No audio track found in this video."
      _state.value = AutoTranscribeUiState(isRunning = false, lastError = msg)
      return AutoTranscribeResult.NoSpeechDetected(emptyList(), msg)
    }

    if (!extracted.hasSpokenAudio) {
      val msg = "Audio track has no detectable speech energy."
      _state.value =
        AutoTranscribeUiState(
          isRunning = false,
          waveformBins = extracted.waveformBins,
          lastSummary = msg,
        )
      return AutoTranscribeResult.NoSpeechDetected(extracted.waveformBins, msg)
    }

    _state.value =
      _state.value.copy(
        waveformBins = extracted.waveformBins,
        stageLabel =
          if (useOnlineGemini) "Sending extracted speech to Gemini..."
          else "Running on-device speech recognition...",
        progressFraction = 0.36f,
      )

    val result =
      if (useOnlineGemini && !geminiApiKey.isNullOrBlank() && geminiModelName.isNotBlank()) {
        transcribeWithGeminiAndAlign(
          extracted = extracted,
          apiKey = geminiApiKey,
          modelName = geminiModelName,
          languageTag = languageTag,
        )
      } else {
        transcribeOfflineWithAcousticAlignment(
          extracted = extracted,
          languageTag = languageTag,
        )
      }

    when (result) {
      is AutoTranscribeResult.Success -> {
        _state.value =
          AutoTranscribeUiState(
            isRunning = false,
            stageLabel = "Complete",
            progressFraction = 1f,
            waveformBins = result.waveformBins,
            segments = result.segments,
            lastSummary =
              "${result.engineLabel}: ${result.segments.size} segments (${result.wordTimings.size} words) ready.",
          )
      }
      is AutoTranscribeResult.NoSpeechDetected -> {
        _state.value =
          AutoTranscribeUiState(
            isRunning = false,
            waveformBins = result.waveformBins,
            lastSummary = result.message,
          )
      }
      is AutoTranscribeResult.Unavailable -> {
        _state.value =
          AutoTranscribeUiState(
            isRunning = false,
            waveformBins = result.waveformBins,
            lastError = result.reason,
          )
      }
    }
    return result
  }

  private suspend fun transcribeWithGeminiAndAlign(
    extracted: ExtractedAudioTrack,
    apiKey: String,
    modelName: String,
    languageTag: String,
  ): AutoTranscribeResult {
    // Chunk cleanly at natural silence gaps if audio exceeds 45 seconds so inline payload stays light
    val chunks = groupSegmentsIntoApiWindows(extracted.speechSegments, maxWindowMs = 45_000L)
    val allWords = mutableListOf<WordTiming>()
    val transcriptParts = mutableListOf<String>()

    for ((idx, window) in chunks.withIndex()) {
      val progress = 0.38f + ((idx.toFloat() / max(1, chunks.size)) * 0.50f)
      _state.value =
        _state.value.copy(
          stageLabel = "Gemini AI transcribing segment ${idx + 1}/${chunks.size}...",
          progressFraction = progress,
        )
      val windowDur = window.last - window.first
      val use8k = windowDur > 30_000L
      val wavBytes =
        AudioExtractionService.encodeWavBytes(
          pcm16k = extracted.pcmMono16k,
          startMs = window.first,
          endMs = window.last,
          downsampleTo8k = use8k,
        )
      val b64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
      when (
        val apiRes =
          GeminiApiClient.transcribeAudioWithWordTimings(
            apiKey = apiKey,
            modelName = modelName,
            wavBase64 = b64,
            languageHint = languageTag,
            offsetMs = window.first,
          )
      ) {
        is GeminiApiResult.Success -> {
          val data = apiRes.data
          if (data.transcript.isNotBlank()) transcriptParts.add(data.transcript)
          if (data.wordTimings.isNotEmpty()) {
            allWords.addAll(data.wordTimings)
          } else if (data.transcript.isNotBlank()) {
            val tokens = data.transcript.split(Regex("\\s+")).filter { it.isNotBlank() }
            allWords.addAll(
              AudioSpeechAligner.alignWordsToAcousticEnvelope(
                words = tokens,
                envelope10ms = extracted.energyEnvelope10ms,
                windowStartMs = window.first,
                windowEndMs = window.last,
              )
            )
          }
        }
        is GeminiApiResult.Error -> {
          return AutoTranscribeResult.Unavailable(
            waveformBins = extracted.waveformBins,
            reason = apiRes.message,
          )
        }
      }
    }

    if (allWords.isEmpty()) {
      return AutoTranscribeResult.NoSpeechDetected(
        waveformBins = extracted.waveformBins,
        message = "Gemini detected no spoken words in the audio track.",
      )
    }

    _state.value =
      _state.value.copy(
        stageLabel = "Locking word timestamps to 10ms audio onsets...",
        progressFraction = 0.92f,
      )
    val refinedWords = AudioSpeechAligner.refineWordTimings(allWords, extracted.energyEnvelope10ms)
    val segmented = VoskVideoTranscriptionService.segmentRecognizedWords(refinedWords)
    val fullTranscript =
      transcriptParts.joinToString(" ").ifBlank { refinedWords.joinToString(" ") { it.word } }

    return AutoTranscribeResult.Success(
      transcript = fullTranscript,
      wordTimings = refinedWords,
      waveformBins = extracted.waveformBins,
      engineLabel = "Gemini AI + 10ms Acoustic Sync",
      segments = segmented,
    )
  }

  private suspend fun transcribeOfflineWithAcousticAlignment(
    extracted: ExtractedAudioTrack,
    languageTag: String,
  ): AutoTranscribeResult {
    // 1. Primary path on all Android versions (7.0+ / API 24+):
    //    VoskVideoTranscriptionService decodes speech segments with embedded Kaldi/Vosk ASR
    //    and snaps words to the 10ms acoustic envelope into editor-ready VoskTextSegments.
    val voskRes =
      voskService.transcribeExtractedAudioToSegments(
        extracted = extracted,
        languageTag = languageTag,
        autoInstallModelIfMissing = true,
      ) { progress, label ->
        _state.value = _state.value.copy(stageLabel = label, progressFraction = progress)
      }

    when (voskRes) {
      is VoskTranscriptionResult.Success -> {
        return AutoTranscribeResult.Success(
          transcript = voskRes.transcript,
          wordTimings = voskRes.wordTimings,
          waveformBins = voskRes.waveformBins,
          engineLabel = voskRes.engineLabel,
          segments = voskRes.segments,
        )
      }
      is VoskTranscriptionResult.NoSpeechDetected -> {
        return AutoTranscribeResult.NoSpeechDetected(voskRes.waveformBins, voskRes.message)
      }
      is VoskTranscriptionResult.ModelUnavailable -> {
        // On Android < 13 there is no system audio-pipe SpeechRecognizer; report Vosk status cleanly.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
          return AutoTranscribeResult.Unavailable(voskRes.waveformBins, voskRes.reason)
        }
        // On Android 13+, fall through to system SpeechRecognizer audio pipe only if available.
        val supportsOnDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val supportsSystem = SpeechRecognizer.isRecognitionAvailable(context)
        if (!supportsOnDevice && !supportsSystem) {
          return AutoTranscribeResult.Unavailable(voskRes.waveformBins, voskRes.reason)
        }
      }
    }

    // 2. Secondary fallback on Android 13+ when Vosk model isn't cached & offline:
    //    System SpeechRecognizer audio pipe.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return AutoTranscribeResult.Unavailable(
        extracted.waveformBins,
        "Install the offline Vosk voice pack for $languageTag to transcribe audio.",
      )
    }
    val supportsOnDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    val segments = extracted.speechSegments
    val allWords = mutableListOf<WordTiming>()
    val transcriptParts = mutableListOf<String>()
    var lastErrorMsg: String? = null

    for ((idx, seg) in segments.withIndex()) {
      val progress = 0.38f + ((idx.toFloat() / max(1, segments.size)) * 0.56f)
      _state.value =
        _state.value.copy(
          stageLabel = "Offline speech pass ${idx + 1}/${segments.size} (${seg.startMs / 1000.0}s)...",
          progressFraction = progress,
        )

      val segRes =
        recognizePcmSegmentOffline(
          pcm16k = extracted.pcmMono16k,
          segment = seg,
          languageTag = languageTag,
          preferOnDevice = supportsOnDevice,
        )

      when (segRes) {
        is SegmentRecognitionResult.Recognized -> {
          if (segRes.text.isNotBlank()) {
            transcriptParts.add(segRes.text)
            val tokens = segRes.text.split(Regex("\\s+")).filter { it.isNotBlank() }
            val aligned =
              AudioSpeechAligner.alignWordsToAcousticEnvelope(
                words = tokens,
                envelope10ms = extracted.energyEnvelope10ms,
                windowStartMs = seg.startMs,
                windowEndMs = seg.endMs,
                observedAnchorsMs = segRes.observedWordTimesMs,
              )
            allWords.addAll(aligned)
          }
        }
        is SegmentRecognitionResult.FatalUnavailable -> {
          return AutoTranscribeResult.Unavailable(
            waveformBins = extracted.waveformBins,
            reason = segRes.message,
          )
        }
        is SegmentRecognitionResult.NoMatch -> {
          lastErrorMsg = segRes.message
        }
      }
    }

    if (allWords.isEmpty()) {
      return if (lastErrorMsg != null) {
        AutoTranscribeResult.Unavailable(extracted.waveformBins, lastErrorMsg)
      } else {
        AutoTranscribeResult.NoSpeechDetected(
          extracted.waveformBins,
          "Speech energy was found, but no recognizable words matched ($languageTag).",
        )
      }
    }

    val segmented = VoskVideoTranscriptionService.segmentRecognizedWords(allWords)
    return AutoTranscribeResult.Success(
      transcript = transcriptParts.joinToString(" "),
      wordTimings = allWords,
      waveformBins = extracted.waveformBins,
      engineLabel = "Offline Speech + 10ms Acoustic Alignment",
      segments = segmented,
    )
  }

  private sealed interface SegmentRecognitionResult {
    data class Recognized(val text: String, val observedWordTimesMs: Map<Int, Long>) :
      SegmentRecognitionResult

    data class NoMatch(val message: String) : SegmentRecognitionResult

    data class FatalUnavailable(val message: String) : SegmentRecognitionResult
  }

  private suspend fun recognizePcmSegmentOffline(
    pcm16k: ShortArray,
    segment: SpeechSegment,
    languageTag: String,
    preferOnDevice: Boolean,
  ): SegmentRecognitionResult {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return SegmentRecognitionResult.FatalUnavailable("Requires Android 13+")
    }

    val segDurMs = (segment.endMs - segment.startMs).coerceAtLeast(300L)
    val timeoutMs = segDurMs + 10_000L

    return withTimeoutOrNull(timeoutMs) {
      withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
          var pipe: Array<ParcelFileDescriptor>? = null
          var feederJob: Job? = null
          var recognizer: SpeechRecognizer? = null

          fun cleanup() {
            feederJob?.cancel()
            runCatching { pipe?.getOrNull(0)?.close() }
            runCatching { pipe?.getOrNull(1)?.close() }
            runCatching { recognizer?.destroy() }
          }

          try {
            pipe = ParcelFileDescriptor.createPipe()
            val readFd = pipe[0]
            val writeFd = pipe[1]

            recognizer =
              if (preferOnDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
              } else {
                SpeechRecognizer.createSpeechRecognizer(context)
              }

            val observedWords = mutableMapOf<Int, Long>()
            var lastPartialText = ""
            val streamStartRealMs = System.currentTimeMillis()

            recognizer.setRecognitionListener(
              object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onPartialResults(partialResults: Bundle?) {
                  val text =
                    partialResults
                      ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                      ?.firstOrNull()
                      ?.trim()
                      .orEmpty()
                  if (text.isNotEmpty()) {
                    lastPartialText = text
                    val elapsed = (System.currentTimeMillis() - streamStartRealMs).coerceAtLeast(0L)
                    val count = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    for (i in 0 until count) {
                      observedWords.putIfAbsent(i, segment.startMs + elapsed)
                    }
                  }
                }

                override fun onResults(results: Bundle?) {
                  val text =
                    results
                      ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                      ?.firstOrNull()
                      ?.trim()
                      .orEmpty()
                      .ifBlank { lastPartialText }
                  cleanup()
                  if (cont.isActive) {
                    cont.resume(SegmentRecognitionResult.Recognized(text, observedWords))
                  }
                }

                override fun onError(error: Int) {
                  val result =
                    when (error) {
                      SpeechRecognizer.ERROR_NO_MATCH,
                      SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (lastPartialText.isNotBlank()) {
                          SegmentRecognitionResult.Recognized(lastPartialText, observedWords)
                        } else {
                          SegmentRecognitionResult.NoMatch("No speech match in segment.")
                        }
                      SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                      SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        SegmentRecognitionResult.FatalUnavailable(
                          "Offline speech language pack for $languageTag is not downloaded on this device. Download it in Android Speech Settings or enable Gemini AI Mode."
                        )
                      SpeechRecognizer.ERROR_AUDIO,
                      SpeechRecognizer.ERROR_CLIENT ->
                        SegmentRecognitionResult.FatalUnavailable(
                          "This device's offline Speech Service rejected audio-pipe input ($error). Use Gemini AI Mode, Dictate Speech, or SRT/VTT import."
                        )
                      SpeechRecognizer.ERROR_NETWORK,
                      SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        SegmentRecognitionResult.FatalUnavailable(
                          "System SpeechRecognizer requested network despite offline preference ($error). Download the offline language pack in Android settings or use Gemini AI Mode."
                        )
                      else ->
                        SegmentRecognitionResult.NoMatch("Speech recognizer code $error")
                    }
                  cleanup()
                  if (cont.isActive) cont.resume(result)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
              }
            )

            val intent =
              Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                  RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                  RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readFd)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(
                  RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                  AudioFormat.ENCODING_PCM_16BIT,
                )
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
                }
              }

            // Stream decoded 16kHz PCM into writeFd in paced 80ms frames (2560 bytes / 80ms)
            feederJob =
              CoroutineScope(Dispatchers.IO).launch {
                val startSample = ((segment.startMs * 16000L) / 1000L).toInt().coerceIn(0, pcm16k.size)
                val endSample = ((segment.endMs * 16000L) / 1000L).toInt().coerceIn(startSample, pcm16k.size)
                val chunkSamples = 1280 // 80ms @ 16kHz
                val byteBuf = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

                try {
                  ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    var cursor = startSample
                    while (cursor < endSample && isActive) {
                      val count = min(chunkSamples, endSample - cursor)
                      byteBuf.clear()
                      for (i in 0 until count) {
                        byteBuf.putShort(pcm16k[cursor + i])
                      }
                      out.write(byteBuf.array(), 0, count * 2)
                      out.flush()
                      cursor += count
                      delay(72L) // slightly faster than 1x while staying safely inside ASR ring buffer
                    }
                    // Write 400ms trailing silence so recognizer endpointing flushes final tokens
                    val silence = ByteArray(1280 * 2)
                    repeat(5) {
                      if (!isActive) return@repeat
                      out.write(silence)
                      out.flush()
                      delay(75L)
                    }
                  }
                } catch (_: IOException) {
                  // Pipe closed by recognizer once utterance finished
                }
              }

            recognizer.startListening(intent)
            cont.invokeOnCancellation { cleanup() }
          } catch (e: Exception) {
            if (e is CancellationException) throw e
            cleanup()
            if (cont.isActive) {
              cont.resume(
                SegmentRecognitionResult.FatalUnavailable(
                  "Offline audio pipe setup failed: ${e.localizedMessage}"
                )
              )
            }
          }
        }
      }
    } ?: SegmentRecognitionResult.NoMatch("Speech segment timed out.")
  }

  private fun groupSegmentsIntoApiWindows(
    segments: List<SpeechSegment>,
    maxWindowMs: Long,
  ): List<LongRange> {
    if (segments.isEmpty()) return emptyList()
    val windows = mutableListOf<LongRange>()
    var curStart = segments.first().startMs
    var curEnd = segments.first().endMs
    for (i in 1 until segments.size) {
      val s = segments[i]
      if (s.endMs - curStart <= maxWindowMs) {
        curEnd = s.endMs
      } else {
        windows.add(curStart..curEnd)
        curStart = s.startMs
        curEnd = s.endMs
      }
    }
    windows.add(curStart..curEnd)
    return windows
  }
}

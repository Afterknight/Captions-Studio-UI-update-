package com.example.captionstudio.media

import android.content.Context
import android.net.Uri
import android.os.Build
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.engine.AudioSpeechAligner
import com.example.captionstudio.engine.LocalCompositionEngine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Timed phrase-level text segment emitted by [VoskVideoTranscriptionService] for the editor timeline.
 */
data class VoskTextSegment(
  val id: String = UUID.randomUUID().toString(),
  val index: Int,
  val text: String,
  val startMs: Long,
  val endMs: Long,
  val words: List<WordTiming>,
  val averageConfidence: Float = 1f,
)

data class VoskTranscriptionState(
  val isTranscribing: Boolean = false,
  val stageLabel: String = "",
  val progressFraction: Float = 0f,
  val activePackName: String = "",
  val segments: List<VoskTextSegment> = emptyList(),
  val waveformBins: List<Float> = emptyList(),
  val lastError: String? = null,
)

sealed interface VoskTranscriptionResult {
  data class Success(
    val segments: List<VoskTextSegment>,
    val transcript: String,
    val wordTimings: List<WordTiming>,
    val waveformBins: List<Float>,
    val engineLabel: String,
  ) : VoskTranscriptionResult

  data class NoSpeechDetected(
    val waveformBins: List<Float>,
    val message: String,
  ) : VoskTranscriptionResult

  data class ModelUnavailable(
    val waveformBins: List<Float>,
    val reason: String,
  ) : VoskTranscriptionResult
}

internal data class ParsedVoskWord(
  val timing: WordTiming,
  val confidence: Float,
)

/**
 * Dedicated offline service utilizing the embedded Kaldi/Vosk library (`org.vosk.Model` &
 * `org.vosk.Recognizer`) to decode audio from a selected video file, run 16kHz mono speech
 * recognition with word-level timestamps & confidence, refine onsets on a 10ms acoustic envelope,
 * and emit pause-segmented [VoskTextSegment] items for the kinetic caption editor.
 */
class VoskVideoTranscriptionService(private val context: Context) {

  private val _state = MutableStateFlow(VoskTranscriptionState())
  val state: StateFlow<VoskTranscriptionState> = _state.asStateFlow()

  @Volatile private var activeJob: Job? = null

  fun cancel() {
    activeJob?.cancel()
    activeJob = null
    _state.value = _state.value.copy(isTranscribing = false, stageLabel = "Cancelled")
  }

  fun clearError() {
    _state.value = _state.value.copy(lastError = null)
  }

  /**
   * Decodes the audio track of [videoUri] and transcribes spoken dialogue into editor-ready
   * [VoskTextSegment] items using the included Vosk offline ASR engine.
   */
  suspend fun transcribeVideoToSegments(
    videoUri: Uri,
    languageTag: String = "en-US",
    autoInstallModelIfMissing: Boolean = true,
    maxWordsPerSegment: Int = 6,
    pauseSplitMs: Long = 380L,
  ): VoskTranscriptionResult {
    activeJob = coroutineContext[Job]
    val pack = OfflineVoskModelManager.packForLanguage(languageTag)

    _state.value =
      VoskTranscriptionState(
        isTranscribing = true,
        stageLabel = "Extracting audio from selected video...",
        progressFraction = 0.05f,
        activePackName = pack.displayName,
      )

    val extracted =
      try {
        AudioExtractionService.extractAudioTrack(context, videoUri) { p ->
          _state.value =
            _state.value.copy(
              stageLabel = "Decoding 16kHz speech waveform...",
              progressFraction = 0.05f + p * 0.25f,
            )
        }
      } catch (ce: CancellationException) {
        _state.value = _state.value.copy(isTranscribing = false, stageLabel = "Cancelled")
        throw ce
      }

    if (extracted == null) {
      val msg = "No audio track found in the selected video file."
      _state.value = VoskTranscriptionState(isTranscribing = false, lastError = msg)
      return VoskTranscriptionResult.NoSpeechDetected(emptyList(), msg)
    }

    if (!extracted.hasSpokenAudio) {
      val msg = "No detectable speech energy found in video audio."
      _state.value =
        VoskTranscriptionState(
          isTranscribing = false,
          waveformBins = extracted.waveformBins,
          stageLabel = msg,
        )
      return VoskTranscriptionResult.NoSpeechDetected(extracted.waveformBins, msg)
    }

    val result =
      transcribeExtractedAudioToSegments(
        extracted = extracted,
        languageTag = languageTag,
        autoInstallModelIfMissing = autoInstallModelIfMissing,
        maxWordsPerSegment = maxWordsPerSegment,
        pauseSplitMs = pauseSplitMs,
      )

    when (result) {
      is VoskTranscriptionResult.Success -> {
        _state.value =
          VoskTranscriptionState(
            isTranscribing = false,
            stageLabel = "Ready (${result.segments.size} segments)",
            progressFraction = 1f,
            activePackName = pack.displayName,
            segments = result.segments,
            waveformBins = result.waveformBins,
          )
      }
      is VoskTranscriptionResult.NoSpeechDetected -> {
        _state.value =
          VoskTranscriptionState(
            isTranscribing = false,
            activePackName = pack.displayName,
            waveformBins = result.waveformBins,
            stageLabel = result.message,
          )
      }
      is VoskTranscriptionResult.ModelUnavailable -> {
        _state.value =
          VoskTranscriptionState(
            isTranscribing = false,
            activePackName = pack.displayName,
            waveformBins = result.waveformBins,
            lastError = result.reason,
          )
      }
    }

    return result
  }

  /**
   * Transcribes an already-decoded [ExtractedAudioTrack] via Vosk into structured editor text
   * segments, avoiding duplicate video decoding when paired with waveform/VAD inspection.
   */
  suspend fun transcribeExtractedAudioToSegments(
    extracted: ExtractedAudioTrack,
    languageTag: String = "en-US",
    autoInstallModelIfMissing: Boolean = true,
    maxWordsPerSegment: Int = 6,
    pauseSplitMs: Long = 380L,
    onProgress: (Float, String) -> Unit = { _, _ -> },
  ): VoskTranscriptionResult {
    val pack = OfflineVoskModelManager.packForLanguage(languageTag)
    var modelDir: File? = OfflineVoskModelManager.getInstalledModelDir(context, languageTag)

    if (modelDir == null && autoInstallModelIfMissing) {
      val installMsg = "Downloading ${pack.displayName} Vosk model (~${pack.approxSizeMb} MB, one-time)..."
      _state.value = _state.value.copy(stageLabel = installMsg, progressFraction = 0.32f)
      onProgress(0.32f, installMsg)

      val dlResult =
        OfflineVoskModelManager.ensureOrDownloadPack(context, pack) { dlProgress ->
          val combined = 0.32f + dlProgress * 0.28f
          val label = "Downloading ${pack.displayName} Vosk model (${(dlProgress * 100).toInt()}%)..."
          _state.value = _state.value.copy(stageLabel = label, progressFraction = combined)
          onProgress(combined, label)
        }
      modelDir = dlResult.getOrNull()
      if (modelDir == null) {
        val err = dlResult.exceptionOrNull()?.localizedMessage ?: "Network unavailable"
        val hint =
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            "Vosk offline model (${pack.displayName}, ~${pack.approxSizeMb} MB) failed to install ($err). Connect briefly once to cache it for permanent offline transcription on Android < 13."
          } else {
            "Vosk offline model (${pack.displayName}, ~${pack.approxSizeMb} MB) is not cached yet ($err)."
          }
        return VoskTranscriptionResult.ModelUnavailable(extracted.waveformBins, hint)
      }
    }

    if (modelDir == null) {
      return VoskTranscriptionResult.ModelUnavailable(
        waveformBins = extracted.waveformBins,
        reason = "Vosk offline model for ${pack.displayName} is not installed yet.",
      )
    }

    return runVoskRecognitionOverSegments(
      modelDir = modelDir,
      pack = pack,
      extracted = extracted,
      maxWordsPerSegment = maxWordsPerSegment,
      pauseSplitMs = pauseSplitMs,
      onProgress = onProgress,
    )
  }

  private suspend fun runVoskRecognitionOverSegments(
    modelDir: File,
    pack: OfflineVoicePackInfo,
    extracted: ExtractedAudioTrack,
    maxWordsPerSegment: Int,
    pauseSplitMs: Long,
    onProgress: (Float, String) -> Unit,
  ): VoskTranscriptionResult =
    withContext(Dispatchers.Default) {
      try {
        val model = OfflineVoskModelManager.getOrLoadCachedModel(modelDir)
        val segments = extracted.speechSegments
        val parsedWords = mutableListOf<ParsedVoskWord>()
        val utteranceBoundariesMs = mutableSetOf<Long>()

        for ((idx, vadSeg) in segments.withIndex()) {
          coroutineContext.ensureActive()
          val progress = 0.60f + (idx.toFloat() / max(1, segments.size)) * 0.32f
          val msg = "Vosk transcribing segment ${idx + 1}/${segments.size}..."
          _state.value = _state.value.copy(stageLabel = msg, progressFraction = progress)
          onProgress(progress, msg)

          val startSample =
            ((vadSeg.startMs * 16000L) / 1000L).toInt().coerceIn(0, extracted.pcmMono16k.size)
          val endSample =
            ((vadSeg.endMs * 16000L) / 1000L)
              .toInt()
              .coerceIn(startSample, extracted.pcmMono16k.size)
          if (endSample - startSample < 320) continue

          val normalizedSlice =
            normalizeQuietPcmIfNeeded(extracted.pcmMono16k, startSample, endSample)

          val recognizer = Recognizer(model, 16000f)
          try {
            recognizer.setWords(true)
            val chunkSamples = 4096
            val byteBuf = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            var cursor = 0
            while (cursor < normalizedSlice.size) {
              coroutineContext.ensureActive()
              val count = min(chunkSamples, normalizedSlice.size - cursor)
              byteBuf.clear()
              for (i in 0 until count) {
                byteBuf.putShort(normalizedSlice[cursor + i])
              }
              if (recognizer.acceptWaveForm(byteBuf.array(), count * 2)) {
                val parsed = parseVoskJsonUtterance(recognizer.result, vadSeg.startMs)
                if (parsed.isNotEmpty()) {
                  parsedWords.addAll(parsed)
                  utteranceBoundariesMs.add(parsed.last().timing.endMs)
                }
              }
              cursor += count
            }

            // Flush final recognizer lattice with 320ms silence tail
            val silence = ByteArray(5120 * 2)
            recognizer.acceptWaveForm(silence, silence.size)
            val finalParsed = parseVoskJsonUtterance(recognizer.finalResult, vadSeg.startMs)
            if (finalParsed.isNotEmpty()) {
              parsedWords.addAll(finalParsed)
              utteranceBoundariesMs.add(finalParsed.last().timing.endMs)
            }
          } finally {
            runCatching { recognizer.close() }
          }
        }

        if (parsedWords.isEmpty()) {
          return@withContext VoskTranscriptionResult.NoSpeechDetected(
            waveformBins = extracted.waveformBins,
            message = "Speech energy was found, but Vosk matched no recognizable words (${pack.displayName}).",
          )
        }

        val syncMsg = "Snapping word timings to 10ms acoustic onsets..."
        _state.value = _state.value.copy(stageLabel = syncMsg, progressFraction = 0.95f)
        onProgress(0.95f, syncMsg)

        val rawTimings = parsedWords.map { it.timing }
        val refinedTimings =
          AudioSpeechAligner.refineWordTimings(rawTimings, extracted.energyEnvelope10ms)
        val confidences = parsedWords.map { it.confidence }

        val editorSegments =
          segmentRecognizedWords(
            words = refinedTimings,
            confidences = confidences,
            utteranceEndBoundariesMs = utteranceBoundariesMs,
            maxWordsPerSegment = maxWordsPerSegment,
            pauseSplitMs = pauseSplitMs,
          )

        val fullTranscript = editorSegments.joinToString(" ") { it.text }.trim()

        VoskTranscriptionResult.Success(
          segments = editorSegments,
          transcript = fullTranscript,
          wordTimings = refinedTimings,
          waveformBins = extracted.waveformBins,
          engineLabel = "Vosk Offline ASR (${pack.displayName}) + 10ms Sync",
        )
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Throwable) {
        VoskTranscriptionResult.ModelUnavailable(
          waveformBins = extracted.waveformBins,
          reason = "Vosk transcription failed: ${t.localizedMessage ?: "unknown native error"}",
        )
      }
    }

  companion object {

    /**
     * Parses a single JSON response from Vosk (`recognizer.result` or `recognizer.finalResult`)
     * into timed, confidence-scored words offset by [segmentOffsetMs].
     */
    internal fun parseVoskJsonUtterance(
      jsonStr: String?,
      segmentOffsetMs: Long,
    ): List<ParsedVoskWord> {
      if (jsonStr.isNullOrBlank()) return emptyList()
      val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
      val resultArr = obj.optJSONArray("result") ?: JSONArray()
      val out = mutableListOf<ParsedVoskWord>()

      if (resultArr.length() > 0) {
        val total = resultArr.length()
        for (i in 0 until total) {
          val wObj = resultArr.optJSONObject(i) ?: continue
          val w = wObj.optString("word", "").trim()
          if (w.isEmpty()) continue
          val sMs = segmentOffsetMs + (wObj.optDouble("start", 0.0) * 1000.0).toLong()
          val eMs = segmentOffsetMs + (wObj.optDouble("end", 0.16) * 1000.0).toLong()
          val conf = wObj.optDouble("conf", 1.0).toFloat().coerceIn(0f, 1f)
          out.add(
            ParsedVoskWord(
              timing =
                WordTiming(
                  word = w,
                  startMs = sMs.coerceAtLeast(segmentOffsetMs),
                  endMs = max(sMs + 75L, eMs),
                  isEstimated = false,
                  emphasisScore = LocalCompositionEngine.scoreWordSalience(w, i, max(1, total)),
                ),
              confidence = conf,
            )
          )
        }
        return out
      }

      // Defensive fallback if a Vosk graph omits word-level array despite setWords(true)
      val text = obj.optString("text", "").trim()
      if (text.isNotEmpty()) {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        var cursor = segmentOffsetMs
        tokens.forEachIndexed { idx, token ->
          val wEnd = cursor + 240L
          out.add(
            ParsedVoskWord(
              timing =
                WordTiming(
                  word = token,
                  startMs = cursor,
                  endMs = wEnd,
                  isEstimated = true,
                  emphasisScore =
                    LocalCompositionEngine.scoreWordSalience(token, idx, tokens.size),
                ),
              confidence = 0.85f,
            )
          )
          cursor = wEnd
        }
      }
      return out
    }

    /**
     * Groups flat word timings into clean, editor-ready [VoskTextSegment] chunks split at natural
     * speech pauses, Vosk utterance endpoints, punctuation, and safe kinetic word-count bounds.
     */
    fun segmentRecognizedWords(
      words: List<WordTiming>,
      confidences: List<Float> = emptyList(),
      utteranceEndBoundariesMs: Set<Long> = emptySet(),
      maxWordsPerSegment: Int = 6,
      maxSegmentDurationMs: Long = 3_400L,
      pauseSplitMs: Long = 380L,
    ): List<VoskTextSegment> {
      if (words.isEmpty()) return emptyList()
      val segments = mutableListOf<VoskTextSegment>()
      val currentWords = mutableListOf<WordTiming>()
      val currentConfs = mutableListOf<Float>()

      fun flushSegment() {
        if (currentWords.isEmpty()) return
        val avgConf =
          if (currentConfs.isNotEmpty()) currentConfs.average().toFloat().coerceIn(0f, 1f) else 1f
        val startMs = currentWords.first().startMs
        val endMs = max(startMs + 180L, currentWords.last().endMs)
        segments.add(
          VoskTextSegment(
            index = segments.size,
            text = currentWords.joinToString(" ") { it.word },
            startMs = startMs,
            endMs = endMs,
            words = currentWords.toList(),
            averageConfidence = avgConf,
          )
        )
        currentWords.clear()
        currentConfs.clear()
      }

      for (i in words.indices) {
        val w = words[i]
        val conf = confidences.getOrElse(i) { 1f }
        currentWords.add(w)
        currentConfs.add(conf)

        val next = words.getOrNull(i + 1)
        val hasPauseAfter = next != null && (next.startMs - w.endMs) >= pauseSplitMs
        val hitUtteranceBoundary = w.endMs in utteranceEndBoundariesMs && currentWords.size >= 3
        val hitPunctuation =
          w.word.endsWith(".") || w.word.endsWith("!") || w.word.endsWith("?")
        val elapsedInSeg = w.endMs - currentWords.first().startMs
        val hitLengthCap =
          currentWords.size >= max(2, maxWordsPerSegment) ||
            (currentWords.size >= 2 && elapsedInSeg >= maxSegmentDurationMs)

        if (next == null || hasPauseAfter || hitUtteranceBoundary || hitPunctuation || hitLengthCap) {
          flushSegment()
        }
      }
      flushSegment()
      return segments
    }

    /**
     * Softly boosts quiet 16kHz PCM audio slices without clipping so low-level mic recordings
     * transcribe accurately in Kaldi/Vosk.
     */
    internal fun normalizeQuietPcmIfNeeded(
      pcm16k: ShortArray,
      startIdx: Int,
      endIdx: Int,
    ): ShortArray {
      val safeStart = startIdx.coerceIn(0, pcm16k.size)
      val safeEnd = endIdx.coerceIn(safeStart, pcm16k.size)
      val len = safeEnd - safeStart
      if (len <= 0) return ShortArray(0)

      var peak = 0
      for (i in safeStart until safeEnd) {
        val a = abs(pcm16k[i].toInt())
        if (a > peak) peak = a
      }

      val slice = pcm16k.copyOfRange(safeStart, safeEnd)
      // If signal already exceeds ~22% peak full-scale or is pure noise floor (< 180), keep raw levels
      if (peak >= 7200 || peak < 180) return slice

      val targetPeak = 21_000f
      val gain = min(6.0f, targetPeak / peak.toFloat())
      for (i in slice.indices) {
        val scaled = (slice[i] * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        slice[i] = scaled.toShort()
      }
      return slice
    }
  }
}

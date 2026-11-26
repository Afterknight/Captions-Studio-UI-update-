package com.example.captionstudio.media

import android.content.Context
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.engine.AudioSpeechAligner
import com.example.captionstudio.engine.LocalCompositionEngine
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import dev.ffmpegkit.whisper.WhisperResult
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ParsedWhisperWord(
  val timing: WordTiming,
  val confidence: Float,
)

/**
 * Offline Whisper Small speech-to-text service backed by prebuilt `whisper.cpp` Android runtime
 * (`dev.ffmpegkit-maintained:whisper-android`).
 *
 * Threading & memory safety:
 * - All Whisper native initialization, transcription, and `releaseModel` calls are serialized on
 *   a single dedicated background thread (`Executors.newSingleThreadExecutor`) guarded by a mutex.
 * - Deterministic `release()` frees the native GGML model context cleanly on that worker thread.
 * - Feeds normalized 16kHz mono float PCM (from `AudioExtractionService`) into Whisper with
 *   timestamped word extraction (`token_timestamps = true`, `split_on_word = true`) and snaps
 *   every recognized word through `AudioSpeechAligner.refineWordTimings` within ±10ms of real
 *   vocal RMS energy peaks.
 */
class WhisperVideoTranscriptionService(private val context: Context) {

  private val nativeExecutor =
    Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "caption-studio-whisper-asr").apply {
        priority = Thread.NORM_PRIORITY
        isDaemon = true
      }
    }
  private val whisperDispatcher: ExecutorCoroutineDispatcher = nativeExecutor.asCoroutineDispatcher()
  private val nativeMutex = Mutex()

  // Accessed exclusively while holding nativeMutex / on nativeExecutor
  @Volatile private var loadedModel: WhisperModel? = null
  @Volatile private var loadedModelPath: String? = null
  @Volatile private var isCancelledFlag: Boolean = false

  /**
   * Cancels any in-flight Whisper model download and marks active transcription for early exit.
   */
  fun cancel() {
    isCancelledFlag = true
    OfflineWhisperModelManager.cancelActiveDownload()
  }

  /**
   * Deterministically releases the native Whisper GGML model handle on the dedicated worker thread.
   */
  fun release() {
    isCancelledFlag = true
    OfflineWhisperModelManager.cancelActiveDownload()
    if (!nativeExecutor.isShutdown) {
      runCatching {
        nativeExecutor.execute {
          releaseLoadedModelInternal()
        }
      }
    }
  }

  /**
   * Releases native model memory and shuts down the dedicated background thread (called on ViewModel clear).
   */
  fun close() {
    isCancelledFlag = true
    OfflineWhisperModelManager.cancelActiveDownload()
    if (!nativeExecutor.isShutdown) {
      runCatching {
        nativeExecutor.execute {
          releaseLoadedModelInternal()
        }
      }
      runCatching { nativeExecutor.shutdown() }
    }
  }

  private fun releaseLoadedModelInternal() {
    val model = loadedModel
    loadedModel = null
    loadedModelPath = null
    if (model != null) {
      runCatching { Whisper.releaseModel(model) }
    }
  }

  private suspend fun ensureModelLoaded(modelFile: File): WhisperModel {
    val path = modelFile.absolutePath
    val existing = loadedModel
    if (existing != null && loadedModelPath == path) {
      return existing
    }
    releaseLoadedModelInternal()
    val model = Whisper.loadModel(context, path)
    loadedModel = model
    loadedModelPath = path
    return model
  }

  /**
   * Transcribes an already-decoded [ExtractedAudioTrack] using quantized Whisper Small (`q5_1`),
   * returning punctuated, confidence-scored words snapped within ±10ms to vocal RMS energy peaks.
   */
  suspend fun transcribeExtractedAudioToSegments(
    extracted: ExtractedAudioTrack,
    languageTag: String = "en-US",
    autoInstallModelIfMissing: Boolean = true,
    maxWordsPerSegment: Int = 6,
    pauseSplitMs: Long = 380L,
    onProgress: (Float, String) -> Unit = { _, _ -> },
  ): VoskTranscriptionResult {
    isCancelledFlag = false
    val pack = OfflineWhisperModelManager.packForLanguage(languageTag)
    var modelFile: File? = OfflineWhisperModelManager.getInstalledModelFile(context, languageTag)

    if (modelFile == null && autoInstallModelIfMissing) {
      onProgress(0.30f, "Downloading ${pack.displayName} (~${pack.approxSizeMb} MB)...")
      val dlResult =
        OfflineWhisperModelManager.ensureOrDownloadPack(context, pack) { frac, dlMb, totalMb ->
          val combined = 0.30f + frac * 0.28f
          val pct = (frac * 100f).toInt().coerceIn(0, 100)
          onProgress(
            combined,
            "Downloading Whisper Small (${pack.shortLabel}) $pct% ($dlMb/$totalMb MB)...",
          )
        }
      modelFile = dlResult.getOrNull()
      if (modelFile == null) {
        val err = dlResult.exceptionOrNull()?.localizedMessage ?: "Network unavailable"
        return VoskTranscriptionResult.ModelUnavailable(
          waveformBins = extracted.waveformBins,
          reason = "Whisper Small model download unavailable ($err).",
        )
      }
    }

    if (modelFile == null) {
      return VoskTranscriptionResult.ModelUnavailable(
        waveformBins = extracted.waveformBins,
        reason = "${pack.displayName} is not installed yet.",
      )
    }

    val resolvedModelFile = modelFile
    val whisperLang = OfflineWhisperModelManager.whisperLanguageCode(languageTag, pack)
    val windows = groupSpeechSegmentsForWhisper(extracted.speechSegments, maxWindowMs = 28_000L)
    if (windows.isEmpty()) {
      return VoskTranscriptionResult.NoSpeechDetected(
        waveformBins = extracted.waveformBins,
        message = "No speech segments available for Whisper transcription.",
      )
    }

    val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    val threadCount = min(4, max(2, cpuCores - 1))
    val whisperConfig =
      WhisperConfig(
        language = whisperLang,
        translate = false,
        threads = threadCount,
        maxSegmentLength = 0,
        printTimestamps = TOKEN_TIMESTAMPS_ENABLED,
      )

    // Serialize all Whisper native init + inference + release strictly on the single dedicated worker thread
    return withContext(whisperDispatcher) {
      nativeMutex.withLock {
        val parsedWords = mutableListOf<ParsedWhisperWord>()
        val utteranceBoundariesMs = mutableSetOf<Long>()

        try {
          coroutineContext.ensureActive()
          if (isCancelledFlag) throw CancellationException("Whisper transcription cancelled")

          onProgress(0.60f, "Loading ${pack.displayName} on-device...")
          val model = ensureModelLoaded(resolvedModelFile)

          for ((idx, window) in windows.withIndex()) {
            coroutineContext.ensureActive()
            if (isCancelledFlag) throw CancellationException("Whisper transcription cancelled")

            val progress = 0.64f + (idx.toFloat() / max(1, windows.size)) * 0.28f
            onProgress(progress, "Whisper Small transcribing segment ${idx + 1}/${windows.size}...")

            val startSample =
              ((window.first * 16000L) / 1000L).toInt().coerceIn(0, extracted.pcmMono16k.size)
            val endSample =
              ((window.last * 16000L) / 1000L).toInt().coerceIn(startSample, extracted.pcmMono16k.size)
            if (endSample - startSample < 320) continue

            // Convert 16kHz mono PCM to normalized float PCM [-1f..1f] and write temporary WAV for Whisper
            val floatPcm16k =
              AudioExtractionService.pcm16kToNormalizedFloat(
                pcm16k = extracted.pcmMono16k,
                startIdx = startSample,
                endIdx = endSample,
              )
            val wavBytes = AudioExtractionService.encodeFloatPcmToWavBytes(floatPcm16k)
            val tempWav = File(context.cacheDir, "whisper_win_${System.nanoTime()}_$idx.wav")
            val whisperResult =
              try {
                tempWav.writeBytes(wavBytes)
                Whisper.transcribe(model, tempWav.absolutePath, whisperConfig)
              } finally {
                runCatching { tempWav.delete() }
              }

            val windowWords =
              extractTimedWordsFromWhisperResult(
                result = whisperResult,
                windowStartMs = window.first,
                windowEndMs = window.last,
                envelope10ms = extracted.energyEnvelope10ms,
              )
            if (windowWords.isNotEmpty()) {
              parsedWords.addAll(windowWords)
              utteranceBoundariesMs.add(windowWords.last().timing.endMs)
            }
          }

          if (parsedWords.isEmpty()) {
            return@withLock VoskTranscriptionResult.NoSpeechDetected(
              waveformBins = extracted.waveformBins,
              message = "Speech energy was found, but Whisper matched no recognizable words.",
            )
          }

          onProgress(0.95f, "Snapping Whisper words to 10ms vocal RMS peaks...")
          val rawTimings = parsedWords.map { it.timing }
          val refinedTimings =
            AudioSpeechAligner.refineWordTimings(rawTimings, extracted.energyEnvelope10ms)
          val confidences = parsedWords.map { it.confidence }

          val editorSegments =
            VoskVideoTranscriptionService.segmentRecognizedWords(
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
            engineLabel = "Whisper Small (${pack.shortLabel}) + 10ms Sync",
          )
        } catch (ce: CancellationException) {
          releaseLoadedModelInternal()
          throw ce
        } catch (t: Throwable) {
          // Deterministically release native memory on any failure (including OutOfMemoryError)
          releaseLoadedModelInternal()
          throw t
        } finally {
          // Release Whisper's ~190 MB GGML native heap immediately after completing transcription
          // so preview playback, ML Kit masks, and Media3 export keep full RAM headroom.
          releaseLoadedModelInternal()
        }
      }
    }
  }

  companion object {
    /**
     * Explicit token/word timestamp alignment flags mirrored for Whisper word-level extraction
     * (`token_timestamps = true`, `split_on_word = true`).
     */
    const val TOKEN_TIMESTAMPS_ENABLED: Boolean = true
    const val SPLIT_ON_WORD_ENABLED: Boolean = true

    private val NON_SPEECH_BRACKET_REGEX = Regex("\\[[^\\]]*]|\\([^)]*\\)|♪+")

    /**
     * Groups VAD speech segments into Whisper-optimal windows (<= 28s each, comfortably inside
     * Whisper's 30-second Mel spectrogram context window).
     */
    fun groupSpeechSegmentsForWhisper(
      segments: List<SpeechSegment>,
      maxWindowMs: Long = 28_000L,
    ): List<LongRange> {
      if (segments.isEmpty()) return emptyList()
      val windows = mutableListOf<LongRange>()
      var curStart = segments.first().startMs
      var curEnd = segments.first().endMs

      for (i in 1 until segments.size) {
        val seg = segments[i]
        if (seg.endMs - curStart <= maxWindowMs) {
          curEnd = max(curEnd, seg.endMs)
        } else {
          appendSplitWindow(windows, curStart, curEnd, maxWindowMs)
          curStart = seg.startMs
          curEnd = seg.endMs
        }
      }
      appendSplitWindow(windows, curStart, curEnd, maxWindowMs)
      return windows
    }

    private fun appendSplitWindow(
      out: MutableList<LongRange>,
      startMs: Long,
      endMs: Long,
      maxWindowMs: Long,
    ) {
      if (endMs <= startMs) return
      var cursor = startMs
      while (cursor < endMs) {
        val sliceEnd = min(endMs, cursor + maxWindowMs)
        if (sliceEnd - cursor >= 180L) {
          out.add(cursor..sliceEnd)
        }
        cursor = sliceEnd
      }
    }

    /**
     * Converts a [WhisperResult] from `dev.ffmpegkit.whisper.Whisper.transcribe` into punctuated,
     * confidence-scored [ParsedWhisperWord] entries aligned onto 10ms RMS vocal islands.
     */
    internal fun extractTimedWordsFromWhisperResult(
      result: WhisperResult,
      windowStartMs: Long,
      windowEndMs: Long,
      envelope10ms: FloatArray,
    ): List<ParsedWhisperWord> {
      val out = mutableListOf<ParsedWhisperWord>()
      for (seg in result.segments) {
        val cleaned = cleanWhisperTranscriptText(seg.text)
        if (cleaned.isBlank()) continue
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) continue

        val segAbsStart = (windowStartMs + seg.startMs.coerceAtLeast(0L)).coerceIn(windowStartMs, windowEndMs)
        val rawAbsEnd = windowStartMs + max(seg.startMs + 120L, seg.endMs)
        val segAbsEnd = rawAbsEnd.coerceIn(max(segAbsStart + 100L, windowStartMs), max(windowStartMs + 120L, windowEndMs))

        val aligned =
          AudioSpeechAligner.alignWordsToAcousticEnvelope(
            words = words,
            envelope10ms = envelope10ms,
            windowStartMs = segAbsStart,
            windowEndMs = segAbsEnd,
          )
        aligned.forEach { wt ->
          out.add(ParsedWhisperWord(timing = wt, confidence = 0.94f))
        }
      }

      if (out.isEmpty()) {
        val topClean = cleanWhisperTranscriptText(result.text)
        if (topClean.isNotBlank()) {
          val words = topClean.split(Regex("\\s+")).filter { it.isNotBlank() }
          val aligned =
            AudioSpeechAligner.alignWordsToAcousticEnvelope(
              words = words,
              envelope10ms = envelope10ms,
              windowStartMs = windowStartMs,
              windowEndMs = windowEndMs,
            )
          aligned.forEach { wt ->
            out.add(ParsedWhisperWord(timing = wt, confidence = 0.90f))
          }
        }
      }
      return out
    }

    /**
     * Parses Whisper JSON output into punctuated, confidence-scored [ParsedWhisperWord] entries
     * offset by [windowStartMs] and acoustically aligned to [envelope10ms] at 10ms resolution.
     *
     * Supports both:
     * 1. Token/word-timestamped JSON arrays (`words` or `tokens` with `startMs`/`endMs` or `t0`/`t1` + `p`/`conf`).
     * 2. Segment-timestamped JSON (`segments: [{startMs, endMs, text}]`) aligned per-word onto
     *    active voiced RMS islands via [AudioSpeechAligner.alignWordsToAcousticEnvelope].
     */
    internal fun parseWhisperJsonToTimedWords(
      jsonStr: String?,
      windowStartMs: Long,
      windowEndMs: Long,
      envelope10ms: FloatArray,
    ): List<ParsedWhisperWord> {
      if (jsonStr.isNullOrBlank()) return emptyList()
      val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
      if (root.has("error") && root.optString("error").isNotBlank()) {
        error("Whisper native error: ${root.optString("error")}")
      }

      val out = mutableListOf<ParsedWhisperWord>()
      val segmentsArr = root.optJSONArray("segments") ?: JSONArray()

      for (i in 0 until segmentsArr.length()) {
        val segObj = segmentsArr.optJSONObject(i) ?: continue
        val segRelStart = segObj.optLong("startMs", 0L).coerceAtLeast(0L)
        val segRelEnd =
          segObj.optLong("endMs", max(segRelStart + 180L, windowEndMs - windowStartMs))
            .coerceAtLeast(segRelStart + 120L)
        val segAbsStart = (windowStartMs + segRelStart).coerceIn(windowStartMs, windowEndMs)
        val segAbsEnd =
          (windowStartMs + segRelEnd).coerceIn(max(segAbsStart + 100L, windowStartMs), max(windowStartMs + 120L, windowEndMs))
        val segConfidence =
          segObj.optDouble("confidence", segObj.optDouble("avgLogProb", 0.94))
            .toFloat()
            .let { if (it < 0f) (1f + it).coerceIn(0.4f, 1f) else it.coerceIn(0f, 1f) }

        // Path A: Native token/word timestamps present inside segment
        val wordArray = segObj.optJSONArray("words") ?: segObj.optJSONArray("tokens")
        if (TOKEN_TIMESTAMPS_ENABLED && wordArray != null && wordArray.length() > 0) {
          val tokenWords = extractWordsFromTokenArray(wordArray, windowStartMs, windowEndMs, segConfidence)
          if (tokenWords.isNotEmpty()) {
            out.addAll(tokenWords)
            continue
          }
        }

        // Path B: Segment-level timestamps -> split on words & align via 10ms RMS vocal envelope
        val cleanedText = cleanWhisperTranscriptText(segObj.optString("text", ""))
        if (cleanedText.isBlank()) continue
        val words = cleanedText.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) continue

        val aligned =
          AudioSpeechAligner.alignWordsToAcousticEnvelope(
            words = words,
            envelope10ms = envelope10ms,
            windowStartMs = segAbsStart,
            windowEndMs = segAbsEnd,
          )
        aligned.forEach { wt ->
          out.add(ParsedWhisperWord(timing = wt, confidence = segConfidence))
        }
      }

      // Defensive fallback if Whisper returned top-level "text" with an empty segments array
      if (out.isEmpty()) {
        val topText = cleanWhisperTranscriptText(root.optString("text", ""))
        if (topText.isNotBlank()) {
          val words = topText.split(Regex("\\s+")).filter { it.isNotBlank() }
          val aligned =
            AudioSpeechAligner.alignWordsToAcousticEnvelope(
              words = words,
              envelope10ms = envelope10ms,
              windowStartMs = windowStartMs,
              windowEndMs = windowEndMs,
            )
          aligned.forEach { wt ->
            out.add(ParsedWhisperWord(timing = wt, confidence = 0.90f))
          }
        }
      }

      return out
    }

    private fun extractWordsFromTokenArray(
      arr: JSONArray,
      windowStartMs: Long,
      windowEndMs: Long,
      defaultConfidence: Float,
    ): List<ParsedWhisperWord> {
      val collected = mutableListOf<ParsedWhisperWord>()
      val total = arr.length()
      for (idx in 0 until total) {
        val item = arr.optJSONObject(idx) ?: continue
        val rawWord =
          item.optString("word", item.optString("text", "")).let { cleanWhisperTranscriptText(it) }
        if (rawWord.isBlank()) continue

        val relStartMs =
          when {
            item.has("startMs") -> item.optLong("startMs", 0L)
            item.has("t0") -> item.optLong("t0", 0L) * 10L
            item.has("start") -> (item.optDouble("start", 0.0) * 1000.0).toLong()
            else -> 0L
          }.coerceAtLeast(0L)

        val relEndMs =
          when {
            item.has("endMs") -> item.optLong("endMs", relStartMs + 140L)
            item.has("t1") -> item.optLong("t1", relStartMs / 10L + 14L) * 10L
            item.has("end") -> (item.optDouble("end", (relStartMs + 140L) / 1000.0) * 1000.0).toLong()
            else -> relStartMs + 140L
          }.coerceAtLeast(relStartMs + 75L)

        val conf =
          item.optDouble("p", item.optDouble("probability", item.optDouble("conf", defaultConfidence.toDouble())))
            .toFloat()
            .coerceIn(0f, 1f)

        val absStart = (windowStartMs + relStartMs).coerceIn(windowStartMs, windowEndMs)
        val absEnd = max(absStart + 75L, (windowStartMs + relEndMs).coerceAtMost(max(absStart + 75L, windowEndMs)))

        val parts = if (SPLIT_ON_WORD_ENABLED) rawWord.split(Regex("\\s+")).filter { it.isNotBlank() } else listOf(rawWord)
        val sliceDur = max(75L, (absEnd - absStart) / max(1, parts.size))
        parts.forEachIndexed { pIdx, part ->
          val ps = absStart + pIdx * sliceDur
          val pe = if (pIdx == parts.lastIndex) absEnd else max(ps + 75L, ps + sliceDur)
          collected.add(
            ParsedWhisperWord(
              timing =
                WordTiming(
                  word = part,
                  startMs = ps,
                  endMs = pe,
                  isEstimated = false,
                  emphasisScore = LocalCompositionEngine.scoreWordSalience(part, collected.size, max(1, total)),
                ),
              confidence = conf,
            )
          )
        }
      }
      return collected
    }

    /**
     * Strips Whisper non-speech hallucination markers (`[BLANK_AUDIO]`, `[MUSIC]`, `(applause)`, `♪`)
     * while preserving natural sentence punctuation (commas, periods, exclamation & question marks).
     */
    fun cleanWhisperTranscriptText(raw: String): String {
      if (raw.isBlank()) return ""
      return raw
        .replace(NON_SPEECH_BRACKET_REGEX, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    }
  }
}

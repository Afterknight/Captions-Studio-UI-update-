package com.example.captionstudio.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.engine.LocalCompositionEngine
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeechRecognitionState(
  val isAvailable: Boolean = false,
  val isListening: Boolean = false,
  val partialTranscript: String = "",
  val errorMessage: String? = null,
)

class SpeechTranscriptionManager(private val context: Context) {

  private val _state =
    MutableStateFlow(
      SpeechRecognitionState(isAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
  val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

  private var recognizer: SpeechRecognizer? = null
  private var sessionStartMs: Long = 0L
  private val observedWordTimestamps = mutableMapOf<Int, Long>()

  fun startListening(
    languageTag: String,
    baseOffsetMs: Long = 0L,
    onResult: (transcript: String, wordTimings: List<WordTiming>) -> Unit,
  ) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      _state.value =
        _state.value.copy(
          isAvailable = false,
          errorMessage = "On-device speech recognition is unavailable on this device. Enter or import transcript manually.",
        )
      return
    }
    stopListening()

    sessionStartMs = System.currentTimeMillis()
    observedWordTimestamps.clear()

    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer = speechRecognizer
    speechRecognizer.setRecognitionListener(
      object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
          _state.value =
            _state.value.copy(isListening = true, partialTranscript = "", errorMessage = null)
        }

        override fun onBeginningOfSpeech() {
          sessionStartMs = System.currentTimeMillis()
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
          _state.value = _state.value.copy(isListening = false)
        }

        override fun onError(error: Int) {
          val msg =
            when (error) {
              SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking closer or enter text manually."
              SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out."
              SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for live dictation."
              SpeechRecognizer.ERROR_NETWORK,
              SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Offline recognition pack not installed; check device speech settings or type manually."
              else -> "Speech recognizer error ($error)."
            }
          _state.value = _state.value.copy(isListening = false, errorMessage = msg)
        }

        override fun onPartialResults(partialResults: Bundle?) {
          val text =
            partialResults
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
              .orEmpty()
          if (text.isNotEmpty()) {
            val nowElapsed = (System.currentTimeMillis() - sessionStartMs).coerceAtLeast(0L)
            val count = text.split(Regex("\\s+")).size
            for (i in 0 until count) {
              observedWordTimestamps.putIfAbsent(i, baseOffsetMs + nowElapsed)
            }
            _state.value = _state.value.copy(partialTranscript = text)
          }
        }

        override fun onResults(results: Bundle?) {
          val text =
            results
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
              .orEmpty()
          _state.value = _state.value.copy(isListening = false, partialTranscript = "")
          if (text.isNotEmpty()) {
            val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            val totalElapsed =
              max((System.currentTimeMillis() - sessionStartMs), tokens.size * 340L)
            val timings =
              if (observedWordTimestamps.size >= tokens.size / 2) {
                tokens.mapIndexed { idx, word ->
                  val start = observedWordTimestamps[idx] ?: (baseOffsetMs + idx * 320L)
                  val nextStart =
                    observedWordTimestamps[idx + 1]
                      ?: (baseOffsetMs + totalElapsed)
                  WordTiming(
                    word = word,
                    startMs = start,
                    endMs = max(start + 180L, nextStart),
                    isEstimated = true,
                  )
                }
              } else {
                LocalCompositionEngine.buildProvisionalWordTimings(text, totalElapsed).map {
                  it.copy(
                    startMs = it.startMs + baseOffsetMs,
                    endMs = it.endMs + baseOffsetMs,
                    isEstimated = true,
                  )
                }
              }
            onResult(text, timings)
          }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
      }
    )

    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
      }
    speechRecognizer.startListening(intent)
  }

  fun stopListening() {
    recognizer?.let {
      runCatching { it.stopListening() }
      runCatching { it.destroy() }
    }
    recognizer = null
    _state.value = _state.value.copy(isListening = false)
  }

  companion object {
    /**
     * Parses SRT or WebVTT subtitle files into cue-accurate WordTiming entries.
     */
    fun parseSubtitles(rawContent: String): Pair<String, List<WordTiming>> {
      val lines = rawContent.replace("\r\n", "\n").split("\n")
      val timeRegex =
        Regex("(\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})")
      val wordTimings = mutableListOf<WordTiming>()
      val fullText = StringBuilder()

      var idx = 0
      while (idx < lines.size) {
        val match = timeRegex.find(lines[idx])
        if (match != null) {
          val startMs = parseTimestampMs(match.groupValues[1])
          val endMs = max(startMs + 250L, parseTimestampMs(match.groupValues[2]))
          idx++
          val cueLines = mutableListOf<String>()
          while (idx < lines.size && lines[idx].isNotBlank()) {
            cueLines.add(lines[idx].trim())
            idx++
          }
          val cueText = cueLines.joinToString(" ").replace(Regex("<[^>]+>"), "").trim()
          if (cueText.isNotEmpty()) {
            if (fullText.isNotEmpty()) fullText.append(" ")
            fullText.append(cueText)
            val words = cueText.split(Regex("\\s+")).filter { it.isNotBlank() }
            val cueDur = max(200L, endMs - startMs)
            val slice = cueDur / max(1, words.size)
            words.forEachIndexed { wIdx, w ->
              val wStart = startMs + slice * wIdx
              val wEnd = if (wIdx == words.lastIndex) endMs else wStart + slice
              wordTimings.add(
                WordTiming(
                  word = w,
                  startMs = wStart,
                  endMs = max(wStart + 120L, wEnd),
                  isEstimated = words.size > 1,
                )
              )
            }
          }
        }
        idx++
      }
      return fullText.toString() to wordTimings
    }

    private fun parseTimestampMs(raw: String): Long {
      val normalized = raw.replace(',', '.')
      val parts = normalized.split(":", ".")
      if (parts.size < 4) return 0L
      val h = parts[0].toLongOrNull() ?: 0L
      val m = parts[1].toLongOrNull() ?: 0L
      val s = parts[2].toLongOrNull() ?: 0L
      val ms = parts[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
      return ((h * 3600 + m * 60 + s) * 1000L) + ms
    }
  }
}

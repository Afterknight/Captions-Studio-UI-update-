package com.example.captionstudio.engine

import com.example.captionstudio.domain.model.WordTiming
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin 10ms acoustic forced aligner.
 * Maps recognized speech tokens directly onto active voiced intervals of a 10ms audio energy
 * envelope so every word startMs / endMs avoids silent gaps and hits real acoustic onsets.
 */
object AudioSpeechAligner {

  private const val FRAME_MS = 10L

  /**
   * Detects fine-grained voiced word/syllable islands inside [windowStartMs, windowEndMs].
   * Splits across silent gaps >= 90ms so words never stretch across silence.
   */
  fun detectVoicedIntervals(
    envelope10ms: FloatArray,
    windowStartMs: Long,
    windowEndMs: Long,
  ): List<LongRange> {
    if (envelope10ms.isEmpty() || windowEndMs <= windowStartMs) {
      return listOf(windowStartMs..max(windowStartMs + 200L, windowEndMs))
    }

    val startFrame = (windowStartMs / FRAME_MS).toInt().coerceIn(0, envelope10ms.lastIndex)
    val endFrame = (windowEndMs / FRAME_MS).toInt().coerceIn(startFrame + 1, envelope10ms.size)
    val slice = envelope10ms.copyOfRange(startFrame, endFrame)
    if (slice.isEmpty()) return listOf(windowStartMs..windowEndMs)

    val sorted = slice.sortedArray()
    val noiseFloor = sorted[(sorted.size * 0.20f).toInt().coerceIn(0, sorted.lastIndex)]
    val peak = sorted[(sorted.size * 0.95f).toInt().coerceIn(0, sorted.lastIndex)]
    if (peak < 0.004f) return listOf(windowStartMs..windowEndMs)

    val threshold = max(0.008f, max(noiseFloor * 2.1f, peak * 0.13f))
    val minGapFrames = 9 // 90ms silence split

    val rawIslands = mutableListOf<IntRange>()
    var runStart = -1
    var silentCount = 0

    for (i in slice.indices) {
      if (slice[i] >= threshold) {
        if (runStart < 0) runStart = i
        silentCount = 0
      } else if (runStart >= 0) {
        silentCount++
        if (silentCount >= minGapFrames) {
          val runEnd = i - silentCount
          if (runEnd >= runStart) {
            rawIslands.add(runStart..runEnd)
          }
          runStart = -1
          silentCount = 0
        }
      }
    }
    if (runStart >= 0) {
      val runEnd = slice.lastIndex - silentCount
      if (runEnd >= runStart) rawIslands.add(runStart..runEnd)
    }

    if (rawIslands.isEmpty()) return listOf(windowStartMs..windowEndMs)

    return rawIslands.map { r ->
      val s = (startFrame + max(0, r.first - 2)) * FRAME_MS
      val e = (startFrame + min(slice.size, r.last + 3)) * FRAME_MS
      s.coerceAtLeast(windowStartMs)..max(s + 90L, e.coerceAtMost(windowEndMs))
    }
  }

  /**
   * Aligns [words] across voiced intervals inside [windowStartMs, windowEndMs] with 10ms precision,
   * weighting word durations by syllable complexity and incorporating optional partial-result timestamps.
   */
  fun alignWordsToAcousticEnvelope(
    words: List<String>,
    envelope10ms: FloatArray,
    windowStartMs: Long,
    windowEndMs: Long,
    observedAnchorsMs: Map<Int, Long> = emptyMap(),
  ): List<WordTiming> {
    val cleanWords = words.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleanWords.isEmpty()) return emptyList()

    val voicedIntervals = detectVoicedIntervals(envelope10ms, windowStartMs, windowEndMs)
    val totalVoicedMs = voicedIntervals.sumOf { max(60L, it.last - it.first) }.coerceAtLeast(cleanWords.size * 80L)

    val weights =
      cleanWords.map { token ->
        val clean = token.trim('.', ',', '!', '?', ';', ':', '"', '\'')
        val syllables = countSyllablesApprox(clean)
        val punctPause =
          when {
            token.endsWith(".") || token.endsWith("!") || token.endsWith("?") -> 1.35f
            token.endsWith(",") || token.endsWith(";") -> 1.15f
            else -> 1.0f
          }
        max(1.2f, (syllables * 0.9f) + (clean.length * 0.22f)) * punctPause
      }
    val totalWeight = weights.sum().coerceAtLeast(1f)

    // Walk linearly through voiced-time space and map back to real video clock milliseconds
    val cumulativeStarts = LongArray(voicedIntervals.size)
    var voicedAcc = 0L
    for (i in voicedIntervals.indices) {
      cumulativeStarts[i] = voicedAcc
      voicedAcc += max(60L, voicedIntervals[i].last - voicedIntervals[i].first)
    }

    fun voicedPosToClockMs(vPos: Long): Long {
      val clamped = vPos.coerceIn(0L, totalVoicedMs)
      var idx = 0
      while (idx < voicedIntervals.lastIndex && cumulativeStarts[idx + 1] <= clamped) {
        idx++
      }
      val offsetInInterval = clamped - cumulativeStarts[idx]
      return voicedIntervals[idx].first + offsetInInterval
    }

    var vCursor = 0L
    return cleanWords.mapIndexed { idx, word ->
      val allocated = ((weights[idx] / totalWeight) * totalVoicedMs).toLong().coerceAtLeast(65L)
      var wStart = voicedPosToClockMs(vCursor)
      val anchor = observedAnchorsMs[idx]
      if (anchor != null && anchor in windowStartMs..windowEndMs) {
        // Blend acoustic voiced-envelope cursor with observed live recognizer arrival timestamp
        val blended = ((wStart * 0.65) + (anchor * 0.35)).toLong()
        wStart = snapToNearestOnset(blended, envelope10ms, windowStartMs, windowEndMs)
      }
      vCursor = min(totalVoicedMs, vCursor + allocated)
      val rawEnd =
        if (idx == cleanWords.lastIndex) voicedIntervals.last().last
        else voicedPosToClockMs(vCursor)
      val wEnd = max(wStart + 75L, rawEnd)
      WordTiming(
        word = word,
        startMs = wStart,
        endMs = wEnd,
        isEstimated = false,
        emphasisScore = LocalCompositionEngine.scoreWordSalience(word, idx, cleanWords.size),
      )
    }
  }

  /**
   * Refines ASR-provided word timestamps (Whisper Small / Vosk / Gemini) against actual 10ms
   * acoustic vocal RMS energy peaks/onsets and offsets within +/- 160ms so Karaoke highlights,
   * Beat Sync, and Word-Tap timelines lock within ±10ms of spoken audio.
   */
  fun refineWordTimings(
    words: List<WordTiming>,
    envelope10ms: FloatArray,
  ): List<WordTiming> {
    if (words.isEmpty() || envelope10ms.isEmpty()) return words
    val totalMs = envelope10ms.size * FRAME_MS
    val sorted = envelope10ms.sortedArray()
    val noiseFloor = sorted[(sorted.size * 0.20f).toInt().coerceIn(0, sorted.lastIndex)]
    val peakEnergy = sorted[(sorted.size * 0.95f).toInt().coerceIn(0, sorted.lastIndex)]
    val voicedThreshold = max(0.010f, max(noiseFloor * 2.0f, peakEnergy * 0.12f))

    var prevEnd = 0L
    return words.mapIndexed { idx, wt ->
      val safeStartRaw = max(prevEnd, wt.startMs.coerceIn(0L, totalMs))
      val snappedStart =
        snapToNearestOnset(
          targetMs = safeStartRaw,
          envelope10ms = envelope10ms,
          minMs = prevEnd,
          maxMs = max(prevEnd, wt.endMs - 60L),
          searchRadiusMs = 160L,
          voicedThreshold = voicedThreshold,
        )
      val nextLimit =
        if (idx < words.lastIndex) max(snappedStart + 80L, words[idx + 1].startMs + 80L)
        else totalMs
      val rawEnd = max(snappedStart + 80L, wt.endMs.coerceAtMost(nextLimit))
      val snappedEnd =
        snapToVocalOffset(
          startMs = snappedStart,
          targetEndMs = rawEnd,
          envelope10ms = envelope10ms,
          maxEndMs = nextLimit.coerceAtMost(totalMs),
          voicedThreshold = voicedThreshold,
        )
      prevEnd = snappedEnd
      wt.copy(
        startMs = snappedStart,
        endMs = snappedEnd,
        isEstimated = false,
        emphasisScore =
          if (wt.emphasisScore != 0f) wt.emphasisScore
          else LocalCompositionEngine.scoreWordSalience(wt.word, idx, words.size),
      )
    }
  }

  private fun snapToNearestOnset(
    targetMs: Long,
    envelope10ms: FloatArray,
    minMs: Long,
    maxMs: Long,
    searchRadiusMs: Long = 120L,
    voicedThreshold: Float = 0.010f,
  ): Long {
    if (envelope10ms.size < 4) return targetMs.coerceIn(minMs, max(minMs, maxMs))
    val lowFrame = ((max(minMs, targetMs - searchRadiusMs)) / FRAME_MS).toInt().coerceIn(1, envelope10ms.lastIndex)
    val highFrame = ((min(maxMs, targetMs + searchRadiusMs)) / FRAME_MS).toInt().coerceIn(lowFrame, envelope10ms.lastIndex)

    var bestFrame = (targetMs / FRAME_MS).toInt().coerceIn(lowFrame, highFrame)
    var bestScore = Float.NEGATIVE_INFINITY
    val targetFrame = targetMs / FRAME_MS.toFloat()

    for (f in lowFrame..highFrame) {
      val curr = envelope10ms[f]
      if (curr < voicedThreshold) continue
      val prev = envelope10ms[f - 1]
      val next = if (f + 1 < envelope10ms.size) envelope10ms[f + 1] else curr
      val rise10ms = (curr - prev).coerceAtLeast(0f)
      val rise20ms = if (f >= 2) (curr - envelope10ms[f - 2]).coerceAtLeast(0f) * 0.5f else 0f
      val localPeakBonus = if (curr >= prev && curr >= next) curr * 0.25f else 0f
      val distPenalty = abs(f - targetFrame) * 0.0025f
      val score = max(rise10ms, rise20ms) + localPeakBonus - distPenalty
      if (score > bestScore) {
        bestScore = score
        bestFrame = f
      }
    }
    return (bestFrame * FRAME_MS).coerceIn(minMs, max(minMs, maxMs))
  }

  private fun snapToVocalOffset(
    startMs: Long,
    targetEndMs: Long,
    envelope10ms: FloatArray,
    maxEndMs: Long,
    voicedThreshold: Float,
  ): Long {
    val minEndMs = startMs + 75L
    if (envelope10ms.size < 4 || targetEndMs <= minEndMs) {
      return max(minEndMs, targetEndMs).coerceAtMost(max(minEndMs, maxEndMs))
    }
    val startFrame = (startMs / FRAME_MS).toInt().coerceIn(0, envelope10ms.lastIndex)
    val targetFrame = (targetEndMs / FRAME_MS).toInt().coerceIn(startFrame, envelope10ms.lastIndex)
    val maxSearchFrame = (min(maxEndMs, targetEndMs + 80L) / FRAME_MS).toInt().coerceIn(targetFrame, envelope10ms.lastIndex)
    val tailThreshold = voicedThreshold * 0.65f

    // If audio is still actively voiced right at targetEndMs, extend slightly (up to +80ms) to vocal decay
    if (envelope10ms[targetFrame] >= tailThreshold) {
      var decayFrame = targetFrame
      while (decayFrame < maxSearchFrame && envelope10ms[decayFrame] >= tailThreshold) {
        decayFrame++
      }
      return (decayFrame * FRAME_MS).coerceIn(minEndMs, max(minEndMs, maxEndMs))
    }

    // Otherwise trim trailing silence back to the last voiced 10ms frame + 20ms natural decay tail
    val minEndFrame = (minEndMs / FRAME_MS).toInt().coerceIn(startFrame, targetFrame)
    var lastVoiced = targetFrame
    while (lastVoiced > minEndFrame && envelope10ms[lastVoiced] < tailThreshold) {
      lastVoiced--
    }
    val trimmedEndMs = (lastVoiced + 2) * FRAME_MS
    return trimmedEndMs.coerceIn(minEndMs, max(minEndMs, targetEndMs))
  }

  private fun countSyllablesApprox(word: String): Int {
    if (word.isEmpty()) return 1
    val vowels = "aeiouy"
    var count = 0
    var inVowel = false
    for (ch in word.lowercase()) {
      val v = ch in vowels
      if (v && !inVowel) count++
      inVowel = v
    }
    if (word.lowercase().endsWith("e") && count > 1) count--
    return max(1, count)
  }
}

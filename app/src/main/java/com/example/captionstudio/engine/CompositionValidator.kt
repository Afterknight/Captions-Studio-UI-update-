package com.example.captionstudio.engine

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.SpatialLayoutMode
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextSurfaceStyle
import com.example.captionstudio.typography.FontRegistry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object CompositionValidator {

  private val validColors =
    setOf(
      StudioPalette.WHITE,
      StudioPalette.YELLOW,
      StudioPalette.ORANGE,
      StudioPalette.RED,
      StudioPalette.CYAN,
    )

  /**
   * Italic display/editorial serifs whose smaller lowercase x-heights require an optical +8% boost
   * when paired inline next to geometric sans fonts.
   */
  val italicSerifFontIds: Set<String> =
    setOf(
      "playfair_display",
      "cormorant_garamond",
      "bodoni_moda",
      "libre_baskerville",
    )

  val geometricSansFontIds: Set<String> =
    setOf(
      "inter",
      "plus_jakarta_sans",
      "dm_sans",
      "manrope",
      "montserrat",
      "space_grotesk",
      "roboto",
      "syne",
      "archivo_black",
      "oswald",
      "anton",
      "bebas_neue",
    )

  const val ITALIC_SERIF_OPTICAL_SCALE_BOOST = 1.08f
  const val TWO_LINE_MAX_WIDTH_VARIANCE = 0.20f

  /**
   * Validates and repairs AI-generated or locally generated compositions before rendering.
   * Ensures text completeness, valid non-overlapping timestamps, valid fonts, safe screen bounds,
   * optical serif/sans x-height alignment, zero-widow 2-line balancing, readable font sizes,
   * and zero horizontal overflow using real Typeface Paint metrics.
   */
  fun validateAndRepair(
    context: Context?,
    compositions: List<CaptionComposition>,
    videoWidthPx: Int = 1080,
    videoHeightPx: Int = 1920,
  ): List<CaptionComposition> {
    if (compositions.isEmpty()) return emptyList()

    val sorted = compositions.sortedBy { it.startMs }
    val repaired = mutableListOf<CaptionComposition>()
    var prevEndMs = 0L

    for (comp in sorted) {
      val safeStart = max(prevEndMs, comp.startMs.coerceAtLeast(0L))
      val safeEnd = max(safeStart + 360L, comp.endMs)
      prevEndMs = safeEnd

      val safeX = comp.anchorXFraction.coerceIn(0.18f, 0.82f)
      val safeY = comp.anchorYFraction.coerceIn(0.12f, 0.88f)
      val safeEntry = comp.entryDurationMs.coerceIn(80, 350)
      val safeExit = comp.exitDurationMs.coerceIn(80, 300)

      val repairedElements =
        repairElements(
          context = context,
          elements = comp.elements,
          originalText = comp.originalText,
          videoWidthPx = videoWidthPx,
          videoHeightPx = videoHeightPx,
          spatialMode = comp.spatialMode,
        )

      repaired.add(
        comp.copy(
          startMs = safeStart,
          endMs = safeEnd,
          anchorXFraction = safeX,
          anchorYFraction = safeY,
          entryDurationMs = safeEntry,
          exitDurationMs = safeExit,
          elements = repairedElements,
        )
      )
    }
    return repaired
  }

  private fun repairElements(
    context: Context?,
    elements: List<CompositionElement>,
    originalText: String,
    videoWidthPx: Int,
    videoHeightPx: Int,
    spatialMode: SpatialLayoutMode,
  ): List<CompositionElement> {
    val nonEmpty = elements.filter { it.text.isNotBlank() }
    val baseList =
      if (nonEmpty.isEmpty()) {
        listOf(
          CompositionElement(
            id = "repaired_fallback",
            text = originalText.ifBlank { "Caption" },
            role = FontRole.SUPPORTING,
            hierarchyLevel = HierarchyLevel.SUPPORTING,
            fontFamilyId = "inter",
            fontWeight = 600,
            isItalic = false,
            fontSizeSp = 22f,
            colorHex = StudioPalette.WHITE,
          )
        )
      } else {
        nonEmpty
      }

    // Check text completeness against originalText
    val combinedNorm = normalizeWords(baseList.joinToString(" ") { it.text })
    val originalNorm = normalizeWords(originalText)
    val completeList =
      if (originalNorm.isNotEmpty() && !combinedNorm.contains(originalNorm.first())) {
        baseList +
          CompositionElement(
            id = "repaired_missing",
            text = originalText,
            role = FontRole.SUPPORTING,
            hierarchyLevel = HierarchyLevel.SUPPORTING,
            fontFamilyId = "inter",
            fontWeight = 500,
            fontSizeSp = 19f,
            colorHex = StudioPalette.WHITE,
          )
      } else {
        baseList
      }

    val referenceScale = min(videoWidthPx, videoHeightPx) / 360f
    val maxLineWidthPx = videoWidthPx * 0.84f
    val inlineGroups = completeList.groupBy { it.inlineRowGroup }

    return completeList.map { el ->
      val validFont = FontRegistry.findById(el.fontFamilyId).id
      val minSize = if (el.hierarchyLevel == HierarchyLevel.DOMINANT) 26f else 14f
      val maxSize = if (el.hierarchyLevel == HierarchyLevel.DOMINANT) 68f else 42f
      var clampedSp = el.fontSizeSp.coerceIn(minSize, maxSize)
      val safeColor = normalizeHex(el.colorHex)

      // Optical Typography Rule: when an italic serif font is paired inline next to a geometric
      // sans font, boost its relative optical scale by +8% so their x-heights match visually.
      val inlinePeers = inlineGroups[el.inlineRowGroup].orEmpty()
      if (el.isItalic && validFont in italicSerifFontIds && inlinePeers.size > 1) {
        val sansNeighbors =
          inlinePeers.filter { peer ->
            peer.id != el.id && FontRegistry.findById(peer.fontFamilyId).id in geometricSansFontIds
          }
        if (sansNeighbors.isNotEmpty()) {
          val nearestSansSp =
            sansNeighbors.minByOrNull { abs(it.fontSizeSp - el.fontSizeSp) }!!.fontSizeSp
          // Guard against double-boosting if validateAndRepair is invoked twice on the same row.
          if (clampedSp <= nearestSansSp * 1.05f) {
            clampedSp = (clampedSp * ITALIC_SERIF_OPTICAL_SCALE_BOOST).coerceIn(minSize, maxSize)
          }
        }
      }

      val scaleXFactor =
        if (el.surfaceStyle == TextSurfaceStyle.CONDENSED_EDITORIAL) 0.82f else 1.0f

      // Measure actual rendered width if Context is available, otherwise use conservative typographic metrics
      val displayText = if (el.uppercase) el.text.uppercase() else el.text
      val tokens = displayText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
      var activePaint: TextPaint? = null
      if (context != null && referenceScale > 0f) {
        val paint =
          TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = FontRegistry.getTypeface(context, validFont, el.fontWeight, el.isItalic)
            if (el.isItalic) textSkewX = -0.20f
            letterSpacing = el.letterSpacingEm
            textScaleX = scaleXFactor
            textSize = clampedSp * referenceScale
          }
        val widestToken =
          tokens.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(displayText)
        if (widestToken > maxLineWidthPx) {
          val shrinkRatio = (maxLineWidthPx / widestToken).coerceIn(0.45f, 1f)
          clampedSp = (clampedSp * shrinkRatio).coerceAtLeast(minSize)
          paint.textSize = clampedSp * referenceScale
        }
        activePaint = paint
      } else if (referenceScale > 0f && tokens.isNotEmpty()) {
        val longestChars = tokens.maxOf { it.length }
        val glyphWidthRatio =
          ((if (el.uppercase) 0.62f else 0.56f) + el.letterSpacingEm.coerceAtLeast(0f)) *
            scaleXFactor
        val estWidestPx = longestChars * (clampedSp * referenceScale) * glyphWidthRatio
        if (estWidestPx > maxLineWidthPx) {
          val shrinkRatio = (maxLineWidthPx / estWidestPx).coerceIn(0.45f, 1f)
          clampedSp = (clampedSp * shrinkRatio).coerceAtLeast(minSize)
        }
      }

      // Zero-widow 2-line balancer for standalone multi-word blocks (skipped on curved single-arc paths)
      val balancedText =
        if (spatialMode != SpatialLayoutMode.CURVED_ARC_PATH &&
          inlinePeers.size == 1 &&
          tokens.size >= 3 &&
          referenceScale > 0f
        ) {
          val measureLineWidth: (String) -> Float = { candidate ->
            val shaped = if (el.uppercase) candidate.uppercase() else candidate
            val p = activePaint
            if (p != null) {
              p.measureText(shaped)
            } else {
              estimateTextWidthPx(
                text = shaped,
                textSizePx = clampedSp * referenceScale,
                uppercase = el.uppercase,
                letterSpacingEm = el.letterSpacingEm,
                scaleXFactor = scaleXFactor,
              )
            }
          }
          val balancedLines =
            balanceTwoLineText(
              text = el.text,
              maxLineWidthPx = maxLineWidthPx,
              measureWidth = measureLineWidth,
            )
          if (balancedLines.size == 2 || el.text.contains('\n')) {
            balancedLines.joinToString("\n")
          } else {
            el.text
          }
        } else {
          el.text
        }

      el.copy(
        text = balancedText,
        fontFamilyId = validFont,
        fontWeight = el.fontWeight.coerceIn(300, 900),
        fontSizeSp = clampedSp,
        colorHex = safeColor,
        letterSpacingEm = el.letterSpacingEm.coerceIn(-0.10f, 0.40f),
        lineHeightMultiplier = el.lineHeightMultiplier.coerceIn(0.80f, 1.80f),
        opacity = el.opacity.coerceIn(0.10f, 1.0f),
        rotationDeg = el.rotationDeg.coerceIn(-25f, 25f),
        tiltDegrees = el.tiltDegrees.coerceIn(-18f, 18f),
        offsetXEm = el.offsetXEm.coerceIn(-0.6f, 0.6f),
        offsetYEm = el.offsetYEm.coerceIn(-0.6f, 0.6f),
        strokeWidthFactor = el.strokeWidthFactor.coerceIn(0f, 10f),
        strokeColorHex = normalizeHex(el.strokeColorHex),
        glowColorHex = el.glowColorHex?.takeIf { it.isNotBlank() }?.let { normalizeHex(it) },
        glowIntensity = el.glowIntensity.coerceIn(0.25f, 2.2f),
        shadowIntensity = el.shadowIntensity.coerceIn(0f, 2.2f),
        shadowColorHex = normalizeHex(el.shadowColorHex),
        backgroundColorHex = el.backgroundColorHex?.takeIf { it.isNotBlank() }?.let { normalizeHex(it) },
        backgroundAlpha = el.backgroundAlpha.coerceIn(0.1f, 1.0f),
        backgroundCornerRadiusDp = el.backgroundCornerRadiusDp.coerceIn(0f, 32f),
        motionSpeed = el.motionSpeed.coerceIn(0.4f, 2.2f),
        motionIntensity = el.motionIntensity.coerceIn(0.25f, 2.0f),
      )
    }
  }

  /**
   * Zero-widow 2-line balancer for multi-word blocks.
   * Keeps single-line text untouched when it fits on one line without explicit breaks.
   * When text wraps onto 2 lines (or has an explicit 2-line break), chooses a split that avoids
   * leaving a single orphan word on line 2 and keeps line widths within ±20% variance whenever
   * a valid split fits within [maxLineWidthPx].
   */
  fun balanceTwoLineText(
    text: String,
    maxLineWidthPx: Float,
    measureWidth: (String) -> Float,
  ): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return listOf(text)

    val hasExplicitNewline = trimmed.contains('\n')
    val explicitLines =
      if (hasExplicitNewline) {
        trimmed.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
      } else {
        emptyList()
      }

    // Preserve intentional 3+ line scripts while balancing any overflowing sub-lines cleanly
    if (explicitLines.size > 2) {
      return explicitLines.flatMap { subLine ->
        balanceTwoLineText(subLine, maxLineWidthPx, measureWidth)
      }
    }

    val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size <= 1) return listOf(words.joinToString(" "))

    val singleLine = words.joinToString(" ")
    if (!hasExplicitNewline && measureWidth(singleLine) <= maxLineWidthPx) {
      return listOf(singleLine)
    }

    data class SplitCandidate(
      val line1: String,
      val line2: String,
      val w1: Float,
      val w2: Float,
      val wordsOnLine1: Int,
      val wordsOnLine2: Int,
    ) {
      val fits: Boolean
        get() = w1 <= maxLineWidthPx && w2 <= maxLineWidthPx

      val hasWidow: Boolean
        get() = wordsOnLine2 == 1

      val hasTopOrphan: Boolean
        get() = wordsOnLine1 == 1 && (wordsOnLine1 + wordsOnLine2) >= 4

      val variance: Float
        get() {
          val maxW = max(w1, w2).coerceAtLeast(1f)
          return abs(w1 - w2) / maxW
        }
    }

    val candidates =
      (1 until words.size).map { splitIdx ->
        val l1 = words.subList(0, splitIdx).joinToString(" ")
        val l2 = words.subList(splitIdx, words.size).joinToString(" ")
        SplitCandidate(
          line1 = l1,
          line2 = l2,
          w1 = measureWidth(l1),
          w2 = measureWidth(l2),
          wordsOnLine1 = splitIdx,
          wordsOnLine2 = words.size - splitIdx,
        )
      }

    val fitting = candidates.filter { it.fits }
    if (fitting.isNotEmpty()) {
      val noWidow = fitting.filter { !it.hasWidow }
      // Tier 1: zero-widow, no top orphan, within ±20% width variance
      val ideal =
        noWidow
          .filter { !it.hasTopOrphan && it.variance <= TWO_LINE_MAX_WIDTH_VARIANCE }
          .minByOrNull { it.variance }
      // Tier 2: zero-widow within ±20% width variance
      val balancedNoWidow =
        ideal
          ?: noWidow
            .filter { it.variance <= TWO_LINE_MAX_WIDTH_VARIANCE }
            .minByOrNull { it.variance }
      // Tier 3: best zero-widow split (smallest width variance, preferring balanced word counts)
      val bestNoWidow =
        balancedNoWidow
          ?: noWidow.minWithOrNull(
            compareBy<SplitCandidate> { it.hasTopOrphan }.thenBy { it.variance }
          )
      // Tier 4: fallback when only 2 words exist or last word is unusually wide
      val best = bestNoWidow ?: fitting.minByOrNull { it.variance }!!
      return listOf(best.line1, best.line2)
    }

    // Requires 3+ lines: greedy wrap followed by a zero-widow pull on the final two lines
    val greedyLines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
      val candidate = if (current.isEmpty()) word else "$current $word"
      if (measureWidth(candidate) <= maxLineWidthPx || current.isEmpty()) {
        current = StringBuilder(candidate)
      } else {
        greedyLines.add(current.toString())
        current = StringBuilder(word)
      }
    }
    if (current.isNotEmpty()) greedyLines.add(current.toString())

    if (greedyLines.size >= 2) {
      val lastWords = greedyLines.last().split(" ").filter { it.isNotEmpty() }
      val prevWords = greedyLines[greedyLines.lastIndex - 1].split(" ").filter { it.isNotEmpty() }
      if (lastWords.size == 1 && prevWords.size >= 3) {
        val movedPrev = prevWords.dropLast(1).joinToString(" ")
        val movedLast = (listOf(prevWords.last()) + lastWords).joinToString(" ")
        if (measureWidth(movedLast) <= maxLineWidthPx) {
          greedyLines[greedyLines.lastIndex - 1] = movedPrev
          greedyLines[greedyLines.lastIndex] = movedLast
        }
      }
    }
    return greedyLines
  }

  private fun estimateTextWidthPx(
    text: String,
    textSizePx: Float,
    uppercase: Boolean,
    letterSpacingEm: Float,
    scaleXFactor: Float,
  ): Float {
    val baseRatio =
      ((if (uppercase) 0.62f else 0.56f) + letterSpacingEm.coerceAtLeast(0f)) * scaleXFactor
    var widthUnits = 0f
    for (ch in text) {
      widthUnits +=
        when {
          ch == ' ' -> 0.42f
          ch in "ilI1|.,!':;" -> 0.48f
          ch in "mwMW@" -> 1.32f
          else -> 1.0f
        }
    }
    return widthUnits * textSizePx * baseRatio
  }

  private fun normalizeHex(hex: String): String {
    val upper = hex.trim().uppercase()
    if (upper in validColors) return upper
    return if (Regex("^#[0-9A-F]{6}$").matches(upper)) upper else StudioPalette.WHITE
  }

  private fun normalizeWords(input: String): List<String> {
    return input.lowercase().split(Regex("\\s+")).map {
      it.trim('.', ',', '!', '?', ';', ':', '"', '\'')
    }.filter { it.isNotEmpty() }
  }
}

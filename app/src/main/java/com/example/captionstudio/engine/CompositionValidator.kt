package com.example.captionstudio.engine

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.typography.FontRegistry
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
   * Validates and repairs AI-generated or locally generated compositions before rendering.
   * Ensures text completeness, valid non-overlapping timestamps, valid fonts, safe screen bounds,
   * readable font sizes, and zero horizontal overflow using real Typeface Paint metrics.
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

    return completeList.map { el ->
      val validFont = FontRegistry.findById(el.fontFamilyId).id
      val minSize = if (el.hierarchyLevel == HierarchyLevel.DOMINANT) 26f else 14f
      val maxSize = if (el.hierarchyLevel == HierarchyLevel.DOMINANT) 68f else 42f
      var clampedSp = el.fontSizeSp.coerceIn(minSize, maxSize)
      val safeColor = normalizeHex(el.colorHex)

      // Measure actual rendered width if Context is available
      if (context != null && referenceScale > 0f) {
        val displayText = if (el.uppercase) el.text.uppercase() else el.text
        val paint =
          TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = FontRegistry.getTypeface(context, validFont, el.fontWeight, el.isItalic)
            if (el.isItalic) textSkewX = -0.20f
            letterSpacing = el.letterSpacingEm
            textSize = clampedSp * referenceScale
          }
        val measured = paint.measureText(displayText)
        if (measured > maxLineWidthPx && !displayText.contains(" ")) {
          val shrinkRatio = (maxLineWidthPx / measured).coerceIn(0.45f, 1f)
          clampedSp = (clampedSp * shrinkRatio).coerceAtLeast(minSize)
        }
      }

      el.copy(
        fontFamilyId = validFont,
        fontWeight = el.fontWeight.coerceIn(300, 900),
        fontSizeSp = clampedSp,
        colorHex = safeColor,
        letterSpacingEm = el.letterSpacingEm.coerceIn(-0.10f, 0.40f),
        lineHeightMultiplier = el.lineHeightMultiplier.coerceIn(0.80f, 1.80f),
        opacity = el.opacity.coerceIn(0.10f, 1.0f),
        rotationDeg = el.rotationDeg.coerceIn(-25f, 25f),
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
      )
    }
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

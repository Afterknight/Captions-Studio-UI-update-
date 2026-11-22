package com.example.captionstudio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.TextAlignment
import com.example.captionstudio.typography.FontRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Unified kinetic composition renderer shared identically between Jetpack Compose Live Preview
 * and Media3 Transformer CanvasOverlay export.
 *
 * Supports:
 * - True per-word timed kinetic reveals & active spoken beat micro-pulse
 * - Horizontal inlineRowGroup pairing (e.g. "and" + "keeps", "click" + gold "Dynamic Captions")
 * - Soft editorial drop shadows & optional warm amber/neon glow bloom
 * - Micro-rotation & baseline overlap offsets for handwritten brush accents
 * - Behind-subject ML Kit DST_OUT occlusion so giant headlines tuck seamlessly behind the speaker
 */
class KineticCompositionRenderer(private val context: Context) {

  private data class MeasuredWordSpan(
    val text: String,
    val startXInLine: Float,
    val wordIndexInComp: Int?,
  )

  private data class MeasuredLine(
    val text: String,
    val widthPx: Float,
    val ascentPx: Float,
    val descentPx: Float,
    val words: List<MeasuredWordSpan>,
  ) {
    val heightPx: Float
      get() = descentPx - ascentPx
  }

  private data class MeasuredElement(
    val element: CompositionElement,
    val paint: TextPaint,
    val glowPaint: TextPaint?,
    val strokePaint: TextPaint?,
    val backgroundPaint: Paint?,
    val lines: List<MeasuredLine>,
    val blockWidthPx: Float,
    val blockHeightPx: Float,
    val centerOffsetXFromAnchorPx: Float,
    val topYOffsetPx: Float,
    val lineSpacingFactor: Float,
  )

  private data class CachedLayout(
    val key: String,
    val measuredElements: List<MeasuredElement>,
    val totalHeightPx: Float,
    val maxWidthPx: Float,
    val anchorX: Float,
    val anchorY: Float,
  )

  private val layoutCache = ConcurrentHashMap<String, CachedLayout>()
  private val boxPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.5f
    }
  private val maskErasePaint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

  fun clearCache() {
    layoutCache.clear()
  }

  fun drawAtTime(
    canvas: Canvas,
    canvasWidth: Int,
    canvasHeight: Int,
    compositions: List<CaptionComposition>,
    positionMs: Long,
    shadowMultiplier: Float = 1.0f,
    selectedCompositionId: String? = null,
    drawSelectionGuide: Boolean = false,
  ) {
    drawWithSubjectMask(
      canvas = canvas,
      canvasWidth = canvasWidth,
      canvasHeight = canvasHeight,
      compositions = compositions,
      positionMs = positionMs,
      subjectMaskBitmap = null,
      wrapInOffscreenLayer = false,
      shadowMultiplier = shadowMultiplier,
      selectedCompositionId = selectedCompositionId,
      drawSelectionGuide = drawSelectionGuide,
    )
  }

  /**
   * 3-pass depth-aware renderer:
   * 1. Draw all [DepthLayer.BEHIND_SUBJECT] elements
   * 2. Erase pixels occluded by [subjectMaskBitmap] via PorterDuff.DST_OUT
   * 3. Draw all [DepthLayer.IN_FRONT] elements cleanly on top
   */
  fun drawWithSubjectMask(
    canvas: Canvas,
    canvasWidth: Int,
    canvasHeight: Int,
    compositions: List<CaptionComposition>,
    positionMs: Long,
    subjectMaskBitmap: Bitmap?,
    wrapInOffscreenLayer: Boolean,
    shadowMultiplier: Float = 1.0f,
    selectedCompositionId: String? = null,
    drawSelectionGuide: Boolean = false,
  ) {
    if (canvasWidth <= 0 || canvasHeight <= 0) return
    val active = compositions.firstOrNull { positionMs in it.startMs..it.endMs } ?: return

    val layout = getOrComputeLayout(active, canvasWidth, canvasHeight, shadowMultiplier)
    val boxTop =
      (layout.anchorY - layout.totalHeightPx / 2f).coerceIn(
        canvasHeight * 0.05f,
        max(canvasHeight * 0.05f, canvasHeight * 0.95f - layout.totalHeightPx),
      )

    if (drawSelectionGuide && active.id == selectedCompositionId) {
      boxPaint.color = Color.argb(140, 255, 184, 28)
      val halfW = layout.maxWidthPx / 2f + 16f
      val rect =
        RectF(
          (layout.anchorX - halfW).coerceAtLeast(8f),
          (boxTop - 12f).coerceAtLeast(8f),
          (layout.anchorX + halfW).coerceAtMost(canvasWidth - 8f),
          (boxTop + layout.totalHeightPx + 12f).coerceAtMost(canvasHeight - 8f),
        )
      canvas.drawRoundRect(rect, 12f, 12f, boxPaint)
    }

    val useMask =
      subjectMaskBitmap != null &&
        !subjectMaskBitmap.isRecycled &&
        active.hasBehindSubjectElements()

    val saveLayerCount =
      if (useMask && wrapInOffscreenLayer) {
        canvas.saveLayer(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), null)
      } else {
        -1
      }

    // Pass 1: Behind-subject elements
    for (measured in layout.measuredElements) {
      val effectiveLayer =
        if (active.backgroundMaskEnabled &&
          (measured.element.depthLayer == DepthLayer.BEHIND_SUBJECT ||
            (active.depthLayer == DepthLayer.BEHIND_SUBJECT &&
              measured.element.hierarchyLevel == HierarchyLevel.DOMINANT))
        ) {
          DepthLayer.BEHIND_SUBJECT
        } else {
          DepthLayer.IN_FRONT
        }
      if (effectiveLayer == DepthLayer.BEHIND_SUBJECT) {
        drawMeasuredElement(canvas, canvasWidth, canvasHeight, active, layout, boxTop, measured, positionMs)
      }
    }

    // Pass 2: Occlude behind-subject text cleanly behind speaker hair/head/shoulders
    if (useMask && subjectMaskBitmap != null) {
      val srcRect = Rect(0, 0, subjectMaskBitmap.width, subjectMaskBitmap.height)
      val dstRect = RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())
      canvas.drawBitmap(subjectMaskBitmap, srcRect, dstRect, maskErasePaint)
    }

    // Pass 3: Foreground crisp readable elements
    for (measured in layout.measuredElements) {
      val effectiveLayer =
        if (active.backgroundMaskEnabled &&
          (measured.element.depthLayer == DepthLayer.BEHIND_SUBJECT ||
            (active.depthLayer == DepthLayer.BEHIND_SUBJECT &&
              measured.element.hierarchyLevel == HierarchyLevel.DOMINANT))
        ) {
          DepthLayer.BEHIND_SUBJECT
        } else {
          DepthLayer.IN_FRONT
        }
      if (effectiveLayer == DepthLayer.IN_FRONT) {
        drawMeasuredElement(canvas, canvasWidth, canvasHeight, active, layout, boxTop, measured, positionMs)
      }
    }

    if (saveLayerCount >= 0) {
      canvas.restoreToCount(saveLayerCount)
    }
  }

  private fun drawMeasuredElement(
    canvas: Canvas,
    canvasWidth: Int,
    canvasHeight: Int,
    active: CaptionComposition,
    layout: CachedLayout,
    boxTop: Float,
    measured: MeasuredElement,
    positionMs: Long,
  ) {
    val el = measured.element
    val compDuration = (active.endMs - active.startMs).coerceAtLeast(1L)

    // Determine exact entry beat from word timings so lines choreograph dynamically as spoken
    val firstWordIdx = el.wordIndices.firstOrNull()
    val wordTriggeredStartMs =
      if (firstWordIdx != null && firstWordIdx in active.wordMappings.indices) {
        val rawStart = active.wordMappings[firstWordIdx].startMs
        min(rawStart, active.endMs - min(compDuration, 180L))
      } else {
        active.startMs + el.staggerDelayMs.toLong()
      }

    val entryDuration = active.entryDurationMs.coerceAtLeast(90).toFloat()
    val rawElapsedFromBeat = (positionMs - wordTriggeredStartMs).toFloat()
    if (rawElapsedFromBeat < -15f) return

    val rawEntryT = (rawElapsedFromBeat.coerceAtLeast(0f) / entryDuration).coerceIn(0f, 1f)
    val easedEntry = applyEasing(rawEntryT, active.easing)

    val remainingMs = (active.endMs - positionMs).coerceAtLeast(0L)
    val exitDuration = active.exitDurationMs.coerceAtLeast(1)
    val exitProgress =
      if (remainingMs < exitDuration) {
        1f - (remainingMs.toFloat() / exitDuration.toFloat()).coerceIn(0f, 1f)
      } else {
        0f
      }

    var alpha = 1f
    var scale = 1f
    var translateY = 0f
    val baseScale = min(canvasWidth, canvasHeight) / 360f
    val slideOffsetUnit = 16f * baseScale

    when (active.entryAnimation) {
      EntryAnimationType.SCALE_RISE -> {
        alpha = easedEntry
        scale = 0.84f + 0.16f * easedEntry
        translateY = (1f - easedEntry) * slideOffsetUnit * 0.72f
      }
      EntryAnimationType.SLIDE_UP -> {
        alpha = easedEntry
        translateY = (1f - easedEntry) * slideOffsetUnit
      }
      EntryAnimationType.FADE_POP -> {
        alpha = easedEntry
        scale = 0.76f + 0.24f * easedEntry
      }
      EntryAnimationType.STAGGER_REVEAL -> {
        alpha = easedEntry
        scale = 0.90f + 0.10f * easedEntry
        translateY = (1f - easedEntry) * slideOffsetUnit * 0.55f
      }
      EntryAnimationType.CUT -> {
        alpha = if (rawElapsedFromBeat >= 0f) 1f else 0f
      }
    }

    // Active spoken word micro-pulse & subtle alive motion breath
    if (firstWordIdx != null && firstWordIdx in active.wordMappings.indices) {
      val lastWordIdx = el.wordIndices.last().coerceIn(0, active.wordMappings.lastIndex)
      val activeWindowStart = active.wordMappings[firstWordIdx].startMs
      val activeWindowEnd = active.wordMappings[lastWordIdx].endMs
      if (positionMs in activeWindowStart..activeWindowEnd && el.hierarchyLevel == HierarchyLevel.DOMINANT) {
        val spanT =
          ((positionMs - activeWindowStart).toFloat() / max(90L, activeWindowEnd - activeWindowStart))
            .coerceIn(0f, 1f)
        val pulse = sin(spanT * Math.PI).toFloat() * 0.045f
        scale *= (1f + pulse)
      }
    }

    // Subtle organic floating breath so text never looks dead/pinned
    val breathT = ((positionMs - active.startMs).coerceAtLeast(0L) / 1000f)
    translateY += sin(breathT * 3.1f) * (1.2f * baseScale)

    if (exitProgress > 0f) {
      val easedExit = applyEasing(exitProgress, AnimationEasingType.DECELERATE)
      when (active.exitAnimation) {
        ExitAnimationType.FADE_DROP -> {
          alpha *= (1f - easedExit)
          translateY += easedExit * slideOffsetUnit * 0.65f
        }
        ExitAnimationType.SCALE_OUT -> {
          alpha *= (1f - easedExit)
          scale *= (1f - 0.14f * easedExit)
        }
        ExitAnimationType.SLIDE_DOWN -> {
          alpha *= (1f - easedExit)
          translateY += easedExit * slideOffsetUnit
        }
        ExitAnimationType.CLEAN_CUT -> {
          if (exitProgress > 0.92f) alpha = 0f
        }
      }
    }

    if (alpha <= 0.01f) return
    val effectiveOpacity = el.opacity.coerceIn(0.05f, 1f)
    val baseAlphaByte = (alpha.coerceIn(0f, 1f) * effectiveOpacity * 255f).toInt().coerceIn(0, 255)

    val elementCenterY = boxTop + measured.topYOffsetPx + measured.blockHeightPx / 2f
    val elementCenterX =
      (layout.anchorX + measured.centerOffsetXFromAnchorPx).coerceIn(
        canvasWidth * 0.06f + measured.blockWidthPx / 2f,
        max(canvasWidth * 0.06f + measured.blockWidthPx / 2f, canvasWidth * 0.94f - measured.blockWidthPx / 2f),
      )

    canvas.save()
    canvas.translate(elementCenterX, elementCenterY + translateY)
    if (el.rotationDeg != 0f) {
      canvas.rotate(el.rotationDeg)
    }
    canvas.scale(scale, scale)

    // Optional Highlight Pill / Background Box behind text block
    val bgPaint = measured.backgroundPaint
    if (bgPaint != null) {
      val padX = 11f * baseScale
      val padY = 6f * baseScale
      val cornerR = el.backgroundCornerRadiusDp.coerceIn(0f, 32f) * baseScale
      bgPaint.alpha = (baseAlphaByte * el.backgroundAlpha.coerceIn(0.1f, 1f)).toInt().coerceIn(0, 255)
      val bgRect =
        RectF(
          -measured.blockWidthPx / 2f - padX,
          -measured.blockHeightPx / 2f - padY,
          measured.blockWidthPx / 2f + padX,
          measured.blockHeightPx / 2f + padY,
        )
      canvas.drawRoundRect(bgRect, cornerR, cornerR, bgPaint)
    }

    val canDoWordReveal =
      el.hierarchyLevel != HierarchyLevel.DOMINANT &&
        el.wordIndices.size > 1 &&
        active.wordMappings.isNotEmpty()

    var localY = -measured.blockHeightPx / 2f
    for (line in measured.lines) {
      val baselineY = localY - line.ascentPx
      val lineStartX =
        when (el.alignment) {
          TextAlignment.LEFT -> -measured.blockWidthPx / 2f
          TextAlignment.RIGHT -> measured.blockWidthPx / 2f - line.widthPx
          TextAlignment.CENTER -> -line.widthPx / 2f
        }

      if (canDoWordReveal && line.words.size > 1) {
        for (wSpan in line.words) {
          val wt = wSpan.wordIndexInComp?.let { active.wordMappings.getOrNull(it) }
          val wAlphaRatio =
            if (wt != null) {
              ((positionMs - (wt.startMs - 30L)).coerceAtLeast(0L) / 110f).coerceIn(0f, 1f)
            } else {
              1f
            }
          if (wAlphaRatio <= 0.02f) continue
          val wordAlpha = (baseAlphaByte * wAlphaRatio).toInt()
          val wordRise = (1f - wAlphaRatio) * 5f * baseScale
          drawTextToken(
            canvas = canvas,
            text = wSpan.text,
            x = lineStartX + wSpan.startXInLine,
            y = baselineY + wordRise,
            paint = measured.paint,
            glowPaint = measured.glowPaint,
            strokePaint = measured.strokePaint,
            glowIntensity = el.glowIntensity,
            alphaByte = wordAlpha,
          )
        }
      } else {
        drawTextToken(
          canvas = canvas,
          text = line.text,
          x = lineStartX,
          y = baselineY,
          paint = measured.paint,
          glowPaint = measured.glowPaint,
          strokePaint = measured.strokePaint,
          glowIntensity = el.glowIntensity,
          alphaByte = baseAlphaByte,
        )
      }
      localY += line.heightPx * measured.lineSpacingFactor
    }
    canvas.restore()
  }

  private fun drawTextToken(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    paint: TextPaint,
    glowPaint: TextPaint?,
    strokePaint: TextPaint?,
    glowIntensity: Float,
    alphaByte: Int,
  ) {
    if (glowPaint != null) {
      val glowAlphaFactor = (0.55f * glowIntensity.coerceIn(0.25f, 1.8f)).coerceIn(0.15f, 0.95f)
      glowPaint.alpha = (alphaByte * glowAlphaFactor).toInt().coerceIn(0, 255)
      canvas.drawText(text, x, y, glowPaint)
    }
    if (strokePaint != null) {
      strokePaint.alpha = alphaByte
      canvas.drawText(text, x, y, strokePaint)
    }
    paint.alpha = alphaByte
    canvas.drawText(text, x, y, paint)
  }

  private fun getOrComputeLayout(
    comp: CaptionComposition,
    canvasWidth: Int,
    canvasHeight: Int,
    shadowMultiplier: Float,
  ): CachedLayout {
    val sig =
      buildString {
        append(comp.id).append('|')
        append(canvasWidth).append('x').append(canvasHeight).append('|')
        append(comp.anchorXFraction).append(',').append(comp.anchorYFraction).append('|')
        append(shadowMultiplier).append('|')
        comp.elements.forEach { el ->
          append(el.text).append(':').append(el.fontFamilyId).append(':').append(el.fontWeight)
          append(':').append(el.isItalic).append(':').append(el.isUnderlined).append(':').append(el.fontSizeSp)
          append(':').append(el.colorHex).append(':').append(el.alignment)
          append(':').append(el.uppercase).append(':').append(el.letterSpacingEm)
          append(':').append(el.lineHeightMultiplier).append(':').append(el.opacity)
          append(':').append(el.inlineRowGroup).append(':').append(el.rotationDeg)
          append(':').append(el.offsetXEm).append(':').append(el.offsetYEm)
          append(':').append(el.glowColorHex).append(':').append(el.glowIntensity)
          append(':').append(el.strokeWidthFactor).append(':').append(el.strokeColorHex)
          append(':').append(el.shadowEnabled).append(':').append(el.shadowIntensity).append(':').append(el.shadowColorHex)
          append(':').append(el.backgroundColorHex).append(':').append(el.backgroundAlpha).append(':').append(el.backgroundCornerRadiusDp)
          append(':').append(el.depthLayer).append(';')
        }
      }
    layoutCache[sig]?.let { return it }

    if (layoutCache.size > 48) {
      layoutCache.clear()
    }

    val baseScale = min(canvasWidth, canvasHeight) / 360f
    val maxLineWidthPx = canvasWidth * 0.86f
    val measuredRaw = mutableListOf<MeasuredElement>()

    data class PaintBundle(
      val fill: TextPaint,
      val glow: TextPaint?,
      val stroke: TextPaint?,
      val background: Paint?,
      val lineSpacingFactor: Float,
    )

    // Measure individual elements first
    val elementSizes =
      comp.elements.map { el ->
        val textStr = if (el.uppercase) el.text.uppercase() else el.text
        val typeface = FontRegistry.getTypeface(context, el.fontFamilyId, el.fontWeight, el.isItalic)
        var textSizePx = el.fontSizeSp * baseScale
        val lineSpacingFactor = el.lineHeightMultiplier.coerceIn(0.8f, 1.8f)

        val paint =
          TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            this.color = parseSafeColor(el.colorHex)
            this.isUnderlineText = el.isUnderlined
            if (el.isItalic && el.fontFamilyId !in setOf("caveat", "pacifico")) {
              textSkewX = -0.18f
            }
            if (el.letterSpacingEm != 0f) letterSpacing = el.letterSpacingEm
          }

        if (!textStr.contains(" ") && paint.measureText(textStr) > maxLineWidthPx) {
          val ratio = (maxLineWidthPx / paint.measureText(textStr)).coerceIn(0.42f, 1f)
          textSizePx *= ratio
          paint.textSize = textSizePx
        }

        if (el.shadowEnabled && el.shadowIntensity > 0.02f) {
          val shadowRadius =
            (if (el.hierarchyLevel == HierarchyLevel.DOMINANT) 10f else 5.5f) *
              baseScale *
              shadowMultiplier *
              el.shadowIntensity.coerceIn(0.1f, 2.2f)
          val rawShadowColor = parseSafeColor(el.shadowColorHex)
          val shadowAlpha = (185f * el.shadowIntensity.coerceIn(0.1f, 1.35f)).toInt().coerceIn(24, 240)
          val resolvedShadowColor =
            Color.argb(
              shadowAlpha,
              Color.red(rawShadowColor),
              Color.green(rawShadowColor),
              Color.blue(rawShadowColor),
            )
          paint.setShadowLayer(shadowRadius, 0f, 1.8f * baseScale, resolvedShadowColor)
        } else {
          paint.clearShadowLayer()
        }

        val glowPaint =
          el.glowColorHex?.takeIf { it.isNotBlank() }?.let { glowHex ->
            TextPaint(paint).apply {
              color = parseSafeColor(glowHex)
              clearShadowLayer()
              val blurRadius = (10f * baseScale * el.glowIntensity.coerceIn(0.3f, 2.2f)).coerceAtLeast(1f)
              maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.OUTER)
            }
          }

        val strokePaint =
          if (el.strokeWidthFactor > 0.05f) {
            TextPaint(paint).apply {
              color = parseSafeColor(el.strokeColorHex)
              style = Paint.Style.STROKE
              strokeWidth = el.strokeWidthFactor * 2f * baseScale
              strokeJoin = Paint.Join.ROUND
              strokeCap = Paint.Cap.ROUND
              clearShadowLayer()
            }
          } else {
            null
          }

        val bgPaint =
          el.backgroundColorHex?.takeIf { it.isNotBlank() }?.let { bgHex ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
              style = Paint.Style.FILL
              color = parseSafeColor(bgHex)
            }
          }

        val wrappedLines = wrapLines(textStr, paint, maxLineWidthPx)
        val fm = paint.fontMetrics
        var globalWordOffset = 0
        val measuredLines =
          wrappedLines.map { line ->
            val tokens = line.split(" ").filter { it.isNotEmpty() }
            val wordSpans = mutableListOf<MeasuredWordSpan>()
            var xAcc = 0f
            val spaceW = paint.measureText(" ")
            tokens.forEachIndexed { tIdx, tok ->
              val mappedIdx = el.wordIndices.getOrNull(globalWordOffset)
              wordSpans.add(MeasuredWordSpan(tok, xAcc, mappedIdx))
              xAcc += paint.measureText(tok) + if (tIdx < tokens.lastIndex) spaceW else 0f
              globalWordOffset++
            }
            MeasuredLine(
              text = line,
              widthPx = paint.measureText(line),
              ascentPx = fm.ascent,
              descentPx = fm.descent,
              words = wordSpans,
            )
          }
        val blockW = measuredLines.maxOfOrNull { it.widthPx } ?: 0f
        val blockH =
          measuredLines.sumOf { (it.heightPx * lineSpacingFactor).toDouble() }.toFloat().coerceAtLeast(1f)
        Triple(
          el,
          PaintBundle(paint, glowPaint, strokePaint, bgPaint, lineSpacingFactor),
          Triple(measuredLines, blockW, blockH),
        )
      }

    // Group elements by inlineRowGroup so inline tokens ("and" + "keeps", "click" + "Dynamic Captions")
    // lay out horizontally side-by-side while stacked rows stack cleanly vertically!
    val groupedRows = elementSizes.groupBy { it.first.inlineRowGroup }.toSortedMap()
    var yCursor = 0f
    var maxTotalRowW = 0f
    val rowGapDefault = 3f * baseScale
    val inlineGapPx = 7f * baseScale

    groupedRows.values.forEachIndexed { rowIdx, rowItems ->
      if (rowIdx > 0) yCursor += rowGapDefault

      val rowHeight = rowItems.maxOfOrNull { it.third.third } ?: 1f
      val rowTotalWidth =
        rowItems.sumOf { it.third.second.toDouble() }.toFloat() +
          max(0, rowItems.size - 1) * inlineGapPx
      maxTotalRowW = max(maxTotalRowW, rowTotalWidth)

      var rowXCursor = -rowTotalWidth / 2f
      rowItems.forEach { (el, paints, metrics) ->
        val (lines, blockW, blockH) = metrics
        val baselineAlignedY =
          yCursor + (rowHeight - blockH) * 0.72f + (el.offsetYEm * paints.fill.textSize)
        val centerOffsetX =
          if (rowItems.size > 1) {
            val itemCenterX = rowXCursor + blockW / 2f
            rowXCursor += blockW + inlineGapPx
            itemCenterX + (el.offsetXEm * paints.fill.textSize)
          } else {
            val alignOffset =
              when (el.alignment) {
                TextAlignment.LEFT -> -(maxLineWidthPx * 0.25f) + blockW / 2f
                TextAlignment.RIGHT -> (maxLineWidthPx * 0.25f) - blockW / 2f
                TextAlignment.CENTER -> 0f
              }
            alignOffset + (el.offsetXEm * paints.fill.textSize)
          }

        measuredRaw.add(
          MeasuredElement(
            element = el,
            paint = paints.fill,
            glowPaint = paints.glow,
            strokePaint = paints.stroke,
            backgroundPaint = paints.background,
            lines = lines,
            blockWidthPx = blockW,
            blockHeightPx = blockH,
            centerOffsetXFromAnchorPx = centerOffsetX,
            topYOffsetPx = max(0f, baselineAlignedY),
            lineSpacingFactor = paints.lineSpacingFactor,
          )
        )
      }
      yCursor += rowHeight
    }

    // Refine left/right stack X alignment cleanly when multi-row LEFT/RIGHT stacked block
    val allLeft = measuredRaw.all { it.element.alignment == TextAlignment.LEFT }
    val allRight = measuredRaw.all { it.element.alignment == TextAlignment.RIGHT }
    val finalMeasured =
      if (allLeft || allRight) {
        measuredRaw.map { m ->
          val offset =
            if (allLeft) (-maxTotalRowW / 2f + m.blockWidthPx / 2f)
            else (maxTotalRowW / 2f - m.blockWidthPx / 2f)
          m.copy(centerOffsetXFromAnchorPx = offset)
        }
      } else {
        measuredRaw
      }

    val computed =
      CachedLayout(
        key = sig,
        measuredElements = finalMeasured,
        totalHeightPx = max(yCursor, finalMeasured.maxOfOrNull { it.topYOffsetPx + it.blockHeightPx } ?: yCursor),
        maxWidthPx = maxTotalRowW,
        anchorX = comp.anchorXFraction * canvasWidth,
        anchorY = comp.anchorYFraction * canvasHeight,
      )
    layoutCache[sig] = computed
    return computed
  }

  private fun wrapLines(text: String, paint: Paint, maxWidthPx: Float): List<String> {
    if (paint.measureText(text) <= maxWidthPx) return listOf(text)
    val words = text.split(" ").filter { it.isNotEmpty() }
    if (words.size <= 1) return listOf(text)

    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
      val candidate = if (current.isEmpty()) word else "$current $word"
      if (paint.measureText(candidate) <= maxWidthPx || current.isEmpty()) {
        current = StringBuilder(candidate)
      } else {
        lines.add(current.toString())
        current = StringBuilder(word)
      }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
  }

  private fun applyEasing(t: Float, easing: AnimationEasingType): Float {
    val clamped = t.coerceIn(0f, 1f)
    return when (easing) {
      AnimationEasingType.LINEAR -> clamped
      AnimationEasingType.DECELERATE -> 1f - (1f - clamped).pow(3)
      AnimationEasingType.SMOOTH_IN_OUT ->
        if (clamped < 0.5f) 4f * clamped * clamped * clamped
        else 1f - (-2f * clamped + 2f).pow(3) / 2f
      AnimationEasingType.SPRING_OVERSHOOT -> {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        1f + c3 * (clamped - 1f).pow(3) + c1 * (clamped - 1f).pow(2)
      }
    }.coerceIn(0f, 1.08f)
  }

  private fun parseSafeColor(hex: String): Int {
    return try {
      Color.parseColor(hex)
    } catch (_: Exception) {
      Color.WHITE
    }
  }
}

package com.example.captionstudio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.DesignerFrameStyle
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.FaceAnchorSample
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.KaraokeHighlightMode
import com.example.captionstudio.domain.model.LayerMotionEffect
import com.example.captionstudio.domain.model.LetterAnimationStyle
import com.example.captionstudio.domain.model.SpatialLayoutMode
import com.example.captionstudio.domain.model.TextAlignment
import com.example.captionstudio.domain.model.TextSurfaceStyle
import com.example.captionstudio.domain.model.VectorFlourish
import com.example.captionstudio.media.AudioExtractionService
import com.example.captionstudio.typography.FontRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
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
    val widthPx: Float,
    val wordIndexInComp: Int?,
    val globalWordOrder: Int,
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

  private data class SurfacePaintBundle(
    val halationOuterPaint: TextPaint? = null,
    val halationInnerPaint: TextPaint? = null,
    val halationOuterFilters: List<BlurMaskFilter>? = null,
    val halationInnerFilters: List<BlurMaskFilter>? = null,
    val inflatedDropPaint: TextPaint? = null,
    val inflatedBevelPaint: TextPaint? = null,
    val inflatedSpecularPaint: TextPaint? = null,
    val frostedFillPaint: Paint? = null,
    val frostedRimPaint: Paint? = null,
    val frameFillPaint: Paint? = null,
    val frameStrokePaint: Paint? = null,
    val frameAccentPaint: Paint? = null,
    val frameHandleFillPaint: Paint? = null,
    val frameHandleBorderPaint: Paint? = null,
    val flourishPaint: Paint? = null,
  ) {
    fun updateTextSize(newSizePx: Float) {
      halationOuterPaint?.textSize = newSizePx
      halationInnerPaint?.textSize = newSizePx
      inflatedDropPaint?.textSize = newSizePx
      inflatedBevelPaint?.textSize = newSizePx
      inflatedSpecularPaint?.textSize = newSizePx
    }

    fun applyHalationVoiceEnergy(vocalEnergy01: Float) {
      val clamped = vocalEnergy01.coerceIn(0f, 1f)
      halationOuterFilters?.let { filters ->
        if (filters.isNotEmpty()) {
          val idx = (clamped * (filters.size - 1)).toInt().coerceIn(0, filters.lastIndex)
          halationOuterPaint?.maskFilter = filters[idx]
        }
      }
      halationInnerFilters?.let { filters ->
        if (filters.isNotEmpty()) {
          val idx = (clamped * (filters.size - 1)).toInt().coerceIn(0, filters.lastIndex)
          halationInnerPaint?.maskFilter = filters[idx]
        }
      }
    }
  }

  private data class MeasuredElement(
    val element: CompositionElement,
    val paint: TextPaint,
    val glowPaint: TextPaint?,
    val strokePaint: TextPaint?,
    val backgroundPaint: Paint?,
    val karaokeHighlightPaint: TextPaint?,
    val karaokePillPaint: Paint?,
    val surfacePaints: SurfacePaintBundle,
    val totalWordsInElement: Int,
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

  private data class FastCacheEntry(
    val comp: CaptionComposition,
    val width: Int,
    val height: Int,
    val shadowMultiplier: Float,
    val layout: CachedLayout,
  )

  private val layoutCache = ConcurrentHashMap<String, CachedLayout>()
  @Volatile private var lastFastEntry: FastCacheEntry? = null
  private val tempGuideRect = RectF()
  private val tempSrcRect = Rect()
  private val tempDstRect = RectF()
  private val tempBgRect = RectF()
  private val tempKaraokeRect = RectF()
  private val tempArcPath = Path()
  private val tempFlourishFullPath = Path()
  private val tempFlourishSegmentPath = Path()
  private val tempPathMeasure = PathMeasure()
  private val tempPos = FloatArray(2)
  private val tempTan = FloatArray(2)
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
    lastFastEntry = null
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
    faceAnchorSamples: List<FaceAnchorSample> = emptyList(),
    audioEnergyEnvelope10ms: FloatArray? = null,
    sourceTimeMs: Long = positionMs,
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
      faceAnchorSamples = faceAnchorSamples,
      audioEnergyEnvelope10ms = audioEnergyEnvelope10ms,
      sourceTimeMs = sourceTimeMs,
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
    videoContentRect: RectF? = null,
    faceAnchorSamples: List<FaceAnchorSample> = emptyList(),
    audioEnergyEnvelope10ms: FloatArray? = null,
    sourceTimeMs: Long = positionMs,
  ) {
    if (canvasWidth <= 0 || canvasHeight <= 0) return
    val active = compositions.firstOrNull { positionMs in it.startMs..it.endMs } ?: return

    val layout = getOrComputeLayout(active, canvasWidth, canvasHeight, shadowMultiplier)
    val boxTop =
      (layout.anchorY - layout.totalHeightPx / 2f).coerceIn(
        canvasHeight * 0.05f,
        max(canvasHeight * 0.05f, canvasHeight * 0.95f - layout.totalHeightPx),
      )

    val parallax =
      if (
        faceAnchorSamples.isNotEmpty() &&
        (active.spatialMode == SpatialLayoutMode.CURVED_ARC_PATH ||
          active.spatialMode == SpatialLayoutMode.LEFT_RIGHT_SPLIT)
      ) {
        computeHeadParallaxTransform(faceAnchorSamples, sourceTimeMs)
      } else {
        FaceParallaxTransform.ZERO
      }
    val vocalEnergy01 =
      if (audioEnergyEnvelope10ms != null && audioEnergyEnvelope10ms.isNotEmpty()) {
        AudioExtractionService.getNormalizedEnergyAt(audioEnergyEnvelope10ms, sourceTimeMs)
      } else {
        0f
      }

    if (drawSelectionGuide && active.id == selectedCompositionId) {
      boxPaint.color = Color.argb(140, 255, 184, 28)
      val halfW = layout.maxWidthPx / 2f + 16f
      tempGuideRect.set(
        (layout.anchorX - halfW).coerceAtLeast(8f),
        (boxTop - 12f).coerceAtLeast(8f),
        (layout.anchorX + halfW).coerceAtMost(canvasWidth - 8f),
        (boxTop + layout.totalHeightPx + 12f).coerceAtMost(canvasHeight - 8f),
      )
      canvas.drawRoundRect(tempGuideRect, 12f, 12f, boxPaint)
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
        drawMeasuredElement(
          canvas = canvas,
          canvasWidth = canvasWidth,
          canvasHeight = canvasHeight,
          active = active,
          layout = layout,
          boxTop = boxTop,
          measured = measured,
          positionMs = positionMs,
          parallax = parallax,
          vocalEnergy01 = vocalEnergy01,
        )
      }
    }

    // Pass 2: Occlude behind-subject text cleanly behind speaker hair/head/shoulders
    if (useMask && subjectMaskBitmap != null) {
      tempSrcRect.set(0, 0, subjectMaskBitmap.width, subjectMaskBitmap.height)
      if (videoContentRect != null && !videoContentRect.isEmpty) {
        tempDstRect.set(videoContentRect)
      } else {
        tempDstRect.set(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())
      }
      canvas.drawBitmap(subjectMaskBitmap, tempSrcRect, tempDstRect, maskErasePaint)
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
        drawMeasuredElement(
          canvas = canvas,
          canvasWidth = canvasWidth,
          canvasHeight = canvasHeight,
          active = active,
          layout = layout,
          boxTop = boxTop,
          measured = measured,
          positionMs = positionMs,
          parallax = parallax,
          vocalEnergy01 = vocalEnergy01,
        )
      }
    }

    if (saveLayerCount >= 0) {
      canvas.restoreToCount(saveLayerCount)
    }
  }

  /**
   * Draws a tasteful, unobtrusive studio corner watermark on free exports (removable via Export toggle).
   */
  fun drawWatermark(canvas: Canvas, canvasWidth: Int, canvasHeight: Int) {
    if (canvasWidth <= 0 || canvasHeight <= 0) return
    val base = min(canvasWidth, canvasHeight).toFloat()
    val textSizePx = (base * 0.025f).coerceIn(18f, 34f)
    val padH = textSizePx * 0.72f
    val padV = textSizePx * 0.42f
    val marginX = base * 0.042f
    val marginY = canvasHeight * 0.036f
    val dotRadius = textSizePx * 0.20f

    val label = "Caption Studio"
    val wmTextPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 248, 248, 252)
        textSize = textSizePx
        letterSpacing = 0.02f
        typeface = FontRegistry.getTypeface(context, "plus_jakarta_sans", 600, false)
      }
    val textW = wmTextPaint.measureText(label)
    val fm = wmTextPaint.fontMetrics
    val textH = fm.descent - fm.ascent

    val pillW = padH * 2f + dotRadius * 2f + textSizePx * 0.42f + textW
    val pillH = textH + padV * 2f
    val right = canvasWidth - marginX
    val bottom = canvasHeight - marginY
    val left = right - pillW
    val top = bottom - pillH

    val pillPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(138, 12, 12, 16)
        style = Paint.Style.FILL
      }
    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(46, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = max(1.2f, base * 0.002f)
      }
    val dotPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 126, 112) // StudioCoral accent dot
        style = Paint.Style.FILL
      }

    tempBgRect.set(left, top, right, bottom)
    val radius = pillH / 2f
    canvas.drawRoundRect(tempBgRect, radius, radius, pillPaint)
    canvas.drawRoundRect(tempBgRect, radius, radius, borderPaint)

    val dotCx = left + padH + dotRadius
    val centerY = top + pillH / 2f
    canvas.drawCircle(dotCx, centerY, dotRadius, dotPaint)

    val textX = dotCx + dotRadius + textSizePx * 0.42f
    val baselineY = centerY - (fm.ascent + fm.descent) / 2f
    canvas.drawText(label, textX, baselineY, wmTextPaint)
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
    parallax: FaceParallaxTransform = FaceParallaxTransform.ZERO,
    vocalEnergy01: Float = 0f,
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
    var translateX = 0f
    var translateY = 0f
    var extraRotationDeg = 0f
    val baseScale = min(canvasWidth, canvasHeight) / 360f
    val slideOffsetUnit = 16f * baseScale

    val hasDedicatedMotion = el.motionEffect != LayerMotionEffect.NONE
    val mSpeed = el.motionSpeed.coerceIn(0.4f, 2.2f)
    val mIntensity = el.motionIntensity.coerceIn(0.25f, 2.0f)
    val motionElapsedSec = (rawElapsedFromBeat.coerceAtLeast(0f) / 1000f) * mSpeed

    if (!hasDedicatedMotion) {
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
    } else {
      // Dedicated per-layer motion choreography
      val layerEntryDurMs = (360f / mSpeed).coerceIn(140f, 760f)
      val tNorm = (rawElapsedFromBeat.coerceAtLeast(0f) / layerEntryDurMs).coerceIn(0f, 1f)
      val easeOutCubic = 1f - (1f - tNorm).pow(3)
      when (el.motionEffect) {
        LayerMotionEffect.NONE -> Unit
        LayerMotionEffect.FADE_IN -> {
          alpha = easeOutCubic
        }
        LayerMotionEffect.SLIDE_UP -> {
          alpha = (tNorm * 1.4f).coerceIn(0f, 1f)
          translateY = (1f - easeOutCubic) * 28f * baseScale * mIntensity
        }
        LayerMotionEffect.SLIDE_DOWN -> {
          alpha = (tNorm * 1.4f).coerceIn(0f, 1f)
          translateY = -(1f - easeOutCubic) * 28f * baseScale * mIntensity
        }
        LayerMotionEffect.SLIDE_LEFT -> {
          alpha = (tNorm * 1.4f).coerceIn(0f, 1f)
          translateX = (1f - easeOutCubic) * 38f * baseScale * mIntensity
        }
        LayerMotionEffect.POP_IN -> {
          val overshoot = applyEasing(tNorm, AnimationEasingType.SPRING_OVERSHOOT)
          alpha = (tNorm * 2.2f).coerceIn(0f, 1f)
          scale = (1f - 0.58f * mIntensity.coerceAtMost(1.4f)) + (0.58f * mIntensity.coerceAtMost(1.4f)) * overshoot
        }
        LayerMotionEffect.ZOOM_IN -> {
          alpha = easeOutCubic
          val startScale = (1f - 0.55f * mIntensity.coerceAtMost(1.4f)).coerceIn(0.25f, 0.9f)
          scale = startScale + (1f - startScale) * easeOutCubic
        }
        LayerMotionEffect.BOUNCE -> {
          val bounceT = (rawElapsedFromBeat.coerceAtLeast(0f) / (560f / mSpeed)).coerceIn(0f, 1f)
          val damped = abs(cos(bounceT * PI * 2.5)).toFloat() * exp(-bounceT * 3.2f)
          alpha = (bounceT * 3.0f).coerceIn(0f, 1f)
          translateY = -damped * 26f * baseScale * mIntensity
          scale = 1f + damped * 0.12f * mIntensity
        }
        LayerMotionEffect.WIGGLE -> {
          alpha = (tNorm * 2.5f).coerceIn(0f, 1f)
          val rad = motionElapsedSec * 9.0f
          extraRotationDeg = sin(rad) * 5.8f * mIntensity
          translateX = sin(rad * 0.5f) * 2.4f * baseScale * mIntensity
        }
        LayerMotionEffect.PULSE -> {
          alpha = (tNorm * 2.5f).coerceIn(0f, 1f)
          scale = 1f + sin(motionElapsedSec * 5.2f) * 0.085f * mIntensity
        }
        LayerMotionEffect.FLOAT -> {
          alpha = easeOutCubic
          translateY = sin(motionElapsedSec * 3.2f) * 8.5f * baseScale * mIntensity
          translateX = cos(motionElapsedSec * 1.9f) * 2.2f * baseScale * mIntensity
        }
        LayerMotionEffect.WAVE -> {
          alpha = (tNorm * 2.2f).coerceIn(0f, 1f)
        }
        LayerMotionEffect.SWING -> {
          alpha = easeOutCubic
          extraRotationDeg = sin(motionElapsedSec * 3.8f) * 8.5f * mIntensity
        }
        LayerMotionEffect.SHAKE -> {
          alpha = (tNorm * 3.0f).coerceIn(0f, 1f)
          translateX = sin(motionElapsedSec * 34.0f) * 3.2f * baseScale * mIntensity
          translateY = cos(motionElapsedSec * 41.0f) * 2.1f * baseScale * mIntensity
          extraRotationDeg = sin(motionElapsedSec * 29.0f) * 1.6f * mIntensity
        }
        LayerMotionEffect.TYPEWRITER -> {
          alpha = 1f
        }
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

    // Subtle organic floating breath only when no explicit loop/reveal motion overrides it
    if (!hasDedicatedMotion) {
      val breathT = ((positionMs - active.startMs).coerceAtLeast(0L) / 1000f)
      translateY += sin(breathT * 3.1f) * (1.2f * baseScale)
    }

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

    // Hero/dominant words breathe subtly (+0%..+5.5%) with 10ms vocal RMS energy when not driven by per-word karaoke
    val isHeroBreathTarget =
      vocalEnergy01 > 0.001f &&
        el.karaokeMode == KaraokeHighlightMode.OFF &&
        (el.hierarchyLevel == HierarchyLevel.DOMINANT || layout.measuredElements.size == 1)
    if (isHeroBreathTarget) {
      scale *= computeVoiceBreathingScale(vocalEnergy01)
    }

    // Subtle damped head-parallax micro-tracking for scene-anchored spatial blocks
    val parallaxDx = parallax.offsetXFraction * canvasWidth
    val parallaxDy = parallax.offsetYFraction * canvasHeight
    val minCenterY = canvasHeight * 0.05f + measured.blockHeightPx / 2f
    val maxCenterY = max(minCenterY, canvasHeight * 0.95f - measured.blockHeightPx / 2f)
    val elementCenterY =
      (boxTop + measured.topYOffsetPx + measured.blockHeightPx / 2f + parallaxDy).coerceIn(minCenterY, maxCenterY)
    val elementCenterX =
      (layout.anchorX + measured.centerOffsetXFromAnchorPx + parallaxDx).coerceIn(
        canvasWidth * 0.06f + measured.blockWidthPx / 2f,
        max(canvasWidth * 0.06f + measured.blockWidthPx / 2f, canvasWidth * 0.94f - measured.blockWidthPx / 2f),
      )

    canvas.save()
    canvas.translate(elementCenterX + translateX, elementCenterY + translateY)
    val totalRot = el.rotationDeg + el.tiltDegrees + extraRotationDeg + parallax.tiltDeg
    if (totalRot != 0f) {
      canvas.rotate(totalRot)
    }
    if (scale != 1f) {
      canvas.scale(scale, scale)
    }

    // Surface & Designer Frame Backdrops
    val bgPaint = measured.backgroundPaint
    if (el.designerFrame != DesignerFrameStyle.NONE) {
      drawDesignerFrameBackdrop(
        canvas = canvas,
        measured = measured,
        baseScale = baseScale,
        baseAlphaByte = baseAlphaByte,
      )
    } else if (el.surfaceStyle == TextSurfaceStyle.FROSTED_GLASS) {
      val fFill = measured.surfacePaints.frostedFillPaint
      val fRim = measured.surfacePaints.frostedRimPaint
      if (fFill != null) {
        val padX = 13f * baseScale
        val padY = 7.5f * baseScale
        val cornerR = max(el.backgroundCornerRadiusDp.coerceIn(0f, 32f), 12f) * baseScale
        tempBgRect.set(
          -measured.blockWidthPx / 2f - padX,
          -measured.blockHeightPx / 2f - padY,
          measured.blockWidthPx / 2f + padX,
          measured.blockHeightPx / 2f + padY,
        )
        fFill.alpha = (158 * baseAlphaByte / 255).coerceIn(0, 255)
        canvas.drawRoundRect(tempBgRect, cornerR, cornerR, fFill)
        if (fRim != null) {
          fRim.alpha = (156 * baseAlphaByte / 255).coerceIn(0, 255)
          canvas.drawRoundRect(tempBgRect, cornerR, cornerR, fRim)
        }
      }
    } else if (bgPaint != null) {
      val padX = 11f * baseScale
      val padY = 6f * baseScale
      val cornerR = el.backgroundCornerRadiusDp.coerceIn(0f, 32f) * baseScale
      bgPaint.alpha = (baseAlphaByte * el.backgroundAlpha.coerceIn(0.1f, 1f)).toInt().coerceIn(0, 255)
      tempBgRect.set(
        -measured.blockWidthPx / 2f - padX,
        -measured.blockHeightPx / 2f - padY,
        measured.blockWidthPx / 2f + padX,
        measured.blockHeightPx / 2f + padY,
      )
      canvas.drawRoundRect(tempBgRect, cornerR, cornerR, bgPaint)
    }

    val useCurvedArc =
      active.spatialMode == SpatialLayoutMode.CURVED_ARC_PATH &&
        el.surfaceStyle != TextSurfaceStyle.INFLATED_3D &&
        el.designerFrame == DesignerFrameStyle.NONE

    // Directional Motion-Blur Ghosting during the first 70ms of fast SLIDE_UP / SLIDE_LEFT entries
    val isSlideUpEntry =
      el.motionEffect == LayerMotionEffect.SLIDE_UP ||
        (!hasDedicatedMotion && active.entryAnimation == EntryAnimationType.SLIDE_UP)
    val isSlideLeftEntry = el.motionEffect == LayerMotionEffect.SLIDE_LEFT
    if (!useCurvedArc &&
      (isSlideUpEntry || isSlideLeftEntry) &&
      rawElapsedFromBeat >= 0f &&
      rawElapsedFromBeat <= MOTION_BLUR_WINDOW_MS.toFloat() &&
      exitProgress <= 0.001f
    ) {
      val blurDecay = (1f - (rawElapsedFromBeat / MOTION_BLUR_WINDOW_MS.toFloat())).coerceIn(0f, 1f)
      val trailStrength = 0.40f + 0.60f * blurDecay
      // Offset backward along the entry velocity vector:
      // SLIDE_UP travels upward (-Y) -> trail trails downward (+Y)
      // SLIDE_LEFT travels leftward (-X) -> trail trails rightward (+X)
      val stepDx = if (isSlideLeftEntry) 7.0f * baseScale * mIntensity * trailStrength else 0f
      val stepDy = if (isSlideUpEntry) 5.5f * baseScale * mIntensity * trailStrength else 0f
      for (ghostIdx in MOTION_BLUR_GHOST_ALPHAS.lastIndex downTo 0) {
        val ghostAlpha =
          (baseAlphaByte * MOTION_BLUR_GHOST_ALPHAS[ghostIdx]).toInt().coerceIn(0, 255)
        if (ghostAlpha <= 1) continue
        val offsetMult = (ghostIdx + 1).toFloat()
        canvas.save()
        canvas.translate(stepDx * offsetMult, stepDy * offsetMult)
        drawElementLinesPass(
          canvas = canvas,
          active = active,
          measured = measured,
          positionMs = positionMs,
          wordTriggeredStartMs = wordTriggeredStartMs,
          rawElapsedFromBeat = rawElapsedFromBeat,
          compDuration = compDuration,
          baseScale = baseScale,
          motionElapsedSec = motionElapsedSec,
          mSpeed = mSpeed,
          mIntensity = mIntensity,
          passAlphaByte = ghostAlpha,
          isGhostPass = true,
          vocalEnergy01 = vocalEnergy01,
        )
        canvas.restore()
      }
    }

    // Crisp primary typography pass (either curved quadratic Bezier arc or standard rectilinear)
    if (useCurvedArc) {
      drawCurvedArcLinesPass(
        canvas = canvas,
        canvasWidth = canvasWidth,
        active = active,
        measured = measured,
        positionMs = positionMs,
        wordTriggeredStartMs = wordTriggeredStartMs,
        rawElapsedFromBeat = rawElapsedFromBeat,
        baseScale = baseScale,
        passAlphaByte = baseAlphaByte,
        vocalEnergy01 = vocalEnergy01,
      )
    } else {
      drawElementLinesPass(
        canvas = canvas,
        active = active,
        measured = measured,
        positionMs = positionMs,
        wordTriggeredStartMs = wordTriggeredStartMs,
        rawElapsedFromBeat = rawElapsedFromBeat,
        compDuration = compDuration,
        baseScale = baseScale,
        motionElapsedSec = motionElapsedSec,
        mSpeed = mSpeed,
        mIntensity = mIntensity,
        passAlphaByte = baseAlphaByte,
        isGhostPass = false,
        vocalEnergy01 = vocalEnergy01,
      )
    }

    // Self-drawing procedural PathMeasure ink flourish (0f..1f over first 180ms)
    if (el.flourish != VectorFlourish.NONE && rawElapsedFromBeat >= 0f) {
      drawVectorFlourish(
        canvas = canvas,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        elementCenterX = elementCenterX + translateX,
        elementCenterY = elementCenterY + translateY,
        measured = measured,
        elapsedMs = rawElapsedFromBeat,
        baseScale = baseScale,
        baseAlphaByte = baseAlphaByte,
      )
    }

    canvas.restore()
  }

  private fun drawDesignerFrameBackdrop(
    canvas: Canvas,
    measured: MeasuredElement,
    baseScale: Float,
    baseAlphaByte: Int,
  ) {
    val el = measured.element
    val sp = measured.surfacePaints
    val hw = measured.blockWidthPx / 2f
    val hh = measured.blockHeightPx / 2f

    when (el.designerFrame) {
      DesignerFrameStyle.NONE -> Unit
      DesignerFrameStyle.DASHED_SELECTION_BOX -> {
        val padX = 11f * baseScale
        val padY = 6.5f * baseScale
        tempBgRect.set(-hw - padX, -hh - padY, hw + padX, hh + padY)
        sp.frameStrokePaint?.let { stroke ->
          stroke.alpha = (baseAlphaByte * 0.92f).toInt().coerceIn(0, 255)
          canvas.drawRect(tempBgRect, stroke)
        }
        // 4 solid square corner transform handles (Figma / Procreate style)
        val handleHalf = 2.8f * baseScale
        val corners =
          arrayOf(
            tempBgRect.left to tempBgRect.top,
            tempBgRect.right to tempBgRect.top,
            tempBgRect.left to tempBgRect.bottom,
            tempBgRect.right to tempBgRect.bottom,
          )
        val fillP = sp.frameHandleFillPaint
        val borderP = sp.frameHandleBorderPaint
        fillP?.alpha = baseAlphaByte
        borderP?.alpha = baseAlphaByte
        for ((cx, cy) in corners) {
          tempKaraokeRect.set(cx - handleHalf, cy - handleHalf, cx + handleHalf, cy + handleHalf)
          if (fillP != null) canvas.drawRect(tempKaraokeRect, fillP)
          if (borderP != null) canvas.drawRect(tempKaraokeRect, borderP)
        }
      }
      DesignerFrameStyle.PAPER_CUTOUT_PILL -> {
        val padX = 14f * baseScale
        val padY = 7.5f * baseScale
        val cornerR = min(hh + padY, 16f * baseScale)
        tempBgRect.set(-hw - padX, -hh - padY, hw + padX, hh + padY)
        sp.frameFillPaint?.let { fill ->
          fill.alpha = (baseAlphaByte * 0.98f).toInt().coerceIn(0, 255)
          canvas.drawRoundRect(tempBgRect, cornerR, cornerR, fill)
        }
        sp.frameStrokePaint?.let { rim ->
          rim.alpha = (baseAlphaByte * 0.28f).toInt().coerceIn(0, 255)
          canvas.drawRoundRect(tempBgRect, cornerR, cornerR, rim)
        }
      }
      DesignerFrameStyle.POLARITY_CARD -> {
        val padX = 14f * baseScale
        val padY = 8f * baseScale
        val cornerR = 10f * baseScale
        tempBgRect.set(-hw - padX, -hh - padY, hw + padX, hh + padY)
        sp.frameFillPaint?.let { fill ->
          fill.alpha = (baseAlphaByte * 0.97f).toInt().coerceIn(0, 255)
          canvas.drawRoundRect(tempBgRect, cornerR, cornerR, fill)
        }
        sp.frameStrokePaint?.let { border ->
          border.alpha = (baseAlphaByte * 0.85f).toInt().coerceIn(0, 255)
          canvas.drawRoundRect(tempBgRect, cornerR, cornerR, border)
        }
        // Subtle gold left-edge editorial accent bar on midnight navy polarity cards
        sp.frameAccentPaint?.let { gold ->
          gold.alpha = baseAlphaByte
          val barW = 2.6f * baseScale
          tempKaraokeRect.set(
            tempBgRect.left + 3.5f * baseScale,
            tempBgRect.top + 4.5f * baseScale,
            tempBgRect.left + 3.5f * baseScale + barW,
            tempBgRect.bottom - 4.5f * baseScale,
          )
          canvas.drawRoundRect(tempKaraokeRect, barW / 2f, barW / 2f, gold)
        }
      }
    }
  }

  private fun drawCurvedArcLinesPass(
    canvas: Canvas,
    canvasWidth: Int,
    active: CaptionComposition,
    measured: MeasuredElement,
    positionMs: Long,
    wordTriggeredStartMs: Long,
    rawElapsedFromBeat: Float,
    baseScale: Float,
    passAlphaByte: Int,
    vocalEnergy01: Float = 0f,
  ) {
    val el = measured.element
    val elementSeed = el.id.hashCode()
    var globalCharIndex = 0
    var localY = -measured.blockHeightPx / 2f

    for (line in measured.lines) {
      val lineMidY = localY + line.heightPx * 0.5f
      val arcHalfSpan = max(line.widthPx * 0.58f, canvasWidth * 0.24f)
      val arcRisePx = (arcHalfSpan * 0.21f).coerceIn(14f * baseScale, 44f * baseScale)

      tempArcPath.reset()
      tempArcPath.moveTo(-arcHalfSpan, lineMidY + arcRisePx * 0.45f)
      tempArcPath.quadTo(0f, lineMidY - arcRisePx * 1.15f, arcHalfSpan, lineMidY + arcRisePx * 0.45f)

      tempPathMeasure.setPath(tempArcPath, false)
      val arcLen = tempPathMeasure.length.coerceAtLeast(1f)
      val hOffset = ((arcLen - line.widthPx) / 2f).coerceAtLeast(0f)
      val vOffset = -(line.ascentPx + line.descentPx) * 0.5f

      if (el.letterAnimation == LetterAnimationStyle.NONE) {
        drawTextOnArcPathDirect(
          canvas = canvas,
          text = line.text,
          path = tempArcPath,
          hOffset = hOffset,
          vOffset = vOffset,
          paint = measured.paint,
          glowPaint = measured.glowPaint,
          strokePaint = measured.strokePaint,
          surfaceStyle = el.surfaceStyle,
          surfacePaints = measured.surfacePaints,
          glowIntensity = el.glowIntensity,
          alphaByte = passAlphaByte,
          wordEntryElapsedMs = rawElapsedFromBeat,
          vocalEnergy01 = vocalEnergy01,
        )
      } else {
        // Per-glyph tangent-aligned placement along the quadratic Bezier curve
        val advances = FloatArray(line.text.length)
        measured.paint.getTextWidths(line.text, advances)
        val totalUnits = countVisibleGlyphClusters(line.text).coerceAtLeast(1)
        var distCursor = hOffset
        var i = 0
        var localVisibleIdx = 0
        while (i < line.text.length) {
          val step =
            if (i + 1 < line.text.length && Character.isHighSurrogate(line.text[i]) && Character.isLowSurrogate(line.text[i + 1])) {
              2
            } else {
              1
            }
          val advance = if (step == 2) advances[i] + advances[i + 1] else advances[i]
          if (line.text[i].isWhitespace()) {
            distCursor += advance
            i += step
            globalCharIndex++
            continue
          }

          var charAlpha = passAlphaByte
          var charScale = 1f
          var extraRot = 0f
          var extraDx = 0f
          var extraDy = 0f

          when (el.letterAnimation) {
            LetterAnimationStyle.STAGGER_POP -> {
              val pop = computeStaggerPopState(positionMs - wordTriggeredStartMs, globalCharIndex)
              if (pop.alpha <= 0.01f) {
                distCursor += advance
                i += step
                globalCharIndex++
                continue
              }
              charAlpha = (passAlphaByte * pop.alpha).toInt().coerceIn(0, 255)
              charScale = pop.scale
              extraDy = pop.offsetYDp * baseScale
            }
            LetterAnimationStyle.CHAR_REVEAL -> {
              val rev =
                computeCharRevealAlpha(
                  positionMs = positionMs,
                  wordStartMs = wordTriggeredStartMs,
                  wordEndMs = active.endMs,
                  charIndexInWord = localVisibleIdx,
                  totalCharsInWord = totalUnits,
                )
              if (rev <= 0.01f) {
                distCursor += advance
                i += step
                globalCharIndex++
                localVisibleIdx++
                continue
              }
              charAlpha = (passAlphaByte * rev).toInt().coerceIn(0, 255)
              charScale = 0.94f + 0.06f * rev
            }
            LetterAnimationStyle.HAND_BOIL_15FPS -> {
              val boil = computeHandBoilTransform(positionMs, globalCharIndex, elementSeed)
              extraRot = boil.rotationDeg
              charScale = boil.scale
              extraDx = boil.offsetXDp * baseScale
              extraDy = boil.offsetYDp * baseScale
            }
            LetterAnimationStyle.NONE -> Unit
          }

          val sampleDist = (distCursor + advance * 0.5f).coerceIn(0f, arcLen)
          tempPathMeasure.getPosTan(sampleDist, tempPos, tempTan)
          val tangentDeg = (atan2(tempTan[1].toDouble(), tempTan[0].toDouble()) * 180.0 / PI).toFloat()

          canvas.save()
          canvas.translate(tempPos[0] + extraDx, tempPos[1] + extraDy)
          canvas.rotate(tangentDeg + extraRot)
          if (charScale != 1f) {
            canvas.scale(charScale, charScale)
          }
          drawStyledTextSlice(
            canvas = canvas,
            text = line.text,
            start = i,
            end = i + step,
            x = -advance * 0.5f,
            y = vOffset,
            paint = measured.paint,
            glowPaint = measured.glowPaint,
            strokePaint = measured.strokePaint,
            surfaceStyle = el.surfaceStyle,
            surfacePaints = measured.surfacePaints,
            glowIntensity = el.glowIntensity,
            alphaByte = charAlpha,
            baseScale = baseScale,
            wordEntryElapsedMs = rawElapsedFromBeat,
            isGhostPass = false,
            vocalEnergy01 = vocalEnergy01,
          )
          canvas.restore()

          distCursor += advance
          i += step
          globalCharIndex++
          localVisibleIdx++
        }
      }

      localY += line.heightPx * measured.lineSpacingFactor
    }
  }

  private fun drawTextOnArcPathDirect(
    canvas: Canvas,
    text: String,
    path: Path,
    hOffset: Float,
    vOffset: Float,
    paint: TextPaint,
    glowPaint: TextPaint?,
    strokePaint: TextPaint?,
    surfaceStyle: TextSurfaceStyle,
    surfacePaints: SurfacePaintBundle,
    glowIntensity: Float,
    alphaByte: Int,
    wordEntryElapsedMs: Float,
    vocalEnergy01: Float = 0f,
  ) {
    if (alphaByte <= 0) return
    if (surfaceStyle == TextSurfaceStyle.HALATION_BLOOM) {
      surfacePaints.applyHalationVoiceEnergy(vocalEnergy01)
      val bloomBoost = computeHalationBloomBoost(wordEntryElapsedMs)
      val voiceAlphaBoost = 0.16f * vocalEnergy01.coerceIn(0f, 1f)
      surfacePaints.halationOuterPaint?.let { outer ->
        outer.alpha =
          (alphaByte * (0.38f + 0.54f * bloomBoost + voiceAlphaBoost).coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        canvas.drawTextOnPath(text, path, hOffset, vOffset, outer)
      }
      surfacePaints.halationInnerPaint?.let { inner ->
        inner.alpha =
          (alphaByte * (0.30f + 0.62f * bloomBoost + voiceAlphaBoost).coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        canvas.drawTextOnPath(text, path, hOffset, vOffset, inner)
      }
    } else if (glowPaint != null) {
      val glowAlphaFactor = (0.55f * glowIntensity.coerceIn(0.25f, 1.8f)).coerceIn(0.15f, 0.95f)
      glowPaint.alpha = (alphaByte * glowAlphaFactor).toInt().coerceIn(0, 255)
      canvas.drawTextOnPath(text, path, hOffset, vOffset, glowPaint)
    }
    if (strokePaint != null) {
      strokePaint.alpha = alphaByte
      canvas.drawTextOnPath(text, path, hOffset, vOffset, strokePaint)
    }
    paint.alpha = alphaByte
    canvas.drawTextOnPath(text, path, hOffset, vOffset, paint)
  }

  private fun drawVectorFlourish(
    canvas: Canvas,
    canvasWidth: Int,
    canvasHeight: Int,
    elementCenterX: Float,
    elementCenterY: Float,
    measured: MeasuredElement,
    elapsedMs: Float,
    baseScale: Float,
    baseAlphaByte: Int,
  ) {
    val progress = computeFlourishProgress(elapsedMs)
    if (progress <= 0.005f || baseAlphaByte <= 1) return

    val flourishPaint = measured.surfacePaints.flourishPaint ?: return
    val baseStrokeW = (2.5f * baseScale).coerceAtLeast(1.8f)
    flourishPaint.alpha = (baseAlphaByte * 0.94f).toInt().coerceIn(0, 255)
    flourishPaint.strokeWidth = baseStrokeW

    val hw = measured.blockWidthPx / 2f
    val hh = measured.blockHeightPx / 2f
    tempFlourishFullPath.reset()
    tempFlourishSegmentPath.reset()

    when (measured.element.flourish) {
      VectorFlourish.NONE -> return
      VectorFlourish.HAND_CIRCLE -> {
        // Slightly imperfect double-loop hand-drawn ink oval encircling the emphasis word
        val rx = hw + 13.5f * baseScale
        val ry = hh + 8.5f * baseScale
        val totalSteps = 56
        val totalAngleRad = (2.0 * PI) * 1.28 // overlapping organic second pass
        val startAngleRad = -0.35
        for (stepIdx in 0..totalSteps) {
          val frac = stepIdx.toFloat() / totalSteps.toFloat()
          val theta = startAngleRad + frac * totalAngleRad
          val wobbleR = 1f + 0.042f * sin(2.0 * theta).toFloat() + 0.026f * cos(3.0 * theta).toFloat()
          val loopDriftY = (frac - 0.5f) * 2.8f * baseScale
          val loopDriftX = (frac - 0.5f) * 1.8f * baseScale
          val px = cos(theta).toFloat() * rx * wobbleR + loopDriftX
          val py = sin(theta).toFloat() * ry * wobbleR + loopDriftY
          if (stepIdx == 0) tempFlourishFullPath.moveTo(px, py)
          else tempFlourishFullPath.lineTo(px, py)
        }
        tempPathMeasure.setPath(tempFlourishFullPath, false)
        val totalLen = tempPathMeasure.length
        if (totalLen > 0f) {
          tempPathMeasure.getSegment(0f, totalLen * progress, tempFlourishSegmentPath, true)
          canvas.drawPath(tempFlourishSegmentPath, flourishPaint)
        }
      }
      VectorFlourish.SWOOSH_UNDERLINE -> {
        // Tapered curved brush underline sweeping left-to-right beneath punchline words
        val startX = -hw - 4f * baseScale
        val endX = hw + 7f * baseScale
        val baseY = hh + 6.5f * baseScale
        tempFlourishFullPath.moveTo(startX, baseY + 1.8f * baseScale)
        tempFlourishFullPath.cubicTo(
          -hw * 0.25f,
          baseY - 4.2f * baseScale,
          hw * 0.45f,
          baseY - 2.6f * baseScale,
          endX,
          baseY - 1.2f * baseScale,
        )
        tempPathMeasure.setPath(tempFlourishFullPath, false)
        val totalLen = tempPathMeasure.length
        if (totalLen > 0f) {
          val drawnLen = totalLen * progress
          // Tapered brush feel: thin entry -> rich mid-belly -> crisp whipped tip
          val seg1End = min(drawnLen, totalLen * 0.22f)
          if (seg1End > 0f) {
            tempFlourishSegmentPath.reset()
            flourishPaint.strokeWidth = baseStrokeW * 0.72f
            tempPathMeasure.getSegment(0f, seg1End, tempFlourishSegmentPath, true)
            canvas.drawPath(tempFlourishSegmentPath, flourishPaint)
          }
          if (drawnLen > totalLen * 0.16f) {
            tempFlourishSegmentPath.reset()
            flourishPaint.strokeWidth = baseStrokeW * 1.32f
            tempPathMeasure.getSegment(
              totalLen * 0.16f,
              min(drawnLen, totalLen * 0.76f),
              tempFlourishSegmentPath,
              true,
            )
            canvas.drawPath(tempFlourishSegmentPath, flourishPaint)
          }
          if (drawnLen > totalLen * 0.70f) {
            tempFlourishSegmentPath.reset()
            flourishPaint.strokeWidth = baseStrokeW * 0.68f
            tempPathMeasure.getSegment(totalLen * 0.70f, drawnLen, tempFlourishSegmentPath, true)
            canvas.drawPath(tempFlourishSegmentPath, flourishPaint)
          }
        }
      }
      VectorFlourish.SPARKLE_BURST -> {
        // 3 hand-drawn radial accent ticks popping off the top-right corner of the word
        val originX = hw + 5.5f * baseScale
        val originY = -hh - 3.0f * baseScale
        val anglesDeg = floatArrayOf(-72f, -38f, -4f)
        val lengths = floatArrayOf(11.5f * baseScale, 15.5f * baseScale, 10.5f * baseScale)
        val startGap = 3.2f * baseScale
        for (k in 0..2) {
          val rad = anglesDeg[k] * PI / 180.0
          val cosA = cos(rad).toFloat()
          val sinA = sin(rad).toFloat()
          val sx = originX + cosA * startGap
          val sy = originY + sinA * startGap
          val ex = originX + cosA * (startGap + lengths[k])
          val ey = originY + sinA * (startGap + lengths[k])
          val ctrlX = (sx + ex) * 0.5f - sinA * 1.8f * baseScale
          val ctrlY = (sy + ey) * 0.5f + cosA * 1.8f * baseScale
          tempFlourishFullPath.moveTo(sx, sy)
          tempFlourishFullPath.quadTo(ctrlX, ctrlY, ex, ey)
        }
        tempPathMeasure.setPath(tempFlourishFullPath, false)
        var contourIdx = 0
        do {
          val cLen = tempPathMeasure.length
          if (cLen > 0f) {
            val localProg = ((progress - contourIdx * 0.12f) / 0.76f).coerceIn(0f, 1f)
            if (localProg > 0.01f) {
              tempFlourishSegmentPath.reset()
              flourishPaint.strokeWidth = baseStrokeW * if (contourIdx == 1) 1.05f else 0.85f
              tempPathMeasure.getSegment(0f, cLen * localProg, tempFlourishSegmentPath, true)
              canvas.drawPath(tempFlourishSegmentPath, flourishPaint)
            }
          }
          contourIdx++
        } while (tempPathMeasure.nextContour())
      }
      VectorFlourish.POINTER_ARROW -> {
        // Curved hand-drawn ink arrow pointing toward the center subject
        val dxToSubject = (canvasWidth * 0.50f) - elementCenterX
        val dyToSubject = (canvasHeight * 0.52f) - elementCenterY
        val dirX = if (abs(dxToSubject) < 12f * baseScale) 0f else if (dxToSubject > 0f) 1f else -1f
        val dirY = if (dyToSubject >= 0f) 1f else -1f

        val startX = if (dirX != 0f) dirX * (hw + 7f * baseScale) else 0f
        val startY = if (dirX == 0f) dirY * (hh + 7f * baseScale) else 0f
        val tipX = startX + dirX * 26f * baseScale + if (dirX == 0f) 6f * baseScale else 0f
        val tipY = startY + dirY * 24f * baseScale
        val ctrlX = (startX + tipX) * 0.5f + (if (dirX == 0f) -11f else 5f) * baseScale
        val ctrlY = (startY + tipY) * 0.5f - dirY * 6f * baseScale

        // Contour 1: Curved shaft
        tempFlourishFullPath.moveTo(startX, startY)
        tempFlourishFullPath.quadTo(ctrlX, ctrlY, tipX, tipY)

        // Contour 2: Hand-drawn V arrowhead aligned to arrival tangent
        val arrivalAngle = atan2((tipY - ctrlY).toDouble(), (tipX - ctrlX).toDouble())
        val headLen = 8.5f * baseScale
        val wing1Angle = arrivalAngle + Math.toRadians(148.0)
        val wing2Angle = arrivalAngle - Math.toRadians(148.0)
        tempFlourishFullPath.moveTo(
          tipX + cos(wing1Angle).toFloat() * headLen,
          tipY + sin(wing1Angle).toFloat() * headLen,
        )
        tempFlourishFullPath.lineTo(tipX, tipY)
        tempFlourishFullPath.lineTo(
          tipX + cos(wing2Angle).toFloat() * headLen,
          tipY + sin(wing2Angle).toFloat() * headLen,
        )

        tempPathMeasure.setPath(tempFlourishFullPath, false)
        // Draw shaft over [0..0.74], arrowhead over [0.68..1.0]
        val shaftLen = tempPathMeasure.length
        if (shaftLen > 0f) {
          val shaftProg = (progress / 0.74f).coerceIn(0f, 1f)
          if (shaftProg > 0.01f) {
            tempFlourishSegmentPath.reset()
            tempPathMeasure.getSegment(0f, shaftLen * shaftProg, tempFlourishSegmentPath, true)
            canvas.drawPath(tempFlourishSegmentPath, flourishPaint)
          }
        }
        if (tempPathMeasure.nextContour()) {
          val headContourLen = tempPathMeasure.length
          val headProg = ((progress - 0.66f) / 0.34f).coerceIn(0f, 1f)
          if (headContourLen > 0f && headProg > 0.01f) {
            tempFlourishSegmentPath.reset()
            tempPathMeasure.getSegment(0f, headContourLen * headProg, tempFlourishSegmentPath, true)
            canvas.drawPath(tempFlourishSegmentPath, flourishPaint)
          }
        }
      }
    }
  }

  private fun drawElementLinesPass(
    canvas: Canvas,
    active: CaptionComposition,
    measured: MeasuredElement,
    positionMs: Long,
    wordTriggeredStartMs: Long,
    rawElapsedFromBeat: Float,
    compDuration: Long,
    baseScale: Float,
    motionElapsedSec: Float,
    mSpeed: Float,
    mIntensity: Float,
    passAlphaByte: Int,
    isGhostPass: Boolean,
    vocalEnergy01: Float = 0f,
  ) {
    val el = measured.element
    val isTypewriter = el.motionEffect == LayerMotionEffect.TYPEWRITER
    val isWave = el.motionEffect == LayerMotionEffect.WAVE
    val totalChars = if (isTypewriter) measured.lines.sumOf { it.text.length }.coerceAtLeast(1) else 0
    val revealedChars =
      if (isTypewriter) {
        val revealDurMs = (compDuration * 0.62f / mSpeed).coerceIn(300f, 2400f)
        val frac = (rawElapsedFromBeat.coerceAtLeast(0f) / revealDurMs).coerceIn(0f, 1f)
        (frac * totalChars).toInt().coerceIn(0, totalChars)
      } else {
        Int.MAX_VALUE
      }
    var charCursor = 0

    val isKaraoke = el.karaokeMode != KaraokeHighlightMode.OFF && !isTypewriter
    val isCharReveal = el.letterAnimation == LetterAnimationStyle.CHAR_REVEAL && !isTypewriter
    val canDoWordReveal =
      !isTypewriter &&
        !isKaraoke &&
        !isCharReveal &&
        el.hierarchyLevel != HierarchyLevel.DOMINANT &&
        el.wordIndices.size > 1 &&
        active.wordMappings.isNotEmpty()

    val elementSeed = el.id.hashCode()
    var globalCharIndex = 0
    var localY = -measured.blockHeightPx / 2f
    var waveWordSeq = 0

    for (line in measured.lines) {
      val baselineY = localY - line.ascentPx
      val lineStartX =
        when (el.alignment) {
          TextAlignment.LEFT -> -measured.blockWidthPx / 2f
          TextAlignment.RIGHT -> measured.blockWidthPx / 2f - line.widthPx
          TextAlignment.CENTER -> -line.widthPx / 2f
        }

      if (isTypewriter) {
        val remainingChars = (revealedChars - charCursor).coerceIn(0, line.text.length)
        charCursor += line.text.length
        if (remainingChars > 0) {
          val slice =
            if (remainingChars >= line.text.length) line.text else line.text.substring(0, remainingChars)
          drawTextToken(
            canvas = canvas,
            text = slice,
            x = lineStartX,
            y = baselineY,
            ascentPx = line.ascentPx,
            descentPx = line.descentPx,
            paint = measured.paint,
            glowPaint = measured.glowPaint,
            strokePaint = measured.strokePaint,
            surfaceStyle = el.surfaceStyle,
            surfacePaints = measured.surfacePaints,
            letterAnimation = el.letterAnimation,
            glowIntensity = el.glowIntensity,
            alphaByte = passAlphaByte,
            baseScale = baseScale,
            positionMs = positionMs,
            elementStartMs = wordTriggeredStartMs,
            wordEntryElapsedMs = rawElapsedFromBeat,
            wordStartMs = wordTriggeredStartMs,
            wordEndMs = active.endMs,
            globalCharStart = globalCharIndex,
            elementSeed = elementSeed,
            isGhostPass = isGhostPass,
            vocalEnergy01 = vocalEnergy01,
          )
        }
        globalCharIndex += line.text.length
      } else if (isKaraoke && line.words.isNotEmpty()) {
        val highlightPaint = measured.karaokeHighlightPaint ?: measured.paint
        val pillPaint = measured.karaokePillPaint
        for (wSpan in line.words) {
          val waveOffsetY =
            if (isWave) {
              val off = sin(motionElapsedSec * 5.8f - waveWordSeq * 0.9f) * 7.5f * baseScale * mIntensity
              waveWordSeq++
              off
            } else {
              0f
            }
          val (wStart, wEnd) =
            resolveWordTimeWindow(
              comp = active,
              globalWordOrder = wSpan.globalWordOrder,
              mappedWordIndex = wSpan.wordIndexInComp,
              totalWordsInElement = measured.totalWordsInElement,
            )
          val kState =
            resolveActiveWordTiming(
              comp = active,
              globalWordOrder = wSpan.globalWordOrder,
              mappedWordIndex = wSpan.wordIndexInComp,
              totalWordsInElement = measured.totalWordsInElement,
              positionMs = positionMs,
            )
          val wordElapsed = (positionMs - wStart).toFloat()
          val wx = lineStartX + wSpan.startXInLine
          val wy = baselineY + waveOffsetY

          if (kState.isActive) {
            val voiceBreathScale = computeVoiceBreathingScale(vocalEnergy01)
            when (el.karaokeMode) {
              KaraokeHighlightMode.COLOR_POP -> {
                val popScale = (1f + sin(kState.progress01 * Math.PI).toFloat() * 0.08f) * voiceBreathScale
                val cx = wx + wSpan.widthPx / 2f
                val cy = wy + (line.ascentPx + line.descentPx) / 2f
                canvas.save()
                canvas.scale(popScale, popScale, cx, cy)
                drawTextToken(
                  canvas = canvas,
                  text = wSpan.text,
                  x = wx,
                  y = wy,
                  ascentPx = line.ascentPx,
                  descentPx = line.descentPx,
                  paint = highlightPaint,
                  glowPaint = measured.glowPaint,
                  strokePaint = measured.strokePaint,
                  surfaceStyle = el.surfaceStyle,
                  surfacePaints = measured.surfacePaints,
                  letterAnimation = el.letterAnimation,
                  glowIntensity = el.glowIntensity,
                  alphaByte = passAlphaByte,
                  baseScale = baseScale,
                  positionMs = positionMs,
                  elementStartMs = wordTriggeredStartMs,
                  wordEntryElapsedMs = wordElapsed,
                  wordStartMs = wStart,
                  wordEndMs = wEnd,
                  globalCharStart = globalCharIndex,
                  elementSeed = elementSeed,
                  isGhostPass = isGhostPass,
                  vocalEnergy01 = vocalEnergy01,
                )
                canvas.restore()
              }
              KaraokeHighlightMode.PILL_SWEEP -> {
                if (!isGhostPass && pillPaint != null) {
                  val padX = 6.5f * baseScale
                  val padY = 3.2f * baseScale
                  val radius = 7.5f * baseScale
                  pillPaint.alpha = (passAlphaByte * 0.92f).toInt().coerceIn(0, 255)
                  tempKaraokeRect.set(
                    wx - padX,
                    wy + line.ascentPx - padY,
                    wx + wSpan.widthPx + padX,
                    wy + line.descentPx + padY,
                  )
                  canvas.drawRoundRect(tempKaraokeRect, radius, radius, pillPaint)
                }
                val popScale = (1f + sin(kState.progress01 * Math.PI).toFloat() * 0.05f) * voiceBreathScale
                val cx = wx + wSpan.widthPx / 2f
                val cy = wy + (line.ascentPx + line.descentPx) / 2f
                canvas.save()
                canvas.scale(popScale, popScale, cx, cy)
                drawTextToken(
                  canvas = canvas,
                  text = wSpan.text,
                  x = wx,
                  y = wy,
                  ascentPx = line.ascentPx,
                  descentPx = line.descentPx,
                  paint = highlightPaint,
                  glowPaint = null,
                  strokePaint = measured.strokePaint,
                  surfaceStyle = el.surfaceStyle,
                  surfacePaints = measured.surfacePaints,
                  letterAnimation = el.letterAnimation,
                  glowIntensity = el.glowIntensity,
                  alphaByte = passAlphaByte,
                  baseScale = baseScale,
                  positionMs = positionMs,
                  elementStartMs = wordTriggeredStartMs,
                  wordEntryElapsedMs = wordElapsed,
                  wordStartMs = wStart,
                  wordEndMs = wEnd,
                  globalCharStart = globalCharIndex,
                  elementSeed = elementSeed,
                  isGhostPass = isGhostPass,
                  vocalEnergy01 = vocalEnergy01,
                )
                canvas.restore()
              }
              KaraokeHighlightMode.SCALE_POP -> {
                val springCurve = sin(kState.progress01 * Math.PI).toFloat()
                val popScale = (1f + springCurve * 0.20f) * voiceBreathScale
                val liftY = -springCurve * 3.0f * baseScale
                val cx = wx + wSpan.widthPx / 2f
                val cy = wy + (line.ascentPx + line.descentPx) / 2f
                canvas.save()
                canvas.translate(0f, liftY)
                canvas.scale(popScale, popScale, cx, cy)
                drawTextToken(
                  canvas = canvas,
                  text = wSpan.text,
                  x = wx,
                  y = wy,
                  ascentPx = line.ascentPx,
                  descentPx = line.descentPx,
                  paint = highlightPaint,
                  glowPaint = measured.glowPaint,
                  strokePaint = measured.strokePaint,
                  surfaceStyle = el.surfaceStyle,
                  surfacePaints = measured.surfacePaints,
                  letterAnimation = el.letterAnimation,
                  glowIntensity = el.glowIntensity,
                  alphaByte = passAlphaByte,
                  baseScale = baseScale,
                  positionMs = positionMs,
                  elementStartMs = wordTriggeredStartMs,
                  wordEntryElapsedMs = wordElapsed,
                  wordStartMs = wStart,
                  wordEndMs = wEnd,
                  globalCharStart = globalCharIndex,
                  elementSeed = elementSeed,
                  isGhostPass = isGhostPass,
                  vocalEnergy01 = vocalEnergy01,
                )
                canvas.restore()
              }
              KaraokeHighlightMode.UNDERLINE_SWEEP -> {
                if (!isGhostPass && pillPaint != null) {
                  val barH = max(2.6f, 3.2f * baseScale)
                  val sweepFrac = (0.28f + 0.72f * kState.progress01).coerceIn(0.28f, 1f)
                  val barTop = wy + line.descentPx * 0.45f
                  pillPaint.alpha = passAlphaByte
                  tempKaraokeRect.set(
                    wx,
                    barTop,
                    wx + wSpan.widthPx * sweepFrac,
                    barTop + barH,
                  )
                  canvas.drawRoundRect(tempKaraokeRect, barH / 2f, barH / 2f, pillPaint)
                }
                val needsVoiceScale = voiceBreathScale > 1.0005f
                if (needsVoiceScale) {
                  val cx = wx + wSpan.widthPx / 2f
                  val cy = wy + (line.ascentPx + line.descentPx) / 2f
                  canvas.save()
                  canvas.scale(voiceBreathScale, voiceBreathScale, cx, cy)
                }
                drawTextToken(
                  canvas = canvas,
                  text = wSpan.text,
                  x = wx,
                  y = wy,
                  ascentPx = line.ascentPx,
                  descentPx = line.descentPx,
                  paint = highlightPaint,
                  glowPaint = measured.glowPaint,
                  strokePaint = measured.strokePaint,
                  surfaceStyle = el.surfaceStyle,
                  surfacePaints = measured.surfacePaints,
                  letterAnimation = el.letterAnimation,
                  glowIntensity = el.glowIntensity,
                  alphaByte = passAlphaByte,
                  baseScale = baseScale,
                  positionMs = positionMs,
                  elementStartMs = wordTriggeredStartMs,
                  wordEntryElapsedMs = wordElapsed,
                  wordStartMs = wStart,
                  wordEndMs = wEnd,
                  globalCharStart = globalCharIndex,
                  elementSeed = elementSeed,
                  isGhostPass = isGhostPass,
                  vocalEnergy01 = vocalEnergy01,
                )
                if (needsVoiceScale) {
                  canvas.restore()
                }
              }
              KaraokeHighlightMode.OFF -> Unit
            }
          } else {
            val inactiveAlpha =
              if (kState.isSpokenAlready) passAlphaByte
              else (passAlphaByte * 0.84f).toInt().coerceIn(0, 255)
            drawTextToken(
              canvas = canvas,
              text = wSpan.text,
              x = wx,
              y = wy,
              ascentPx = line.ascentPx,
              descentPx = line.descentPx,
              paint = measured.paint,
              glowPaint = measured.glowPaint,
              strokePaint = measured.strokePaint,
              surfaceStyle = el.surfaceStyle,
              surfacePaints = measured.surfacePaints,
              letterAnimation = el.letterAnimation,
              glowIntensity = el.glowIntensity,
              alphaByte = inactiveAlpha,
              baseScale = baseScale,
              positionMs = positionMs,
              elementStartMs = wordTriggeredStartMs,
              wordEntryElapsedMs = if (kState.isSpokenAlready) wordElapsed else rawElapsedFromBeat,
              wordStartMs = wStart,
              wordEndMs = wEnd,
              globalCharStart = globalCharIndex,
              elementSeed = elementSeed,
              isGhostPass = isGhostPass,
              vocalEnergy01 = vocalEnergy01,
            )
          }
          globalCharIndex += wSpan.text.length + 1
        }
      } else if (isCharReveal && line.words.isNotEmpty()) {
        for (wSpan in line.words) {
          val waveOffsetY =
            if (isWave) {
              val off = sin(motionElapsedSec * 5.8f - waveWordSeq * 0.9f) * 7.5f * baseScale * mIntensity
              waveWordSeq++
              off
            } else {
              0f
            }
          val (wStart, wEnd) =
            resolveWordTimeWindow(
              comp = active,
              globalWordOrder = wSpan.globalWordOrder,
              mappedWordIndex = wSpan.wordIndexInComp,
              totalWordsInElement = measured.totalWordsInElement,
            )
          if (positionMs >= wStart) {
            drawTextToken(
              canvas = canvas,
              text = wSpan.text,
              x = lineStartX + wSpan.startXInLine,
              y = baselineY + waveOffsetY,
              ascentPx = line.ascentPx,
              descentPx = line.descentPx,
              paint = measured.paint,
              glowPaint = measured.glowPaint,
              strokePaint = measured.strokePaint,
              surfaceStyle = el.surfaceStyle,
              surfacePaints = measured.surfacePaints,
              letterAnimation = el.letterAnimation,
              glowIntensity = el.glowIntensity,
              alphaByte = passAlphaByte,
              baseScale = baseScale,
              positionMs = positionMs,
              elementStartMs = wordTriggeredStartMs,
              wordEntryElapsedMs = (positionMs - wStart).toFloat(),
              wordStartMs = wStart,
              wordEndMs = wEnd,
              globalCharStart = globalCharIndex,
              elementSeed = elementSeed,
              isGhostPass = isGhostPass,
              vocalEnergy01 = vocalEnergy01,
            )
          }
          globalCharIndex += wSpan.text.length + 1
        }
      } else if (isWave && line.words.isNotEmpty()) {
        for (wSpan in line.words) {
          val waveOffsetY = sin(motionElapsedSec * 5.8f - waveWordSeq * 0.9f) * 7.5f * baseScale * mIntensity
          waveWordSeq++
          val (wStart, wEnd) =
            resolveWordTimeWindow(
              comp = active,
              globalWordOrder = wSpan.globalWordOrder,
              mappedWordIndex = wSpan.wordIndexInComp,
              totalWordsInElement = measured.totalWordsInElement,
            )
          drawTextToken(
            canvas = canvas,
            text = wSpan.text,
            x = lineStartX + wSpan.startXInLine,
            y = baselineY + waveOffsetY,
            ascentPx = line.ascentPx,
            descentPx = line.descentPx,
            paint = measured.paint,
            glowPaint = measured.glowPaint,
            strokePaint = measured.strokePaint,
            surfaceStyle = el.surfaceStyle,
            surfacePaints = measured.surfacePaints,
            letterAnimation = el.letterAnimation,
            glowIntensity = el.glowIntensity,
            alphaByte = passAlphaByte,
            baseScale = baseScale,
            positionMs = positionMs,
            elementStartMs = wordTriggeredStartMs,
            wordEntryElapsedMs = rawElapsedFromBeat,
            wordStartMs = wStart,
            wordEndMs = wEnd,
            globalCharStart = globalCharIndex,
            elementSeed = elementSeed,
            isGhostPass = isGhostPass,
            vocalEnergy01 = vocalEnergy01,
          )
          globalCharIndex += wSpan.text.length + 1
        }
      } else if (canDoWordReveal && line.words.size > 1) {
        for (wSpan in line.words) {
          val wt = wSpan.wordIndexInComp?.let { active.wordMappings.getOrNull(it) }
          val wAlphaRatio =
            if (wt != null) {
              ((positionMs - (wt.startMs - 30L)).coerceAtLeast(0L) / 110f).coerceIn(0f, 1f)
            } else {
              1f
            }
          if (wAlphaRatio <= 0.02f) {
            globalCharIndex += wSpan.text.length + 1
            continue
          }
          val wordAlpha = (passAlphaByte * wAlphaRatio).toInt()
          val wordRise = (1f - wAlphaRatio) * 5f * baseScale
          val (wStart, wEnd) =
            resolveWordTimeWindow(
              comp = active,
              globalWordOrder = wSpan.globalWordOrder,
              mappedWordIndex = wSpan.wordIndexInComp,
              totalWordsInElement = measured.totalWordsInElement,
            )
          drawTextToken(
            canvas = canvas,
            text = wSpan.text,
            x = lineStartX + wSpan.startXInLine,
            y = baselineY + wordRise,
            ascentPx = line.ascentPx,
            descentPx = line.descentPx,
            paint = measured.paint,
            glowPaint = measured.glowPaint,
            strokePaint = measured.strokePaint,
            surfaceStyle = el.surfaceStyle,
            surfacePaints = measured.surfacePaints,
            letterAnimation = el.letterAnimation,
            glowIntensity = el.glowIntensity,
            alphaByte = wordAlpha,
            baseScale = baseScale,
            positionMs = positionMs,
            elementStartMs = wordTriggeredStartMs,
            wordEntryElapsedMs = (positionMs - wStart).toFloat(),
            wordStartMs = wStart,
            wordEndMs = wEnd,
            globalCharStart = globalCharIndex,
            elementSeed = elementSeed,
            isGhostPass = isGhostPass,
            vocalEnergy01 = vocalEnergy01,
          )
          globalCharIndex += wSpan.text.length + 1
        }
      } else {
        drawTextToken(
          canvas = canvas,
          text = line.text,
          x = lineStartX,
          y = baselineY,
          ascentPx = line.ascentPx,
          descentPx = line.descentPx,
          paint = measured.paint,
          glowPaint = measured.glowPaint,
          strokePaint = measured.strokePaint,
          surfaceStyle = el.surfaceStyle,
          surfacePaints = measured.surfacePaints,
          letterAnimation = el.letterAnimation,
          glowIntensity = el.glowIntensity,
          alphaByte = passAlphaByte,
          baseScale = baseScale,
          positionMs = positionMs,
          elementStartMs = wordTriggeredStartMs,
          wordEntryElapsedMs = rawElapsedFromBeat,
          wordStartMs = wordTriggeredStartMs,
          wordEndMs = active.endMs,
          globalCharStart = globalCharIndex,
          elementSeed = elementSeed,
          isGhostPass = isGhostPass,
          vocalEnergy01 = vocalEnergy01,
        )
        globalCharIndex += line.text.length
      }
      localY += line.heightPx * measured.lineSpacingFactor
    }
  }

  private fun drawTextToken(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    ascentPx: Float,
    descentPx: Float,
    paint: TextPaint,
    glowPaint: TextPaint?,
    strokePaint: TextPaint?,
    surfaceStyle: TextSurfaceStyle,
    surfacePaints: SurfacePaintBundle,
    letterAnimation: LetterAnimationStyle,
    glowIntensity: Float,
    alphaByte: Int,
    baseScale: Float,
    positionMs: Long,
    elementStartMs: Long,
    wordEntryElapsedMs: Float,
    wordStartMs: Long,
    wordEndMs: Long,
    globalCharStart: Int,
    elementSeed: Int,
    isGhostPass: Boolean,
    vocalEnergy01: Float = 0f,
  ) {
    if (text.isEmpty() || alphaByte <= 0) return

    // Fast single-draw path when per-character animation is disabled
    if (letterAnimation == LetterAnimationStyle.NONE) {
      drawStyledTextSlice(
        canvas = canvas,
        text = text,
        start = 0,
        end = text.length,
        x = x,
        y = y,
        paint = paint,
        glowPaint = glowPaint,
        strokePaint = strokePaint,
        surfaceStyle = surfaceStyle,
        surfacePaints = surfacePaints,
        glowIntensity = glowIntensity,
        alphaByte = alphaByte,
        baseScale = baseScale,
        wordEntryElapsedMs = wordEntryElapsedMs,
        isGhostPass = isGhostPass,
        vocalEnergy01 = vocalEnergy01,
      )
      return
    }

    // Per-character rendering path driven strictly by deterministic playbackTimeMs (positionMs)
    val advances = FloatArray(text.length)
    paint.getTextWidths(text, advances)

    val totalRevealUnits =
      if (letterAnimation == LetterAnimationStyle.CHAR_REVEAL) {
        countVisibleGlyphClusters(text).coerceAtLeast(1)
      } else {
        1
      }

    var cursorX = x
    var i = 0
    var charSeq = globalCharStart
    var localVisibleIdx = 0
    val glyphMidOffsetY = (ascentPx + descentPx) * 0.5f

    while (i < text.length) {
      val step =
        if (i + 1 < text.length && Character.isHighSurrogate(text[i]) && Character.isLowSurrogate(text[i + 1])) {
          2
        } else {
          1
        }
      val advance = if (step == 2) advances[i] + advances[i + 1] else advances[i]

      if (text[i].isWhitespace()) {
        cursorX += advance
        i += step
        charSeq++
        continue
      }

      var charAlphaByte = alphaByte
      var charScale = 1f
      var charRotDeg = 0f
      var charOffsetX = 0f
      var charOffsetY = 0f
      var charEntryElapsedMs = wordEntryElapsedMs

      when (letterAnimation) {
        LetterAnimationStyle.STAGGER_POP -> {
          val pop = computeStaggerPopState(positionMs - elementStartMs, charSeq)
          if (pop.alpha <= 0.01f) {
            cursorX += advance
            i += step
            charSeq++
            continue
          }
          charAlphaByte = (alphaByte * pop.alpha).toInt().coerceIn(0, 255)
          charScale = pop.scale
          charOffsetY = pop.offsetYDp * baseScale
          charEntryElapsedMs = ((positionMs - elementStartMs) - charSeq * STAGGER_POP_STEP_MS).toFloat()
        }
        LetterAnimationStyle.CHAR_REVEAL -> {
          val revealAlpha =
            computeCharRevealAlpha(
              positionMs = positionMs,
              wordStartMs = wordStartMs,
              wordEndMs = wordEndMs,
              charIndexInWord = localVisibleIdx,
              totalCharsInWord = totalRevealUnits,
            )
          if (revealAlpha <= 0.01f) {
            cursorX += advance
            i += step
            charSeq++
            localVisibleIdx++
            continue
          }
          charAlphaByte = (alphaByte * revealAlpha).toInt().coerceIn(0, 255)
          charScale = 0.94f + 0.06f * revealAlpha
          val charRevealStartMs =
            wordStartMs + ((wordEndMs - wordStartMs).coerceAtLeast(40L) * localVisibleIdx / totalRevealUnits)
          charEntryElapsedMs = (positionMs - charRevealStartMs).toFloat()
        }
        LetterAnimationStyle.HAND_BOIL_15FPS -> {
          val boil = computeHandBoilTransform(positionMs, charSeq, elementSeed)
          charRotDeg = boil.rotationDeg
          charScale = boil.scale
          charOffsetX = boil.offsetXDp * baseScale
          charOffsetY = boil.offsetYDp * baseScale
        }
        LetterAnimationStyle.NONE -> Unit
      }

      val glyphCx = cursorX + advance * 0.5f
      val glyphCy = y + glyphMidOffsetY
      val needsTransform =
        charOffsetX != 0f || charOffsetY != 0f || charRotDeg != 0f || charScale != 1f

      if (needsTransform) {
        canvas.save()
        if (charOffsetX != 0f || charOffsetY != 0f) {
          canvas.translate(charOffsetX, charOffsetY)
        }
        if (charRotDeg != 0f) {
          canvas.rotate(charRotDeg, glyphCx, glyphCy)
        }
        if (charScale != 1f) {
          canvas.scale(charScale, charScale, glyphCx, glyphCy)
        }
      }

      drawStyledTextSlice(
        canvas = canvas,
        text = text,
        start = i,
        end = i + step,
        x = cursorX,
        y = y,
        paint = paint,
        glowPaint = glowPaint,
        strokePaint = strokePaint,
        surfaceStyle = surfaceStyle,
        surfacePaints = surfacePaints,
        glowIntensity = glowIntensity,
        alphaByte = charAlphaByte,
        baseScale = baseScale,
        wordEntryElapsedMs = charEntryElapsedMs,
        isGhostPass = isGhostPass,
        vocalEnergy01 = vocalEnergy01,
      )

      if (needsTransform) {
        canvas.restore()
      }

      cursorX += advance
      i += step
      charSeq++
      localVisibleIdx++
    }
  }

  private fun drawStyledTextSlice(
    canvas: Canvas,
    text: String,
    start: Int,
    end: Int,
    x: Float,
    y: Float,
    paint: TextPaint,
    glowPaint: TextPaint?,
    strokePaint: TextPaint?,
    surfaceStyle: TextSurfaceStyle,
    surfacePaints: SurfacePaintBundle,
    glowIntensity: Float,
    alphaByte: Int,
    baseScale: Float,
    wordEntryElapsedMs: Float,
    isGhostPass: Boolean,
    vocalEnergy01: Float = 0f,
  ) {
    if (alphaByte <= 0) return

    if (isGhostPass) {
      paint.alpha = alphaByte
      canvas.drawText(text, start, end, x, y, paint)
      return
    }

    when (surfaceStyle) {
      TextSurfaceStyle.HALATION_BLOOM -> {
        surfacePaints.applyHalationVoiceEnergy(vocalEnergy01)
        val bloomBoost = computeHalationBloomBoost(wordEntryElapsedMs)
        val voiceAlphaBoost = 0.16f * vocalEnergy01.coerceIn(0f, 1f)
        surfacePaints.halationOuterPaint?.let { outer ->
          val outerFactor = (0.38f + 0.54f * bloomBoost + voiceAlphaBoost).coerceIn(0f, 1f)
          outer.alpha = (alphaByte * outerFactor).toInt().coerceIn(0, 255)
          canvas.drawText(text, start, end, x, y, outer)
        }
        surfacePaints.halationInnerPaint?.let { inner ->
          val innerFactor = (0.30f + 0.62f * bloomBoost + voiceAlphaBoost).coerceIn(0f, 1f)
          inner.alpha = (alphaByte * innerFactor).toInt().coerceIn(0, 255)
          canvas.drawText(text, start, end, x, y, inner)
        }
        if (strokePaint != null) {
          strokePaint.alpha = alphaByte
          canvas.drawText(text, start, end, x, y, strokePaint)
        }
        paint.alpha = alphaByte
        canvas.drawText(text, start, end, x, y, paint)
      }
      TextSurfaceStyle.INFLATED_3D -> {
        surfacePaints.inflatedDropPaint?.let { drop ->
          drop.alpha = (alphaByte * 0.78f).toInt().coerceIn(0, 255)
          canvas.drawText(text, start, end, x + 2.4f * baseScale, y + 3.2f * baseScale, drop)
          drop.alpha = (alphaByte * 0.90f).toInt().coerceIn(0, 255)
          canvas.drawText(text, start, end, x + 1.2f * baseScale, y + 1.6f * baseScale, drop)
        }
        surfacePaints.inflatedBevelPaint?.let { bevel ->
          bevel.alpha = (alphaByte * 0.84f).toInt().coerceIn(0, 255)
          canvas.drawText(text, start, end, x - 1.35f * baseScale, y - 1.45f * baseScale, bevel)
        }
        if (strokePaint != null) {
          strokePaint.alpha = alphaByte
          canvas.drawText(text, start, end, x, y, strokePaint)
        }
        paint.alpha = alphaByte
        canvas.drawText(text, start, end, x, y, paint)
        surfacePaints.inflatedSpecularPaint?.let { spec ->
          spec.alpha = (alphaByte * 0.46f).toInt().coerceIn(0, 255)
          canvas.drawText(text, start, end, x - 1.6f * baseScale, y - 1.7f * baseScale, spec)
        }
      }
      TextSurfaceStyle.STANDARD,
      TextSurfaceStyle.CONDENSED_EDITORIAL,
      TextSurfaceStyle.FROSTED_GLASS -> {
        if (glowPaint != null) {
          val glowAlphaFactor = (0.55f * glowIntensity.coerceIn(0.25f, 1.8f)).coerceIn(0.15f, 0.95f)
          glowPaint.alpha = (alphaByte * glowAlphaFactor).toInt().coerceIn(0, 255)
          canvas.drawText(text, start, end, x, y, glowPaint)
        }
        if (strokePaint != null) {
          strokePaint.alpha = alphaByte
          canvas.drawText(text, start, end, x, y, strokePaint)
        }
        paint.alpha = alphaByte
        canvas.drawText(text, start, end, x, y, paint)
      }
    }
  }

  private fun countVisibleGlyphClusters(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
      val step =
        if (i + 1 < text.length && Character.isHighSurrogate(text[i]) && Character.isLowSurrogate(text[i + 1])) {
          2
        } else {
          1
        }
      if (!text[i].isWhitespace()) count++
      i += step
    }
    return count
  }

  private fun getOrComputeLayout(
    comp: CaptionComposition,
    canvasWidth: Int,
    canvasHeight: Int,
    shadowMultiplier: Float,
  ): CachedLayout {
    // O(1) identity fast-path: eliminates per-frame StringBuilder allocations during 60fps playback
    val fast = lastFastEntry
    if (fast != null &&
      fast.comp === comp &&
      fast.width == canvasWidth &&
      fast.height == canvasHeight &&
      fast.shadowMultiplier == shadowMultiplier
    ) {
      return fast.layout
    }

    val sig =
      buildString(256) {
        append(comp.id).append('|')
        append(canvasWidth).append('x').append(canvasHeight).append('|')
        append(comp.anchorXFraction).append(',').append(comp.anchorYFraction).append('|')
        append(comp.spatialMode).append('|')
        append(shadowMultiplier).append('|')
        comp.elements.forEach { el ->
          append(el.text).append(':').append(el.fontFamilyId).append(':').append(el.fontWeight)
          append(':').append(el.isItalic).append(':').append(el.isUnderlined).append(':').append(el.fontSizeSp)
          append(':').append(el.colorHex).append(':').append(el.alignment)
          append(':').append(el.uppercase).append(':').append(el.letterSpacingEm)
          append(':').append(el.lineHeightMultiplier).append(':').append(el.opacity)
          append(':').append(el.inlineRowGroup).append(':').append(el.rotationDeg).append(':').append(el.tiltDegrees)
          append(':').append(el.offsetXEm).append(':').append(el.offsetYEm)
          append(':').append(el.glowColorHex).append(':').append(el.glowIntensity)
          append(':').append(el.strokeWidthFactor).append(':').append(el.strokeColorHex)
          append(':').append(el.shadowEnabled).append(':').append(el.shadowIntensity).append(':').append(el.shadowColorHex)
          append(':').append(el.backgroundColorHex).append(':').append(el.backgroundAlpha).append(':').append(el.backgroundCornerRadiusDp)
          append(':').append(el.depthLayer)
          append(':').append(el.karaokeMode).append(':').append(el.karaokeHighlightColorHex).append(':').append(el.karaokePillColorHex)
          append(':').append(el.letterAnimation).append(':').append(el.surfaceStyle)
          append(':').append(el.designerFrame).append(':').append(el.flourish).append(':').append(el.polarityVariantIndex).append(';')
        }
      }
    layoutCache[sig]?.let { cached ->
      lastFastEntry = FastCacheEntry(comp, canvasWidth, canvasHeight, shadowMultiplier, cached)
      return cached
    }

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
      val karaokeHighlight: TextPaint?,
      val karaokePill: Paint?,
      val surface: SurfacePaintBundle,
      val lineSpacingFactor: Float,
    )

    // For LEFT_RIGHT_SPLIT with a single 2–4 word element, split cleanly into left & right halves
    val effectiveSourceElements =
      if (comp.spatialMode == SpatialLayoutMode.LEFT_RIGHT_SPLIT && comp.elements.size == 1) {
        splitSingleElementForLeftRight(comp.elements.first())
      } else {
        comp.elements
      }

    val perElementMaxLineW =
      if (comp.spatialMode == SpatialLayoutMode.LEFT_RIGHT_SPLIT && effectiveSourceElements.size >= 2) {
        canvasWidth * 0.34f
      } else {
        maxLineWidthPx
      }

    // Measure individual elements first
    val elementSizes =
      effectiveSourceElements.map { el ->
        val textStr = if (el.uppercase) el.text.uppercase() else el.text
        val typeface = FontRegistry.getTypeface(context, el.fontFamilyId, el.fontWeight, el.isItalic)
        var textSizePx = el.fontSizeSp * baseScale
        val lineSpacingFactor = el.lineHeightMultiplier.coerceIn(0.8f, 1.8f)

        val resolvedTextColor =
          when (el.designerFrame) {
            DesignerFrameStyle.PAPER_CUTOUT_PILL -> {
              val raw = parseSafeColor(el.colorHex)
              val lum = (0.299f * Color.red(raw) + 0.587f * Color.green(raw) + 0.114f * Color.blue(raw)) / 255f
              if (lum > 0.45f) Color.parseColor("#17181C") else raw
            }
            DesignerFrameStyle.POLARITY_CARD -> {
              val palette = computePolarityCardPalette(el.polarityVariantIndex)
              if (palette.isMidnightNavy && el.hierarchyLevel == HierarchyLevel.DOMINANT) {
                parseSafeColor(palette.accentHex)
              } else {
                parseSafeColor(palette.inkHex)
              }
            }
            else -> parseSafeColor(el.colorHex)
          }

        val paint =
          TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            this.color = resolvedTextColor
            this.isUnderlineText = el.isUnderlined
            if (el.isItalic && el.fontFamilyId !in setOf("caveat", "pacifico")) {
              textSkewX = -0.18f
            }
            if (el.surfaceStyle == TextSurfaceStyle.CONDENSED_EDITORIAL) {
              textScaleX = 0.82f
              letterSpacing = if (el.letterSpacingEm != 0f) el.letterSpacingEm else -0.025f
            } else if (el.letterSpacingEm != 0f) {
              letterSpacing = el.letterSpacingEm
            }
          }

        // Smoothly auto-scale font size so no single word or oversized multi-word phrase overflows
        // horizontally or wraps awkwardly mid-word.
        val tokensForFit = textStr.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val widestWordPx = tokensForFit.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(textStr)
        if (widestWordPx > perElementMaxLineW) {
          val ratio = (perElementMaxLineW / widestWordPx).coerceIn(0.40f, 1f)
          textSizePx *= ratio
          paint.textSize = textSizePx
        }
        // Also prevent excessive multi-line wrapping (>2 lines for dominant, >3 for supporting; 1 line on curves)
        val maxAllowedLines =
          when {
            comp.spatialMode == SpatialLayoutMode.CURVED_ARC_PATH -> 1
            el.hierarchyLevel == HierarchyLevel.DOMINANT -> 2
            else -> 3
          }
        var fitAttempts = 0
        while (fitAttempts < 4 && wrapLines(textStr, paint, perElementMaxLineW).size > maxAllowedLines) {
          textSizePx = (textSizePx * 0.86f).coerceAtLeast(11f * baseScale)
          paint.textSize = textSizePx
          fitAttempts++
        }

        val isPaperOrPolarityInk =
          el.designerFrame == DesignerFrameStyle.PAPER_CUTOUT_PILL ||
            el.designerFrame == DesignerFrameStyle.POLARITY_CARD

        if (isPaperOrPolarityInk) {
          // Crisp flat ink on warm paper cutout or polarity cards (shadow lives on the card itself)
          paint.clearShadowLayer()
        } else if (el.surfaceStyle == TextSurfaceStyle.CONDENSED_EDITORIAL) {
          // Soft contact shadow directly under tall condensed editorial lettering
          val contactRadius = (4.2f * baseScale * shadowMultiplier).coerceAtLeast(1f)
          val contactAlpha =
            (182f * el.shadowIntensity.coerceIn(0.4f, 1.35f)).toInt().coerceIn(80, 230)
          paint.setShadowLayer(
            contactRadius,
            0f,
            2.6f * baseScale,
            Color.argb(contactAlpha, 8, 8, 12),
          )
        } else if (el.shadowEnabled && el.shadowIntensity > 0.02f) {
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
          el.glowColorHex?.takeIf { it.isNotBlank() && !isPaperOrPolarityInk }?.let { glowHex ->
            TextPaint(paint).apply {
              color = parseSafeColor(glowHex)
              clearShadowLayer()
              val blurRadius = (10f * baseScale * el.glowIntensity.coerceIn(0.3f, 2.2f)).coerceAtLeast(1f)
              maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.OUTER)
            }
          }

        val strokePaint =
          if (el.strokeWidthFactor > 0.05f && !isPaperOrPolarityInk) {
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

        val karaokeHighlightPaint =
          if (el.karaokeMode != KaraokeHighlightMode.OFF) {
            TextPaint(paint).apply {
              color = parseSafeColor(el.karaokeHighlightColorHex)
            }
          } else {
            null
          }

        val karaokePillPaint =
          if (el.karaokeMode == KaraokeHighlightMode.PILL_SWEEP ||
              el.karaokeMode == KaraokeHighlightMode.UNDERLINE_SWEEP
          ) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
              style = Paint.Style.FILL
              color = parseSafeColor(el.karaokePillColorHex ?: el.karaokeHighlightColorHex)
            }
          } else {
            null
          }

        val baseSurfaceBundle =
          when (el.surfaceStyle) {
            TextSurfaceStyle.HALATION_BLOOM -> {
              val bloomStrength = el.glowIntensity.coerceIn(0.4f, 2.0f)
              val outerBlur = (13.5f * baseScale * bloomStrength).coerceAtLeast(2f)
              val innerBlur = (6.0f * baseScale * bloomStrength).coerceAtLeast(1.5f)
              val steps = HALATION_VOICE_FILTER_STEPS
              val outerFilters =
                List(steps) { idx ->
                  val energy = idx.toFloat() / (steps - 1).toFloat()
                  val mult = computeHalationVoiceRadiusMultiplier(energy)
                  BlurMaskFilter((outerBlur * mult).coerceAtLeast(2f), BlurMaskFilter.Blur.OUTER)
                }
              val innerFilters =
                List(steps) { idx ->
                  val energy = idx.toFloat() / (steps - 1).toFloat()
                  val mult = computeHalationVoiceRadiusMultiplier(energy)
                  BlurMaskFilter((innerBlur * mult).coerceAtLeast(1.5f), BlurMaskFilter.Blur.NORMAL)
                }
              SurfacePaintBundle(
                halationOuterPaint =
                  TextPaint(paint).apply {
                    clearShadowLayer()
                    color = Color.rgb(255, 198, 118)
                    maskFilter = outerFilters.first()
                  },
                halationInnerPaint =
                  TextPaint(paint).apply {
                    clearShadowLayer()
                    color = Color.rgb(255, 250, 240)
                    maskFilter = innerFilters.first()
                  },
                halationOuterFilters = outerFilters,
                halationInnerFilters = innerFilters,
              )
            }
            TextSurfaceStyle.INFLATED_3D -> {
              SurfacePaintBundle(
                inflatedDropPaint =
                  TextPaint(paint).apply {
                    clearShadowLayer()
                    color = Color.rgb(42, 18, 6)
                    style = Paint.Style.FILL_AND_STROKE
                    strokeWidth = (1.6f * baseScale).coerceAtLeast(1f)
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                  },
                inflatedBevelPaint =
                  TextPaint(paint).apply {
                    clearShadowLayer()
                    color = Color.rgb(255, 168, 36)
                    maskFilter = BlurMaskFilter((1.6f * baseScale).coerceAtLeast(1f), BlurMaskFilter.Blur.SOLID)
                  },
                inflatedSpecularPaint =
                  TextPaint(paint).apply {
                    clearShadowLayer()
                    color = Color.rgb(255, 244, 178)
                    maskFilter = BlurMaskFilter((1.1f * baseScale).coerceAtLeast(0.8f), BlurMaskFilter.Blur.NORMAL)
                  },
              )
            }
            TextSurfaceStyle.FROSTED_GLASS -> {
              SurfacePaintBundle(
                frostedFillPaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(158, 15, 18, 28)
                  },
                frostedRimPaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = Color.argb(156, 255, 255, 255)
                    strokeWidth = (1.5f * baseScale).coerceAtLeast(1f)
                  },
              )
            }
            TextSurfaceStyle.STANDARD,
            TextSurfaceStyle.CONDENSED_EDITORIAL -> SurfacePaintBundle()
          }

        val frameFilledBundle =
          when (el.designerFrame) {
            DesignerFrameStyle.NONE -> baseSurfaceBundle
            DesignerFrameStyle.DASHED_SELECTION_BOX ->
              baseSurfaceBundle.copy(
                frameStrokePaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (1.5f * baseScale).coerceAtLeast(1.2f)
                    color = Color.argb(235, 248, 250, 255)
                    pathEffect = DashPathEffect(floatArrayOf(5.5f * baseScale, 3.8f * baseScale), 0f)
                  },
                frameHandleFillPaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.WHITE
                  },
                frameHandleBorderPaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (1.1f * baseScale).coerceAtLeast(1f)
                    color = Color.rgb(24, 28, 38)
                  },
              )
            DesignerFrameStyle.PAPER_CUTOUT_PILL ->
              baseSurfaceBundle.copy(
                frameFillPaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.parseColor(PAPER_CUTOUT_BG_HEX)
                    setShadowLayer(
                      7.5f * baseScale * shadowMultiplier,
                      0f,
                      3.2f * baseScale,
                      Color.argb(132, 12, 12, 18),
                    )
                  },
                frameStrokePaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (1.0f * baseScale).coerceAtLeast(0.8f)
                    color = Color.argb(64, 34, 30, 22)
                  },
              )
            DesignerFrameStyle.POLARITY_CARD -> {
              val palette = computePolarityCardPalette(el.polarityVariantIndex)
              baseSurfaceBundle.copy(
                frameFillPaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = parseSafeColor(palette.fillHex)
                    setShadowLayer(
                      8.5f * baseScale * shadowMultiplier,
                      0f,
                      3.5f * baseScale,
                      Color.argb(138, 6, 10, 18),
                    )
                  },
                frameStrokePaint =
                  Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (1.3f * baseScale).coerceAtLeast(1f)
                    color = parseSafeColor(palette.borderHex)
                  },
                frameAccentPaint =
                  if (palette.isMidnightNavy) {
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                      style = Paint.Style.FILL
                      color = parseSafeColor(palette.accentHex)
                    }
                  } else {
                    null
                  },
              )
            }
          }

        val surfaceBundle =
          if (el.flourish != VectorFlourish.NONE) {
            val flourishColor =
              when {
                el.designerFrame == DesignerFrameStyle.PAPER_CUTOUT_PILL -> Color.parseColor("#E85D4A")
                el.designerFrame == DesignerFrameStyle.POLARITY_CARD -> {
                  val palette = computePolarityCardPalette(el.polarityVariantIndex)
                  parseSafeColor(palette.accentHex)
                }
                !el.glowColorHex.isNullOrBlank() -> parseSafeColor(el.glowColorHex)
                else -> Color.parseColor("#FFB81C")
              }
            frameFilledBundle.copy(
              flourishPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                  style = Paint.Style.STROKE
                  strokeCap = Paint.Cap.ROUND
                  strokeJoin = Paint.Join.ROUND
                  strokeWidth = (2.5f * baseScale).coerceAtLeast(1.8f)
                  color = flourishColor
                },
            )
          } else {
            frameFilledBundle
          }

        val wrappedLines =
          if (comp.spatialMode == SpatialLayoutMode.CURVED_ARC_PATH) {
            listOf(textStr.trim().replace(Regex("\\s+"), " "))
          } else {
            wrapLines(textStr, paint, perElementMaxLineW)
          }
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
              val tokW = paint.measureText(tok)
              wordSpans.add(
                MeasuredWordSpan(
                  text = tok,
                  startXInLine = xAcc,
                  widthPx = tokW,
                  wordIndexInComp = mappedIdx,
                  globalWordOrder = globalWordOffset,
                )
              )
              xAcc += tokW + if (tIdx < tokens.lastIndex) spaceW else 0f
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
          PaintBundle(
            fill = paint,
            glow = glowPaint,
            stroke = strokePaint,
            background = bgPaint,
            karaokeHighlight = karaokeHighlightPaint,
            karaokePill = karaokePillPaint,
            surface = surfaceBundle,
            lineSpacingFactor = lineSpacingFactor,
          ),
          Triple(measuredLines, blockW, blockH),
        )
      }

    var yCursor = 0f
    var maxTotalRowW = 0f
    val anchorXPx = comp.anchorXFraction * canvasWidth

    // Subject-aware LEFT_RIGHT_SPLIT layout: anchor first half to the left of the center subject
    // zone and second half to the right on the exact same horizontal plane.
    if (comp.spatialMode == SpatialLayoutMode.LEFT_RIGHT_SPLIT && elementSizes.size >= 2) {
      val splitIdx = (elementSizes.size + 1) / 2
      val leftGroup = elementSizes.subList(0, splitIdx)
      val rightGroup = elementSizes.subList(splitIdx, elementSizes.size)
      val inlineGapPx = 6f * baseScale

      val leftGroupW =
        leftGroup.sumOf { it.third.second.toDouble() }.toFloat() +
          max(0, leftGroup.size - 1) * inlineGapPx
      val rightGroupW =
        rightGroup.sumOf { it.third.second.toDouble() }.toFloat() +
          max(0, rightGroup.size - 1) * inlineGapPx
      val planeHeight = elementSizes.maxOfOrNull { it.third.third } ?: 1f

      val (leftAnchorX, rightAnchorX) =
        computeLeftRightSplitX(
          canvasWidth = canvasWidth.toFloat(),
          leftWidthPx = leftGroupW,
          rightWidthPx = rightGroupW,
        )

      var leftCursor = leftAnchorX - leftGroupW / 2f
      leftGroup.forEach { (el, paints, metrics) ->
        val (lines, blockW, blockH) = metrics
        val itemCenterX = leftCursor + blockW / 2f
        leftCursor += blockW + inlineGapPx
        val baselineAlignedY =
          (planeHeight - blockH) * 0.72f + (el.offsetYEm * paints.fill.textSize)
        measuredRaw.add(
          MeasuredElement(
            element = el,
            paint = paints.fill,
            glowPaint = paints.glow,
            strokePaint = paints.stroke,
            backgroundPaint = paints.background,
            karaokeHighlightPaint = paints.karaokeHighlight,
            karaokePillPaint = paints.karaokePill,
            surfacePaints = paints.surface,
            totalWordsInElement = lines.sumOf { it.words.size }.coerceAtLeast(1),
            lines = lines,
            blockWidthPx = blockW,
            blockHeightPx = blockH,
            centerOffsetXFromAnchorPx = (itemCenterX - anchorXPx) + (el.offsetXEm * paints.fill.textSize),
            topYOffsetPx = max(0f, baselineAlignedY),
            lineSpacingFactor = paints.lineSpacingFactor,
          )
        )
      }

      var rightCursor = rightAnchorX - rightGroupW / 2f
      rightGroup.forEach { (el, paints, metrics) ->
        val (lines, blockW, blockH) = metrics
        val itemCenterX = rightCursor + blockW / 2f
        rightCursor += blockW + inlineGapPx
        val baselineAlignedY =
          (planeHeight - blockH) * 0.72f + (el.offsetYEm * paints.fill.textSize)
        measuredRaw.add(
          MeasuredElement(
            element = el,
            paint = paints.fill,
            glowPaint = paints.glow,
            strokePaint = paints.stroke,
            backgroundPaint = paints.background,
            karaokeHighlightPaint = paints.karaokeHighlight,
            karaokePillPaint = paints.karaokePill,
            surfacePaints = paints.surface,
            totalWordsInElement = lines.sumOf { it.words.size }.coerceAtLeast(1),
            lines = lines,
            blockWidthPx = blockW,
            blockHeightPx = blockH,
            centerOffsetXFromAnchorPx = (itemCenterX - anchorXPx) + (el.offsetXEm * paints.fill.textSize),
            topYOffsetPx = max(0f, baselineAlignedY),
            lineSpacingFactor = paints.lineSpacingFactor,
          )
        )
      }
      yCursor = planeHeight
      maxTotalRowW = (rightAnchorX + rightGroupW / 2f) - (leftAnchorX - leftGroupW / 2f)
    } else {
      // Group elements by inlineRowGroup so inline tokens ("and" + "keeps", "click" + "Dynamic Captions")
      // lay out horizontally side-by-side while stacked rows stack cleanly vertically!
      val groupedRows = elementSizes.groupBy { it.first.inlineRowGroup }.toSortedMap()
      val rowGapDefault = 3f * baseScale
      val inlineGapPx = 7f * baseScale

      groupedRows.values.forEachIndexed { rowIdx, rowItems ->
        if (rowIdx > 0) yCursor += rowGapDefault

        val rowHeight = rowItems.maxOfOrNull { it.third.third } ?: 1f
        val rawRowTotalWidth =
          rowItems.sumOf { it.third.second.toDouble() }.toFloat() +
            max(0, rowItems.size - 1) * inlineGapPx

        // If an inline side-by-side row exceeds safe horizontal width, scale its items uniformly to fit cleanly
        val effectiveRowItems =
          if (rowItems.size > 1 && rawRowTotalWidth > maxLineWidthPx) {
            val scaleFactor = (maxLineWidthPx / rawRowTotalWidth).coerceIn(0.50f, 1f)
            rowItems.map { (el, paints, _) ->
              val newSize = (paints.fill.textSize * scaleFactor).coerceAtLeast(10f * baseScale)
              paints.fill.textSize = newSize
              paints.glow?.textSize = newSize
              paints.stroke?.textSize = newSize
              paints.karaokeHighlight?.textSize = newSize
              paints.surface.updateTextSize(newSize)
              val textStr = if (el.uppercase) el.text.uppercase() else el.text
              val singleLineText = textStr.trim().replace(Regex("\\s+"), " ")
              val fm = paints.fill.fontMetrics
              val singleW = paints.fill.measureText(singleLineText)
              val tokens = singleLineText.split(" ").filter { it.isNotEmpty() }
              val wordSpans = mutableListOf<MeasuredWordSpan>()
              var xAcc = 0f
              val spaceW = paints.fill.measureText(" ")
              tokens.forEachIndexed { tIdx, tok ->
                val tokW = paints.fill.measureText(tok)
                wordSpans.add(
                  MeasuredWordSpan(
                    text = tok,
                    startXInLine = xAcc,
                    widthPx = tokW,
                    wordIndexInComp = el.wordIndices.getOrNull(tIdx),
                    globalWordOrder = tIdx,
                  )
                )
                xAcc += tokW + if (tIdx < tokens.lastIndex) spaceW else 0f
              }
              val line = MeasuredLine(singleLineText, singleW, fm.ascent, fm.descent, wordSpans)
              val newH = (line.heightPx * paints.lineSpacingFactor).coerceAtLeast(1f)
              Triple(el, paints, Triple(listOf(line), singleW, newH))
            }
          } else {
            rowItems
          }

        val finalRowHeight = effectiveRowItems.maxOfOrNull { it.third.third } ?: rowHeight
        val rowTotalWidth =
          effectiveRowItems.sumOf { it.third.second.toDouble() }.toFloat() +
            max(0, effectiveRowItems.size - 1) * inlineGapPx
        maxTotalRowW = max(maxTotalRowW, rowTotalWidth)

        var rowXCursor = -rowTotalWidth / 2f
        effectiveRowItems.forEach { (el, paints, metrics) ->
          val (lines, blockW, blockH) = metrics
          val baselineAlignedY =
            yCursor + (finalRowHeight - blockH) * 0.72f + (el.offsetYEm * paints.fill.textSize)
          val centerOffsetX =
            if (effectiveRowItems.size > 1) {
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
              karaokeHighlightPaint = paints.karaokeHighlight,
              karaokePillPaint = paints.karaokePill,
              surfacePaints = paints.surface,
              totalWordsInElement = lines.sumOf { it.words.size }.coerceAtLeast(1),
              lines = lines,
              blockWidthPx = blockW,
              blockHeightPx = blockH,
              centerOffsetXFromAnchorPx = centerOffsetX,
              topYOffsetPx = max(0f, baselineAlignedY),
              lineSpacingFactor = paints.lineSpacingFactor,
            )
          )
        }
        yCursor += finalRowHeight
      }
    }

    // Refine left/right stack X alignment cleanly when multi-row LEFT/RIGHT stacked block
    val allLeft =
      comp.spatialMode == SpatialLayoutMode.STANDARD &&
        measuredRaw.all { it.element.alignment == TextAlignment.LEFT }
    val allRight =
      comp.spatialMode == SpatialLayoutMode.STANDARD &&
        measuredRaw.all { it.element.alignment == TextAlignment.RIGHT }
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
        anchorX = anchorXPx,
        anchorY = comp.anchorYFraction * canvasHeight,
      )
    layoutCache[sig] = computed
    lastFastEntry = FastCacheEntry(comp, canvasWidth, canvasHeight, shadowMultiplier, computed)
    return computed
  }

  private fun splitSingleElementForLeftRight(el: CompositionElement): List<CompositionElement> {
    val words = el.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size !in 2..4) return listOf(el)
    val bestSplit =
      (1 until words.size).minByOrNull { k ->
        val lLen = words.subList(0, k).joinToString(" ").length
        val rLen = words.subList(k, words.size).joinToString(" ").length
        abs(lLen - rLen)
      } ?: (words.size / 2)
    val leftWords = words.subList(0, bestSplit)
    val rightWords = words.subList(bestSplit, words.size)
    val leftIndices = leftWords.indices.mapNotNull { el.wordIndices.getOrNull(it) }
    val rightIndices = (bestSplit until words.size).mapNotNull { el.wordIndices.getOrNull(it) }
    return listOf(
      el.copy(
        id = "${el.id}_left",
        text = leftWords.joinToString(" "),
        wordIndices = leftIndices,
        alignment = TextAlignment.RIGHT,
        inlineRowGroup = 0,
      ),
      el.copy(
        id = "${el.id}_right",
        text = rightWords.joinToString(" "),
        wordIndices = rightIndices,
        alignment = TextAlignment.LEFT,
        inlineRowGroup = 0,
      ),
    )
  }

  private fun wrapLines(text: String, paint: Paint, maxWidthPx: Float): List<String> {
    return CompositionValidator.balanceTwoLineText(text, maxWidthPx) { candidate ->
      paint.measureText(candidate)
    }
  }

  private fun applyEasing(t: Float, easing: AnimationEasingType): Float =
    applyEasingStatic(t, easing)

  private fun parseSafeColor(hex: String): Int {
    return try {
      Color.parseColor(hex)
    } catch (_: Exception) {
      Color.WHITE
    }
  }

  data class KaraokeWordState(
    val isActive: Boolean,
    val progress01: Float,
    val isSpokenAlready: Boolean,
  )

  data class StaggerPopState(
    val alpha: Float,
    val scale: Float,
    val offsetYDp: Float,
  )

  data class HandBoilTransform(
    val rotationDeg: Float,
    val scale: Float,
    val offsetXDp: Float,
    val offsetYDp: Float,
  )

  data class PolarityCardPalette(
    val fillHex: String,
    val inkHex: String,
    val accentHex: String,
    val borderHex: String,
    val isMidnightNavy: Boolean,
  )

  companion object {
    const val STAGGER_POP_STEP_MS: Long = 32L
    const val HAND_BOIL_BUCKET_MS: Long = 68L
    const val HALATION_BLOOM_PEAK_MS: Long = 140L
    const val MOTION_BLUR_WINDOW_MS: Long = 70L
    const val FLOURISH_DRAW_DURATION_MS: Long = 180L
    const val PAPER_CUTOUT_BG_HEX: String = "#F5F2EB"
    const val POLARITY_CREAM_FILL_HEX: String = "#EFECE4"
    const val POLARITY_CREAM_INK_HEX: String = "#111318"
    const val POLARITY_NAVY_FILL_HEX: String = "#0E1A2B"
    const val POLARITY_NAVY_INK_HEX: String = "#EFECE4"
    const val POLARITY_GOLD_ACCENT_HEX: String = "#C8A252"
    val MOTION_BLUR_GHOST_ALPHAS: FloatArray = floatArrayOf(0.25f, 0.12f, 0.05f)

    /**
     * Resolves alternating high-contrast warm cream / midnight navy + gold card palette.
     */
    fun computePolarityCardPalette(polarityVariantIndex: Int): PolarityCardPalette {
      val isNavy = Math.floorMod(polarityVariantIndex, 2) == 1
      return if (isNavy) {
        PolarityCardPalette(
          fillHex = POLARITY_NAVY_FILL_HEX,
          inkHex = POLARITY_NAVY_INK_HEX,
          accentHex = POLARITY_GOLD_ACCENT_HEX,
          borderHex = POLARITY_GOLD_ACCENT_HEX,
          isMidnightNavy = true,
        )
      } else {
        PolarityCardPalette(
          fillHex = POLARITY_CREAM_FILL_HEX,
          inkHex = POLARITY_CREAM_INK_HEX,
          accentHex = POLARITY_CREAM_INK_HEX,
          borderHex = "#D6D1C4",
          isMidnightNavy = false,
        )
      }
    }

    /**
     * Computes subject-aware left & right center X coordinates flanking the center subject zone
     * ([0.38..0.62] corridor) on the exact same horizontal plane.
     */
    fun computeLeftRightSplitX(
      canvasWidth: Float,
      leftWidthPx: Float,
      rightWidthPx: Float,
    ): Pair<Float, Float> {
      val margin = canvasWidth * 0.06f
      val leftMaxCenter = canvasWidth * 0.37f - leftWidthPx / 2f
      val leftMinCenter = margin + leftWidthPx / 2f
      val leftCenterX =
        if (leftMaxCenter >= leftMinCenter) {
          (canvasWidth * 0.22f).coerceIn(leftMinCenter, leftMaxCenter)
        } else {
          leftMinCenter
        }

      val rightMinCenter = canvasWidth * 0.63f + rightWidthPx / 2f
      val rightMaxCenter = canvasWidth - margin - rightWidthPx / 2f
      val rightCenterX =
        if (rightMaxCenter >= rightMinCenter) {
          (canvasWidth * 0.78f).coerceIn(rightMinCenter, rightMaxCenter)
        } else {
          rightMaxCenter
        }

      return leftCenterX to rightCenterX
    }

    /**
     * Animates procedural PathMeasure flourishes cleanly from 0f to 1f over the first 180ms.
     */
    fun computeFlourishProgress(elapsedMs: Float): Float {
      if (elapsedMs <= 0f) return 0f
      val raw = (elapsedMs / FLOURISH_DRAW_DURATION_MS.toFloat()).coerceIn(0f, 1f)
      return 1f - (1f - raw).pow(3)
    }

    fun applyEasingStatic(t: Float, easing: AnimationEasingType): Float {
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

    /**
     * Computes deterministic per-character stagger pop entry state (`charIndex * 32L` ms delay).
     */
    fun computeStaggerPopState(
      elementElapsedMs: Long,
      charIndex: Int,
    ): StaggerPopState {
      val charElapsed = elementElapsedMs - charIndex * STAGGER_POP_STEP_MS
      if (charElapsed < 0L) {
        return StaggerPopState(alpha = 0f, scale = 0.52f, offsetYDp = 5.5f)
      }
      val t = (charElapsed.toFloat() / 150f).coerceIn(0f, 1f)
      val alpha = (t * 2.2f).coerceIn(0f, 1f)
      val overshoot = applyEasingStatic(t, AnimationEasingType.SPRING_OVERSHOOT)
      val scale = 0.52f + 0.48f * overshoot
      val offsetYDp = (1f - t).pow(2) * 5.5f
      return StaggerPopState(alpha = alpha, scale = scale, offsetYDp = offsetYDp)
    }

    /**
     * Computes deterministic beat-synced character reveal alpha across a word's active duration.
     */
    fun computeCharRevealAlpha(
      positionMs: Long,
      wordStartMs: Long,
      wordEndMs: Long,
      charIndexInWord: Int,
      totalCharsInWord: Int,
    ): Float {
      val count = totalCharsInWord.coerceAtLeast(1)
      if (positionMs < wordStartMs) return 0f
      if (positionMs >= wordEndMs) return 1f
      val dur = max(40L, wordEndMs - wordStartMs).toFloat()
      val progress01 = ((positionMs - wordStartMs).toFloat() / dur).coerceIn(0f, 1f)
      val frontier = progress01 * count.toFloat() - charIndexInWord.toFloat()
      if (frontier <= 0f) return 0f
      return (frontier * 2.4f).coerceIn(0f, 1f)
    }

    /**
     * Quantizes [positionMs] into 68ms (~14.7fps) stepped buckets and computes deterministic
     * hash-based per-character micro-jitter (±1.8° rotation, ±0.03 scale, ±1.5dp X/Y offset).
     */
    fun computeHandBoilTransform(
      positionMs: Long,
      charIndex: Int,
      elementSeed: Int = 0,
    ): HandBoilTransform {
      val bucket = Math.floorDiv(positionMs, HAND_BOIL_BUCKET_MS)
      val rotUnit = deterministicSignedUnit(bucket, charIndex, elementSeed, channel = 1)
      val scaleUnit = deterministicSignedUnit(bucket, charIndex, elementSeed, channel = 2)
      val xUnit = deterministicSignedUnit(bucket, charIndex, elementSeed, channel = 3)
      val yUnit = deterministicSignedUnit(bucket, charIndex, elementSeed, channel = 4)
      return HandBoilTransform(
        rotationDeg = rotUnit * 1.8f,
        scale = 1f + scaleUnit * 0.03f,
        offsetXDp = xUnit * 1.5f,
        offsetYDp = yUnit * 1.5f,
      )
    }

    /**
     * Returns normalized [0f..1f] halation entry flash intensity: peaks brightly during the first
     * 140ms of word entry and settles smoothly to 0f (ambient halo) afterward.
     */
    fun computeHalationBloomBoost(wordEntryElapsedMs: Float): Float {
      if (wordEntryElapsedMs < 0f || wordEntryElapsedMs > HALATION_BLOOM_PEAK_MS.toFloat()) {
        return 0f
      }
      val t = (wordEntryElapsedMs / HALATION_BLOOM_PEAK_MS.toFloat()).coerceIn(0f, 1f)
      return (1f - t).pow(1.6f)
    }

    private fun deterministicSignedUnit(
      bucket: Long,
      charIndex: Int,
      elementSeed: Int,
      channel: Int,
    ): Float {
      var h = (bucket xor (bucket ushr 32)).toInt()
      h = h xor (charIndex * 0x27d4eb2d)
      h = h xor (elementSeed * 0x165667b1)
      h = h xor (channel * 0x9e3779b9.toInt())
      h = h xor (h ushr 16)
      h *= 0x85ebca6b.toInt()
      h = h xor (h ushr 13)
      h *= 0xc2b2ae35.toInt()
      h = h xor (h ushr 16)
      val normalized01 = (h ushr 8).toFloat() / 16777215f // [0f, 1f]
      return (normalized01 * 2f - 1f).coerceIn(-1f, 1f)
    }

    fun resolveWordTimeWindow(
      comp: CaptionComposition,
      globalWordOrder: Int,
      mappedWordIndex: Int?,
      totalWordsInElement: Int,
    ): Pair<Long, Long> {
      val exactTiming =
        mappedWordIndex?.let { comp.wordMappings.getOrNull(it) }
          ?: comp.wordMappings.getOrNull(globalWordOrder)
      if (exactTiming != null && exactTiming.endMs > exactTiming.startMs) {
        return exactTiming.startMs to exactTiming.endMs
      }
      val count = totalWordsInElement.coerceAtLeast(1)
      val dur = (comp.endMs - comp.startMs).coerceAtLeast(120L)
      val slice = dur.toFloat() / count.toFloat()
      val wStart = comp.startMs + (slice * globalWordOrder).toLong()
      val wEnd =
        if (globalWordOrder >= count - 1) comp.endMs
        else max(wStart + 60L, comp.startMs + (slice * (globalWordOrder + 1)).toLong())
      return wStart to wEnd
    }

    /**
     * Resolves millisecond-exact active-word karaoke state.
     * Uses exact [CaptionComposition.wordMappings] when present, or smoothly interpolates
     * proportional slices across [comp.startMs..comp.endMs] so karaoke works even on manual scripts.
     */
    fun resolveActiveWordTiming(
      comp: CaptionComposition,
      globalWordOrder: Int,
      mappedWordIndex: Int?,
      totalWordsInElement: Int,
      positionMs: Long,
    ): KaraokeWordState {
      val (wStart, wEnd) =
        resolveWordTimeWindow(
          comp = comp,
          globalWordOrder = globalWordOrder,
          mappedWordIndex = mappedWordIndex,
          totalWordsInElement = totalWordsInElement,
        )
      val isActive = positionMs in wStart..wEnd
      val progress =
        if (isActive) {
          ((positionMs - wStart).toFloat() / max(40L, wEnd - wStart).toFloat()).coerceIn(0f, 1f)
        } else if (positionMs > wEnd) {
          1f
        } else {
          0f
        }
      return KaraokeWordState(
        isActive = isActive,
        progress01 = progress,
        isSpokenAlready = positionMs > wEnd,
      )
    }

    private const val HALATION_VOICE_FILTER_STEPS = 10
    const val PARALLAX_OFFSET_DAMPING = 0.22f
    const val PARALLAX_TILT_DAMPING = 0.25f
    const val PARALLAX_MAX_OFFSET_X = 0.065f
    const val PARALLAX_MAX_OFFSET_Y = 0.055f
    const val PARALLAX_MAX_TILT_DEG = 6.5f
    const val VOICE_BREATHING_MAX_SCALE_BOOST = 0.055f
    const val HALATION_VOICE_MAX_RADIUS_BOOST = 0.55f

    data class FaceParallaxTransform(
      val offsetXFraction: Float = 0f,
      val offsetYFraction: Float = 0f,
      val tiltDeg: Float = 0f,
    ) {
      companion object {
        val ZERO = FaceParallaxTransform()
      }
    }

    /**
     * Smoothly interpolates [FaceAnchorSample]s at [timeMs] in O(log N) time using binary search.
     * Assumes [samples] are sorted chronologically by [FaceAnchorSample.timestampMs] (clamps cleanly at endpoints).
     */
    fun interpolateFaceAnchor(
      samples: List<FaceAnchorSample>,
      timeMs: Long,
    ): FaceAnchorSample? {
      if (samples.isEmpty()) return null
      if (samples.size == 1) return samples.first()
      if (timeMs <= samples.first().timestampMs) return samples.first()
      if (timeMs >= samples.last().timestampMs) return samples.last()

      var lo = 0
      var hi = samples.lastIndex
      while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val midTs = samples[mid].timestampMs
        when {
          midTs == timeMs -> return samples[mid]
          midTs < timeMs -> lo = mid + 1
          else -> hi = mid - 1
        }
      }
      val rightIdx = lo.coerceIn(1, samples.lastIndex)
      val leftIdx = rightIdx - 1
      val a = samples[leftIdx]
      val b = samples[rightIdx]
      val span = (b.timestampMs - a.timestampMs).coerceAtLeast(1L)
      val t = ((timeMs - a.timestampMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
      return FaceAnchorSample(
        timestampMs = timeMs,
        centerX = a.centerX + (b.centerX - a.centerX) * t,
        centerY = a.centerY + (b.centerY - a.centerY) * t,
        headEulerAngleZ = a.headEulerAngleZ + (b.headEulerAngleZ - a.headEulerAngleZ) * t,
      )
    }

    /**
     * Computes damped scene-anchored head-parallax micro-tracking (~22% head offset, ~25% roll tilt)
     * clamped to safe margins so [SpatialLayoutMode.CURVED_ARC_PATH] and [SpatialLayoutMode.LEFT_RIGHT_SPLIT]
     * typography feels physically anchored in the 3D scene without drifting off-screen.
     */
    fun computeHeadParallaxTransform(
      samples: List<FaceAnchorSample>,
      timeMs: Long,
      neutralCenterX: Float = 0.50f,
      neutralCenterY: Float = 0.38f,
    ): FaceParallaxTransform {
      val anchor = interpolateFaceAnchor(samples, timeMs) ?: return FaceParallaxTransform.ZERO
      val dx =
        ((anchor.centerX - neutralCenterX) * PARALLAX_OFFSET_DAMPING)
          .coerceIn(-PARALLAX_MAX_OFFSET_X, PARALLAX_MAX_OFFSET_X)
      val dy =
        ((anchor.centerY - neutralCenterY) * PARALLAX_OFFSET_DAMPING)
          .coerceIn(-PARALLAX_MAX_OFFSET_Y, PARALLAX_MAX_OFFSET_Y)
      val tilt =
        (anchor.headEulerAngleZ * PARALLAX_TILT_DAMPING)
          .coerceIn(-PARALLAX_MAX_TILT_DEG, PARALLAX_MAX_TILT_DEG)
      return FaceParallaxTransform(
        offsetXFraction = dx,
        offsetYFraction = dy,
        tiltDeg = tilt,
      )
    }

    /**
     * Computes vocal RMS voice-breathing scale multiplier (`1.000f` silent .. `1.055f` peak speech).
     */
    fun computeVoiceBreathingScale(energy01: Float): Float =
      1f + energy01.coerceIn(0f, 1f) * VOICE_BREATHING_MAX_SCALE_BOOST

    /**
     * Computes halation bloom blur-radius multiplier driven by live vocal RMS (`1.00f` .. `1.55f`).
     */
    fun computeHalationVoiceRadiusMultiplier(energy01: Float): Float =
      1f + energy01.coerceIn(0f, 1f) * HALATION_VOICE_MAX_RADIUS_BOOST
  }
}

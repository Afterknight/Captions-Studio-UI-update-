package com.example.captionstudio.ui.components

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.captionstudio.domain.model.AspectRatioOption
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.KeepSegment
import com.example.captionstudio.domain.model.PreviewQualityMode
import com.example.captionstudio.domain.model.SafeZonePlatform
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.engine.KineticCompositionRenderer
import com.example.captionstudio.media.AudioExtractionService
import com.example.captionstudio.media.SubjectMaskProcessor
import com.example.captionstudio.presets.PresetCatalog
import com.example.ui.theme.CaptionRed
import com.example.ui.theme.CaptionYellow
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class MagneticSnapResult(
  val x: Float,
  val y: Float,
  val snappedVerticalX: Float? = null,
  val snappedHorizontalY: Float? = null,
  val snapLabel: String? = null,
)

/**
 * Computes magnetic snapping for direct on-canvas caption dragging against center axes,
 * upper/lower safe-zone anchors, and platform-specific social UI boundaries.
 */
fun computeMagneticSnap(
  rawX: Float,
  rawY: Float,
  safeZonePlatform: SafeZonePlatform = SafeZonePlatform.NONE,
  threshold: Float = 0.028f,
): MagneticSnapResult {
  val clampedX = rawX.coerceIn(0.16f, 0.84f)
  val clampedY = rawY.coerceIn(0.10f, 0.90f)

  val verticalTargets =
    buildList {
      add(0.50f to "Center")
      add(0.27f to "Left Safe")
      add(0.73f to "Right Safe")
      if (safeZonePlatform == SafeZonePlatform.TIKTOK ||
          safeZonePlatform == SafeZonePlatform.REELS ||
          safeZonePlatform == SafeZonePlatform.SHORTS
      ) {
        add(0.42f to "${safeZonePlatform.shortLabel} Safe X")
      }
    }
  val horizontalTargets =
    buildList {
      add(0.50f to "Middle")
      add(0.24f to "Upper Safe")
      add(0.78f to "Lower Safe")
      when (safeZonePlatform) {
        SafeZonePlatform.TIKTOK -> add(0.74f to "TikTok Safe")
        SafeZonePlatform.REELS -> add(0.73f to "Reels Safe")
        SafeZonePlatform.SHORTS -> add(0.76f to "Shorts Safe")
        else -> Unit
      }
    }

  val bestX = verticalTargets.minByOrNull { abs(clampedX - it.first) }
  val bestY = horizontalTargets.minByOrNull { abs(clampedY - it.first) }

  val snappedX = bestX?.takeIf { abs(clampedX - it.first) <= threshold }
  val snappedY = bestY?.takeIf { abs(clampedY - it.first) <= threshold }

  val finalX = snappedX?.first ?: clampedX
  val finalY = snappedY?.first ?: clampedY
  val label =
    when {
      snappedX != null && snappedY != null -> "${snappedX.second} • ${snappedY.second}"
      snappedX != null -> snappedX.second
      snappedY != null -> snappedY.second
      else -> null
    }

  return MagneticSnapResult(
    x = finalX,
    y = finalY,
    snappedVerticalX = snappedX?.first,
    snappedHorizontalY = snappedY?.first,
    snapLabel = label,
  )
}

@OptIn(UnstableApi::class)
@Composable
fun KineticPreviewStage(
  videoUriString: String,
  durationMs: Long,
  aspectRatio: AspectRatioOption,
  presetId: String,
  compositions: List<CaptionComposition>,
  analysisReport: VideoAnalysisReport,
  selectedCompositionId: String?,
  playheadMs: Long,
  isPlaying: Boolean,
  isMuted: Boolean = false,
  playbackSpeed: Float = 1.0f,
  repeatLoop: Boolean = true,
  previewQuality: PreviewQualityMode,
  showSafeGuides: Boolean,
  safeZonePlatform: SafeZonePlatform = if (showSafeGuides) SafeZonePlatform.GRID else SafeZonePlatform.NONE,
  onPlayheadUpdate: (Long) -> Unit,
  onPlayingChanged: (Boolean) -> Unit,
  onDragCompositionAnchor: (compId: String, anchorX: Float, anchorY: Float) -> Unit,
  onPinchScaleComposition: (compId: String, scaleMultiplier: Float, recordHistory: Boolean) -> Unit = { _, _, _ -> },
  modifier: Modifier = Modifier,
  keepSegments: List<KeepSegment> = emptyList(),
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val renderer = remember(context) { KineticCompositionRenderer(context) }
  val preset = remember(presetId) { PresetCatalog.findById(presetId) }

  val isLowRamDevice =
    remember(context) {
      (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
  val effectivePerformanceMode = previewQuality == PreviewQualityMode.PERFORMANCE || isLowRamDevice

  // Local 60fps draw-phase clock isolates high-frequency Canvas invalidation from EditorScreen recompositions
  var localPlayheadMs by remember { mutableLongStateOf(playheadMs) }
  LaunchedEffect(playheadMs, isPlaying) {
    if (!isPlaying) {
      localPlayheadMs = playheadMs
    } else if (abs(localPlayheadMs - playheadMs) > 220L) {
      localPlayheadMs = playheadMs
    }
  }

  val latestPlayhead by rememberUpdatedState(playheadMs)
  val latestCompositions by rememberUpdatedState(compositions)
  val latestSelectedId by rememberUpdatedState(selectedCompositionId)
  val latestIsPlaying by rememberUpdatedState(isPlaying)
  val latestOnPlayingChanged by rememberUpdatedState(onPlayingChanged)
  val latestOnPlayheadUpdate by rememberUpdatedState(onPlayheadUpdate)

  var isBuffering by remember(videoUriString) { mutableStateOf(false) }
  var playerError by remember(videoUriString) { mutableStateOf<String?>(null) }

  val hasVideo = videoUriString.isNotBlank()
  var audioEnergyEnvelope by remember(videoUriString) {
    mutableStateOf(
      com.example.captionstudio.media.AudioExtractionService.getCachedNormalizedEnvelope(videoUriString)
    )
  }
  LaunchedEffect(videoUriString, hasVideo) {
    if (hasVideo && audioEnergyEnvelope == null) {
      val loaded =
        withContext(Dispatchers.IO) {
          runCatching {
            com.example.captionstudio.media.AudioExtractionService.extractNormalizedEnvelope10ms(
              context,
              Uri.parse(videoUriString),
            )
          }.getOrNull()
        }
      if (loaded != null) {
        audioEnergyEnvelope = loaded
      }
    }
  }
  val validKeepSegments =
    remember(keepSegments) { keepSegments.filter { it.sourceEndMs > it.sourceStartMs + 40L } }
  val latestKeepSegments by rememberUpdatedState(validKeepSegments)
  val exoPlayer =
    remember(videoUriString) {
      if (hasVideo) {
        ExoPlayer.Builder(context).build().apply {
          videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
          repeatMode = if (repeatLoop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        }
      } else {
        null
      }
    }

  LaunchedEffect(exoPlayer, videoUriString, validKeepSegments) {
    if (exoPlayer == null || !hasVideo) return@LaunchedEffect
    val parsedUri = Uri.parse(videoUriString)
    val wasPlaying = exoPlayer.playWhenReady
    if (validKeepSegments.isNotEmpty()) {
      val clippedItems =
        validKeepSegments.map { seg ->
          MediaItem.Builder()
            .setUri(parsedUri)
            .setClippingConfiguration(
              MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(seg.sourceStartMs.coerceAtLeast(0L))
                .setEndPositionMs(seg.sourceEndMs)
                .build()
            )
            .build()
        }
      exoPlayer.setMediaItems(clippedItems, true)
    } else {
      exoPlayer.setMediaItem(MediaItem.fromUri(parsedUri))
    }
    exoPlayer.prepare()
    seekPlayerToEditedTime(exoPlayer, latestPlayhead, validKeepSegments)
    exoPlayer.playWhenReady = wasPlaying || latestIsPlaying
  }

  DisposableEffect(exoPlayer) {
    if (exoPlayer == null) {
      onDispose {}
    } else {
      val listener =
        object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
              playerError = null
            } else if (playbackState == Player.STATE_ENDED && !repeatLoop) {
              latestOnPlayingChanged(false)
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            isBuffering = false
            playerError = error.localizedMessage ?: "Unable to decode video preview."
            latestOnPlayingChanged(false)
          }
        }
      exoPlayer.addListener(listener)
      onDispose {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
      }
    }
  }

  // Lifecycle-aware pause when backgrounded or navigating away
  DisposableEffect(lifecycleOwner, exoPlayer) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE,
        Lifecycle.Event.ON_STOP -> {
          exoPlayer?.pause()
          if (latestIsPlaying) latestOnPlayingChanged(false)
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Sync play/pause, mute, speed, and loop mode with ExoPlayer
  LaunchedEffect(exoPlayer, isPlaying) {
    exoPlayer?.playWhenReady = isPlaying
  }

  LaunchedEffect(exoPlayer, isMuted) {
    exoPlayer?.volume = if (isMuted) 0f else 1f
  }

  LaunchedEffect(exoPlayer, playbackSpeed) {
    exoPlayer?.setPlaybackSpeed(playbackSpeed.coerceIn(0.25f, 2.0f))
  }

  LaunchedEffect(exoPlayer, repeatLoop) {
    exoPlayer?.repeatMode = if (repeatLoop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
  }

  // Seek ExoPlayer when user scrubs timeline while paused
  LaunchedEffect(exoPlayer, playheadMs, isPlaying, validKeepSegments) {
    if (exoPlayer != null && !isPlaying) {
      val currentEditedPos = computeEditedPlayheadFromPlayer(exoPlayer, validKeepSegments)
      val diff = abs(currentEditedPos - playheadMs)
      if (diff > 45L) {
        seekPlayerToEditedTime(exoPlayer, playheadMs, validKeepSegments)
      }
    }
  }

  // Smooth 60Hz local Canvas ticker with throttled parent scrubber sync (~13Hz) to eliminate full-screen recomposition jank
  LaunchedEffect(exoPlayer, isPlaying, durationMs, playbackSpeed, effectivePerformanceMode) {
    if (!isPlaying) return@LaunchedEffect
    var lastFrameMs = -1L
    val canvasStepMs = if (effectivePerformanceMode) 28L else 0L
    val parentSyncIntervalMs = if (effectivePerformanceMode) 110L else 75L
    var lastCanvasTickMs = 0L
    var lastParentSyncMs = 0L

    while (isPlaying) {
      withFrameMillis { frameMs ->
        if (exoPlayer != null) {
          if (frameMs - lastCanvasTickMs >= canvasStepMs) {
            lastCanvasTickMs = frameMs
            val pos = computeEditedPlayheadFromPlayer(exoPlayer, latestKeepSegments)
            localPlayheadMs = pos
            if (frameMs - lastParentSyncMs >= parentSyncIntervalMs) {
              lastParentSyncMs = frameMs
              latestOnPlayheadUpdate(pos)
            }
          }
        } else {
          if (lastFrameMs >= 0L) {
            val delta = ((frameMs - lastFrameMs).coerceIn(0L, 100L) * playbackSpeed).toLong()
            val total = durationMs.coerceAtLeast(4000L)
            val next = (localPlayheadMs + delta) % total
            localPlayheadMs = next
            if (frameMs - lastParentSyncMs >= parentSyncIntervalMs) {
              lastParentSyncMs = frameMs
              latestOnPlayheadUpdate(next)
            }
          }
          lastFrameMs = frameMs
        }
      }
    }
    // Sync final exact playhead immediately upon pause
    latestOnPlayheadUpdate(localPlayheadMs)
  }

  val ratio = aspectRatio.ratioWidth / aspectRatio.ratioHeight
  var dragAnchorX by remember { mutableStateOf(0.5f) }
  var dragAnchorY by remember { mutableStateOf(0.7f) }
  var activeSnapState by remember { mutableStateOf<MagneticSnapResult?>(null) }
  var isDraggingCanvas by remember { mutableStateOf(false) }
  var pinchBadgeText by remember { mutableStateOf<String?>(null) }
  val latestSafeZonePlatform by rememberUpdatedState(safeZonePlatform)
  val latestOnDragAnchor by rememberUpdatedState(onDragCompositionAnchor)
  val latestOnPinchScale by rememberUpdatedState(onPinchScaleComposition)
  val playerViewRef = remember(videoUriString) { mutableStateOf<PlayerView?>(null) }
  val cachedTextureViewRef = remember(videoUriString) { mutableStateOf<TextureView?>(null) }
  var activeSubjectMask by remember(videoUriString) { mutableStateOf<Bitmap?>(null) }
  var activeMaskVideoRect by remember(videoUriString) { mutableStateOf<RectF?>(null) }

  val activeComp =
    remember(compositions, localPlayheadMs, selectedCompositionId) {
      compositions.firstOrNull { localPlayheadMs in it.startMs..it.endMs }
        ?: compositions.firstOrNull { it.id == selectedCompositionId }
    }
  val needsSubjectMask = hasVideo && activeComp?.hasBehindSubjectElements() == true

  // Tightly frame-locked live-preview subject mask loop (eliminates parent-playhead lag & seek drift)
  LaunchedEffect(videoUriString, needsSubjectMask, isPlaying, effectivePerformanceMode, validKeepSegments, playheadMs.takeIf { !isPlaying }) {
    if (!needsSubjectMask) {
      activeSubjectMask = null
      return@LaunchedEffect
    }
    var lastProcessedSourcePosMs = Long.MIN_VALUE
    val targetIntervalMs = if (effectivePerformanceMode) 68L else 36L

    while (isActive) {
      val liveEditedPosMs =
        exoPlayer?.let { computeEditedPlayheadFromPlayer(it, latestKeepSegments) } ?: localPlayheadMs
      val liveSourcePosMs =
        AudioExtractionService.mapEditedToSourceTimeMs(liveEditedPosMs, latestKeepSegments)
      val jumped =
        lastProcessedSourcePosMs != Long.MIN_VALUE &&
          abs(liveSourcePosMs - lastProcessedSourcePosMs) > 240L
      if (jumped) {
        // Seek jump, jump-cut seam, or loop wrap: flush temporal filter so stale cutout never lags
        SubjectMaskProcessor.invalidatePreviewTemporalState()
        activeSubjectMask = null
      }

      val texView =
        cachedTextureViewRef.value
          ?: playerViewRef.value?.let { pv -> findTextureView(pv).also { cachedTextureViewRef.value = it } }
      val pv = playerViewRef.value

      if (texView != null && pv != null && texView.isAvailable && texView.width > 0 && texView.height > 0) {
        activeMaskVideoRect = computeTextureRectInParent(pv, texView)
        val cached = SubjectMaskProcessor.getCachedMask(videoUriString, liveSourcePosMs)
        val shouldComputeFresh =
          cached == null || jumped || abs(liveSourcePosMs - lastProcessedSourcePosMs) >= targetIntervalMs

        if (cached != null && !jumped) {
          activeSubjectMask = cached
        }

        if (shouldComputeFresh) {
          val texRatio = (texView.width.toFloat() / texView.height.toFloat()).coerceIn(0.35f, 3.0f)
          val sampleW = if (effectivePerformanceMode) 224 else 320
          val sampleH = (sampleW / texRatio).toInt().coerceIn(128, 480)
          val captureEditedPosMs =
            exoPlayer?.let { computeEditedPlayheadFromPlayer(it, latestKeepSegments) } ?: liveEditedPosMs
          val captureSourcePosMs =
            AudioExtractionService.mapEditedToSourceTimeMs(captureEditedPosMs, latestKeepSegments)
          val snap = runCatching { texView.getBitmap(sampleW, sampleH) }.getOrNull()
          if (snap != null) {
            val computed =
              SubjectMaskProcessor.generateAndCacheMask(
                videoKey = videoUriString,
                positionMs = captureSourcePosMs,
                frameBitmap = snap,
                highPrecision = !effectivePerformanceMode,
              )
            snap.recycle()
            if (computed != null) {
              activeSubjectMask = computed
              lastProcessedSourcePosMs = captureSourcePosMs
            }
          }
        }
      }

      if (!isPlaying) {
        // When paused, one clean settled mask pass is enough; wait for next scrub/play trigger
        if (activeSubjectMask != null) break
        delay(60L)
      } else {
        delay(targetIntervalMs)
      }
    }
  }

  Box(
    modifier =
      modifier
        .aspectRatio(ratio)
        .clip(RoundedCornerShape(22.dp))
        .background(StudioObsidian)
        .testTag("kinetic_preview_stage"),
    contentAlignment = Alignment.Center,
  ) {
    if (exoPlayer != null) {
      AndroidView(
        factory = { ctx ->
          (LayoutInflater.from(ctx).inflate(R.layout.studio_player_view, null, false) as PlayerView)
            .apply {
              player = exoPlayer
              playerViewRef.value = this
              cachedTextureViewRef.value = findTextureView(this)
            }
        },
        update = { view ->
          if (view.player !== exoPlayer) view.player = exoPlayer
          playerViewRef.value = view
          if (cachedTextureViewRef.value == null) {
            cachedTextureViewRef.value = findTextureView(view)
          }
        },
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(Color(0xFF1F1D26), Color(0xFF121217), StudioObsidian)
              )
            )
      )
    }

    // Unified Kinetic Typography Overlay Canvas + Tap Play/Pause + Magnetic Drag + Pinch-to-Resize
    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(Unit) {
            awaitEachGesture {
              val firstDown = awaitFirstDown(requireUnconsumed = false)
              var targetComp =
                latestCompositions.firstOrNull { localPlayheadMs in it.startMs..it.endMs }
                  ?: latestCompositions.firstOrNull { it.id == latestSelectedId }
              var rawX = targetComp?.anchorXFraction ?: 0.50f
              var rawY = targetComp?.anchorYFraction ?: 0.72f
              dragAnchorX = rawX
              dragAnchorY = rawY
              var cumulativeZoom = 1f
              var totalDragPx = 0f
              var didTransform = false
              var didPinch = false
              var lastSnapKey: String? = null
              val touchSlop = viewConfiguration.touchSlop

              while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                if (targetComp == null) {
                  targetComp =
                    latestCompositions.firstOrNull { localPlayheadMs in it.startMs..it.endMs }
                      ?: latestCompositions.firstOrNull { it.id == latestSelectedId }
                  if (targetComp != null) {
                    rawX = targetComp.anchorXFraction
                    rawY = targetComp.anchorYFraction
                  }
                }

                val activeTarget = targetComp
                if (pressed.size >= 2 && activeTarget != null) {
                  val zoom = event.calculateZoom()
                  val centroidSpread = event.calculateCentroidSize(useCurrent = false)
                  if (centroidSpread > 0f && abs(zoom - 1f) > 0.004f) {
                    didTransform = true
                    didPinch = true
                    isDraggingCanvas = false
                    activeSnapState = null
                    cumulativeZoom = (cumulativeZoom * zoom).coerceIn(0.45f, 2.2f)
                    pinchBadgeText = "Size ${(cumulativeZoom * 100).toInt()}%"
                    latestOnPinchScale(activeTarget.id, zoom, false)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                  }
                } else if (pressed.size == 1 && activeTarget != null && size.width > 0 && size.height > 0) {
                  val pan = event.calculatePan()
                  totalDragPx += pan.getDistance()
                  if (totalDragPx > touchSlop) {
                    didTransform = true
                    isDraggingCanvas = true
                    rawX = (rawX + pan.x / size.width).coerceIn(0.16f, 0.84f)
                    rawY = (rawY + pan.y / size.height).coerceIn(0.10f, 0.90f)
                    val snap = computeMagneticSnap(rawX, rawY, latestSafeZonePlatform)
                    dragAnchorX = snap.x
                    dragAnchorY = snap.y
                    activeSnapState = snap
                    val snapKey = "${snap.snappedVerticalX}|${snap.snappedHorizontalY}"
                    if ((snap.snappedVerticalX != null || snap.snappedHorizontalY != null) && snapKey != lastSnapKey) {
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    lastSnapKey = snapKey
                    latestOnDragAnchor(activeTarget.id, snap.x, snap.y)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                  }
                }
              }

              if (!didTransform && !firstDown.isConsumed) {
                latestOnPlayingChanged(!latestIsPlaying)
              } else if (didPinch && targetComp != null) {
                // Commit undo snapshot on pinch end
                latestOnPinchScale(targetComp.id, 1f, true)
              }
              isDraggingCanvas = false
              activeSnapState = null
              pinchBadgeText = null
            }
          }
    ) {
      val effectivePlatform =
        if (safeZonePlatform != SafeZonePlatform.NONE) safeZonePlatform
        else if (showSafeGuides) SafeZonePlatform.GRID
        else SafeZonePlatform.NONE

      if (effectivePlatform == SafeZonePlatform.GRID) {
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        for (i in 1..2) {
          drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(thirdW * i, 0f),
            end = Offset(thirdW * i, size.height),
            strokeWidth = 1.5f,
          )
          drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(0f, thirdH * i),
            end = Offset(size.width, thirdH * i),
            strokeWidth = 1.5f,
          )
        }
        analysisReport.faceRegions.forEach { face ->
          drawRect(
            color = Color(0xFFEE7E70).copy(alpha = 0.72f),
            topLeft = Offset(face.left * size.width, face.top * size.height),
            size = Size((face.right - face.left) * size.width, (face.bottom - face.top) * size.height),
            style = Stroke(width = 3f),
          )
        }
      } else if (effectivePlatform != SafeZonePlatform.NONE) {
        // Authentic Social Safe-Zone Overlays (TikTok / Instagram Reels / YouTube Shorts)
        val dangerFill = Color(0xFFFF3B5C).copy(alpha = 0.22f)
        val dangerBorder = Color(0xFFFF6B81).copy(alpha = 0.72f)
        val safeBorder = Color(0xFF3AE8B0).copy(alpha = 0.78f)
        val uiMockColor = Color.White.copy(alpha = 0.42f)

        val topDangerH =
          when (effectivePlatform) {
            SafeZonePlatform.TIKTOK -> size.height * 0.13f
            SafeZonePlatform.REELS -> size.height * 0.12f
            SafeZonePlatform.SHORTS -> size.height * 0.11f
            else -> 0f
          }
        val bottomDangerH =
          when (effectivePlatform) {
            SafeZonePlatform.TIKTOK -> size.height * 0.23f
            SafeZonePlatform.REELS -> size.height * 0.24f
            SafeZonePlatform.SHORTS -> size.height * 0.21f
            else -> 0f
          }
        val rightRailW =
          when (effectivePlatform) {
            SafeZonePlatform.TIKTOK -> size.width * 0.17f
            SafeZonePlatform.REELS -> size.width * 0.16f
            SafeZonePlatform.SHORTS -> size.width * 0.16f
            else -> 0f
          }
        val rightRailTop = size.height * 0.44f

        // Top UI danger bar (Following/For You/Camera header)
        drawRect(color = dangerFill, topLeft = Offset.Zero, size = Size(size.width, topDangerH))
        // Bottom UI danger bar (@handle, description, music marquee)
        drawRect(
          color = dangerFill,
          topLeft = Offset(0f, size.height - bottomDangerH),
          size = Size(size.width, bottomDangerH),
        )
        // Right social action rail (Like, Comment, Bookmark, Share)
        drawRect(
          color = dangerFill,
          topLeft = Offset(size.width - rightRailW, rightRailTop),
          size = Size(rightRailW, (size.height - bottomDangerH - rightRailTop).coerceAtLeast(0f)),
        )
        // Green safe caption frame outline
        drawRect(
          color = safeBorder,
          topLeft = Offset(size.width * 0.05f, topDangerH + 4f),
          size = Size(size.width - rightRailW - size.width * 0.06f, size.height - topDangerH - bottomDangerH - 8f),
          style = Stroke(width = 2.2f),
        )
        drawLine(
          color = dangerBorder,
          start = Offset(0f, topDangerH),
          end = Offset(size.width, topDangerH),
          strokeWidth = 1.6f,
        )
        drawLine(
          color = dangerBorder,
          start = Offset(0f, size.height - bottomDangerH),
          end = Offset(size.width, size.height - bottomDangerH),
          strokeWidth = 1.6f,
        )
        // Mock action circles on right rail for instant spatial intuition
        val railCx = size.width - rightRailW * 0.5f
        val circleR = (rightRailW * 0.22f).coerceIn(6f, 16f)
        val railSpan = (size.height - bottomDangerH - rightRailTop).coerceAtLeast(60f)
        for (idx in 0..3) {
          val cy = rightRailTop + railSpan * (0.16f + idx * 0.22f)
          drawCircle(color = uiMockColor, radius = circleR, center = Offset(railCx, cy))
        }
        // Mock bottom handle + caption bars
        val barLeft = size.width * 0.06f
        val barTop = size.height - bottomDangerH + 16f
        drawRoundRect(
          color = uiMockColor,
          topLeft = Offset(barLeft, barTop),
          size = Size(size.width * 0.34f, 8f),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        )
        drawRoundRect(
          color = uiMockColor.copy(alpha = 0.28f),
          topLeft = Offset(barLeft, barTop + 16f),
          size = Size(size.width * 0.56f, 7f),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        )
      }

      val shadowFactor =
        if (effectivePerformanceMode) 0.55f else preset.shadowRadiusFactor
      val drawPositionMs = localPlayheadMs
      val drawSourcePosMs =
        AudioExtractionService.mapEditedToSourceTimeMs(drawPositionMs, latestKeepSegments)

      drawIntoCanvas { canvas ->
        renderer.drawWithSubjectMask(
          canvas = canvas.nativeCanvas,
          canvasWidth = size.width.toInt(),
          canvasHeight = size.height.toInt(),
          compositions = latestCompositions,
          positionMs = drawPositionMs,
          subjectMaskBitmap = if (needsSubjectMask) activeSubjectMask else null,
          wrapInOffscreenLayer = needsSubjectMask,
          shadowMultiplier = shadowFactor,
          selectedCompositionId = latestSelectedId,
          drawSelectionGuide = !isPlaying || isDraggingCanvas,
          videoContentRect = activeMaskVideoRect,
          faceAnchorSamples = analysisReport.faceAnchorSamples,
          audioEnergyEnvelope10ms = audioEnergyEnvelope,
          sourceTimeMs = drawSourcePosMs,
        )
      }

      // Live Magnetic Alignment Snap Lines while dragging captions directly on canvas
      val snap = activeSnapState
      if (isDraggingCanvas && snap != null) {
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        snap.snappedVerticalX?.let { vx ->
          val px = vx * size.width
          drawLine(
            color = Color(0xFF4CC9F0).copy(alpha = 0.92f),
            start = Offset(px, 0f),
            end = Offset(px, size.height),
            strokeWidth = 2.4f,
            pathEffect = if (vx == 0.50f) null else dashEffect,
          )
        }
        snap.snappedHorizontalY?.let { hy ->
          val py = hy * size.height
          drawLine(
            color = Color(0xFFEE7E70).copy(alpha = 0.92f),
            start = Offset(0f, py),
            end = Offset(size.width, py),
            strokeWidth = 2.4f,
            pathEffect = if (hy == 0.50f) null else dashEffect,
          )
        }
      }
    }

    // Floating Gesture HUD Pill (Snap Guide label or Pinch Scale readout)
    val gestureBadge = pinchBadgeText ?: activeSnapState?.snapLabel?.let { "Snapped: $it" }
    if (gestureBadge != null) {
      Surface(
        color = StudioObsidian.copy(alpha = 0.86f),
        shape = CircleShape,
        modifier =
          Modifier.align(Alignment.TopCenter)
            .padding(top = 12.dp)
            .testTag("canvas_gesture_hud_badge"),
      ) {
        Text(
          text = gestureBadge,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF4CC9F0),
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }

    // Minimal Apple-style frosted center Play indicator when paused
    androidx.compose.animation.AnimatedVisibility(
      visible = !isPlaying && !isBuffering && playerError == null,
      enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
      exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.88f),
    ) {
      Surface(
        onClick = { latestOnPlayingChanged(true) },
        color = Color.Black.copy(alpha = 0.56f),
        shape = CircleShape,
        modifier = Modifier.size(62.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play preview",
            tint = Color.White,
            modifier = Modifier.size(32.dp),
          )
        }
      }
    }

    if (isBuffering) {
      CircularProgressIndicator(
        color = com.example.ui.theme.StudioCoral,
        modifier = Modifier.size(34.dp),
        strokeWidth = 3.dp,
      )
    }

    playerError?.let { msg ->
      Surface(
        color = StudioObsidian.copy(alpha = 0.88f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.padding(16.dp),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = CaptionRed)
          Text(msg, style = MaterialTheme.typography.bodyMedium, color = StudioTextPrimary)
        }
      }
    }
  }
}

/**
 * Self-contained Media3 ExoPlayer review player used after MP4 export to verify audio sync and burned-in captions.
 */
@OptIn(UnstableApi::class)
@Composable
fun ExportedVideoReviewPlayer(
  videoUri: Uri,
  aspectRatio: AspectRatioOption,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var isPlaying by remember { mutableStateOf(false) }
  var isMuted by remember { mutableStateOf(false) }
  var isBuffering by remember { mutableStateOf(false) }
  var playerError by remember { mutableStateOf<String?>(null) }
  var currentPosMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(1L) }

  val exoPlayer =
    remember(videoUri) {
      ExoPlayer.Builder(context).build().apply {
        videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        setMediaItem(MediaItem.fromUri(videoUri))
        repeatMode = Player.REPEAT_MODE_ALL
        prepare()
      }
    }

  DisposableEffect(exoPlayer) {
    val listener =
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          isBuffering = playbackState == Player.STATE_BUFFERING
          if (playbackState == Player.STATE_READY) {
            playerError = null
            durationMs = exoPlayer.duration.coerceAtLeast(1L)
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          isBuffering = false
          playerError = error.localizedMessage ?: "Unable to play exported video."
          isPlaying = false
        }
      }
    exoPlayer.addListener(listener)
    onDispose {
      exoPlayer.removeListener(listener)
      exoPlayer.release()
    }
  }

  DisposableEffect(lifecycleOwner, exoPlayer) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        exoPlayer.pause()
        isPlaying = false
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(exoPlayer, isPlaying) { exoPlayer.playWhenReady = isPlaying }
  LaunchedEffect(exoPlayer, isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }

  LaunchedEffect(exoPlayer, isPlaying) {
    while (isPlaying) {
      withFrameMillis {
        currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
      }
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(280.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(StudioObsidian)
          .testTag("exported_video_review_player"),
      contentAlignment = Alignment.Center,
    ) {
      val ratio = aspectRatio.ratioWidth / aspectRatio.ratioHeight
      AndroidView(
        factory = { ctx ->
          (LayoutInflater.from(ctx).inflate(R.layout.studio_player_view, null, false) as PlayerView)
            .apply { player = exoPlayer }
        },
        update = { view -> if (view.player !== exoPlayer) view.player = exoPlayer },
        modifier =
          Modifier.aspectRatio(ratio)
            .fillMaxSize()
            .pointerInput(Unit) {
              detectTapGestures(onTap = { isPlaying = !isPlaying })
            },
      )

      if (isBuffering) {
        CircularProgressIndicator(
          color = com.example.ui.theme.StudioCoral,
          modifier = Modifier.size(32.dp),
          strokeWidth = 3.dp,
        )
      }

      playerError?.let { msg ->
        Surface(
          color = StudioObsidian.copy(alpha = 0.88f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.padding(12.dp),
        ) {
          Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            color = CaptionRed,
            modifier = Modifier.padding(12.dp),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = { isPlaying = !isPlaying },
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .background(com.example.ui.theme.StudioCoral)
            .testTag("export_review_play_pause_button"),
      ) {
        Icon(
          if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (isPlaying) "Pause review" else "Play review",
          tint = Color.White,
        )
      }
      Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
        Slider(
          value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
          onValueChange = {
            isPlaying = false
            currentPosMs = it.toLong()
            exoPlayer.seekTo(currentPosMs)
          },
          valueRange = 0f..durationMs.toFloat(),
          colors =
            SliderDefaults.colors(
              thumbColor = Color.White,
              activeTrackColor = Color.White,
              inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
          modifier = Modifier.height(24.dp),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = formatReviewTimecode(currentPosMs),
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextPrimary,
          )
          Text(
            text = formatReviewTimecode(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextSecondary,
          )
        }
      }
      IconButton(
        onClick = { isMuted = !isMuted },
        modifier = Modifier.testTag("export_review_mute_button"),
      ) {
        Icon(
          if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
          contentDescription = if (isMuted) "Unmute" else "Mute",
          tint = if (isMuted) CaptionRed else StudioTextSecondary,
        )
      }
    }
    Spacer(Modifier.height(2.dp))
  }
}

private fun findTextureView(view: View): TextureView? {
  if (view is TextureView) return view
  if (view is ViewGroup) {
    for (i in 0 until view.childCount) {
      findTextureView(view.getChildAt(i))?.let { return it }
    }
  }
  return null
}

private fun computeTextureRectInParent(parent: View, child: View): RectF? {
  if (parent.width <= 0 || parent.height <= 0 || child.width <= 0 || child.height <= 0) return null
  val parentLoc = IntArray(2)
  val childLoc = IntArray(2)
  parent.getLocationInWindow(parentLoc)
  child.getLocationInWindow(childLoc)
  val left = (childLoc[0] - parentLoc[0]).toFloat()
  val top = (childLoc[1] - parentLoc[1]).toFloat()
  val right = left + child.width.toFloat()
  val bottom = top + child.height.toFloat()
  if (right <= left || bottom <= top) return null
  return RectF(left, top, right, bottom)
}

private fun computeEditedPlayheadFromPlayer(
  player: ExoPlayer,
  keepSegments: List<KeepSegment>,
): Long {
  if (keepSegments.isEmpty()) return player.currentPosition.coerceAtLeast(0L)
  val itemIdx = player.currentMediaItemIndex.coerceIn(0, keepSegments.lastIndex)
  var prefixMs = 0L
  for (i in 0 until itemIdx) {
    prefixMs += keepSegments[i].durationMs
  }
  val inSegmentMs = player.currentPosition.coerceIn(0L, keepSegments[itemIdx].durationMs)
  return prefixMs + inSegmentMs
}

private fun seekPlayerToEditedTime(
  player: ExoPlayer,
  editedMs: Long,
  keepSegments: List<KeepSegment>,
) {
  val clampedEdited = editedMs.coerceAtLeast(0L)
  if (keepSegments.isEmpty()) {
    player.seekTo(clampedEdited)
    return
  }
  var cursor = 0L
  for (idx in keepSegments.indices) {
    val segDur = keepSegments[idx].durationMs
    if (clampedEdited < cursor + segDur || idx == keepSegments.lastIndex) {
      val offsetInSeg = (clampedEdited - cursor).coerceIn(0L, segDur)
      player.seekTo(idx, offsetInSeg)
      return
    }
    cursor += segDur
  }
}

private fun formatReviewTimecode(ms: Long): String {
  val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
  val mins = totalSeconds / 60L
  val secs = totalSeconds % 60L
  val tenths = (ms.coerceAtLeast(0L) % 1000L) / 100L
  return "%02d:%02d.%d".format(mins, secs, tenths)
}

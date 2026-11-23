package com.example.captionstudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.KaraokeHighlightMode
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Clamps a caption block's trimmed [proposedStartMs]..[proposedEndMs] cleanly within video bounds,
 * enforcing a minimum duration and preventing overlap with neighboring captions on the timeline.
 */
fun clampTrimRange(
  proposedStartMs: Long,
  proposedEndMs: Long,
  totalDurationMs: Long,
  prevCompEndMs: Long? = null,
  nextCompStartMs: Long? = null,
  minDurationMs: Long = 300L,
): Pair<Long, Long> {
  val safeTotal = max(totalDurationMs, minDurationMs)
  val minAllowedStart = (prevCompEndMs ?: 0L).coerceIn(0L, max(0L, safeTotal - minDurationMs))
  val maxAllowedEnd = (nextCompStartMs ?: safeTotal).coerceIn(minAllowedStart + minDurationMs, safeTotal)

  var start = proposedStartMs.coerceIn(minAllowedStart, max(minAllowedStart, maxAllowedEnd - minDurationMs))
  var end = proposedEndMs.coerceIn(start + minDurationMs, maxAllowedEnd)
  if (end - start < minDurationMs) {
    end = min(maxAllowedEnd, start + minDurationMs)
    start = max(minAllowedStart, end - minDurationMs)
  }
  return start to end
}

/**
 * Interactive Visual Waveform Caption Timeline:
 * Renders the video's audio amplitude waveform with caption blocks directly aligned on top,
 * featuring draggable left & right trim handles, horizontal zoom (1x/2x/4x), and playhead scrubbing.
 */
@Composable
fun VisualCaptionTimeline(
  compositions: List<CaptionComposition>,
  waveformBins: List<Float>,
  totalDurationMs: Long,
  playheadMs: Long,
  selectedCompositionId: String?,
  onSeekPlayhead: (Long) -> Unit,
  onSelectComposition: (String) -> Unit,
  onTrimComposition: (compId: String, newStartMs: Long, newEndMs: Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current
  val effectiveDurationMs =
    remember(totalDurationMs, compositions) {
      max(totalDurationMs, max(compositions.maxOfOrNull { it.endMs } ?: 4000L, 4000L))
    }
  var zoomFactor by remember { mutableFloatStateOf(1f) }
  var activeTrimReadout by remember { mutableStateOf<String?>(null) }

  StudioGlassCard(modifier = modifier.fillMaxWidth().testTag("visual_waveform_timeline"), cornerRadius = 20.dp) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Compact narrow-screen-safe header with Zoom pills
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Waveform Timeline",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = activeTrimReadout ?: "Drag block edges to trim • Tap track to seek",
            style = MaterialTheme.typography.labelSmall,
            color = if (activeTrimReadout != null) StudioCoralLight else StudioTextSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf(1f to "1x", 2f to "2x", 4f to "4x").forEach { (z, label) ->
            val selected = zoomFactor == z
            Surface(
              onClick = { zoomFactor = z },
              color = if (selected) StudioCoral else StudioElevated,
              shape = CircleShape,
              modifier =
                Modifier.heightIn(min = 34.dp)
                  .border(1.dp, if (selected) StudioCoralLight else StudioHairline, CircleShape)
                  .testTag("timeline_zoom_${label}"),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = if (selected) Color.White else StudioTextSecondary,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
        }
      }

      BoxWithConstraints(
        modifier =
          Modifier.fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(StudioObsidian)
            .border(1.dp, StudioHairline, RoundedCornerShape(14.dp)),
      ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(240f)
        val trackWidthPx = viewportWidthPx * zoomFactor
        val trackWidthDp = with(density) { trackWidthPx.toDp() }
        val scrollState = rememberScrollState()
        val sortedComps = remember(compositions) { compositions.sortedBy { it.startMs } }

        Box(
          modifier =
            Modifier.fillMaxHeight()
              .horizontalScroll(scrollState)
              .width(trackWidthDp),
        ) {
          // 1. Audio Waveform + Time Ruler Canvas + Tap-to-Seek
          Canvas(
            modifier =
              Modifier.fillMaxSize()
                .pointerInput(effectiveDurationMs, trackWidthPx) {
                  detectTapGestures { offset ->
                    val frac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                    onSeekPlayhead((frac * effectiveDurationMs).toLong())
                  }
                }
          ) {
            val rulerH = 22.dp.toPx()
            val waveTop = rulerH + 4.dp.toPx()
            val waveBottom = size.height - 6.dp.toPx()
            val waveMidY = (waveTop + waveBottom) / 2f
            val waveMaxHalfH = (waveBottom - waveTop) * 0.44f

            // Subtle ruler baseline
            drawLine(
              color = Color.White.copy(alpha = 0.08f),
              start = Offset(0f, rulerH),
              end = Offset(size.width, rulerH),
              strokeWidth = 1f,
            )

            // Second tick marks along top ruler
            val stepSec = if (zoomFactor >= 3f) 1 else if (zoomFactor >= 2f) 2 else 3
            val totalSecs = (effectiveDurationMs / 1000L).toInt().coerceAtLeast(1)
            for (sec in 0..totalSecs step stepSec) {
              val x = (sec * 1000f / effectiveDurationMs.toFloat()) * size.width
              drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(x, 6.dp.toPx()),
                end = Offset(x, rulerH),
                strokeWidth = 1.2f,
              )
            }

            // Audio RMS Waveform Bars (real extracted bins or crisp subtle placeholder pulses)
            val bins =
              if (waveformBins.isNotEmpty()) {
                waveformBins
              } else {
                List(72) { idx ->
                  val wave = kotlin.math.abs(kotlin.math.sin(idx * 0.45f)).toFloat()
                  0.14f + wave * 0.34f
                }
              }
            val barSpacing = size.width / bins.size.coerceAtLeast(1)
            val barW = (barSpacing * 0.56f).coerceIn(1.5f, 5f)
            bins.forEachIndexed { idx, amp ->
              val cx = (idx + 0.5f) * barSpacing
              val h = (amp.coerceIn(0.06f, 1f) * waveMaxHalfH).coerceAtLeast(3f)
              val isBeforePlayhead = (cx / size.width) <= (playheadMs.toFloat() / effectiveDurationMs.toFloat())
              drawRoundRect(
                color =
                  if (isBeforePlayhead) StudioCoral.copy(alpha = 0.46f)
                  else Color.White.copy(alpha = 0.16f),
                topLeft = Offset(cx - barW / 2f, waveMidY - h),
                size = Size(barW, h * 2f),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
              )
            }
          }

          // 2. Caption Blocks Overlaid on Waveform with Draggable Left & Right Trim Handles
          sortedComps.forEachIndexed { idx, comp ->
            val prevEnd = sortedComps.getOrNull(idx - 1)?.endMs
            val nextStart = sortedComps.getOrNull(idx + 1)?.startMs
            val isSelected = comp.id == selectedCompositionId
            val isHook = comp.startMs < 3000L
            val hasKaraoke = comp.elements.any { it.karaokeMode != KaraokeHighlightMode.OFF }

            var draftStartMs by remember(comp.id, comp.startMs) { mutableLongStateOf(comp.startMs) }
            var draftEndMs by remember(comp.id, comp.endMs) { mutableLongStateOf(comp.endMs) }
            val latestDraftStart by rememberUpdatedState(draftStartMs)
            val latestDraftEnd by rememberUpdatedState(draftEndMs)
            val latestOnTrim by rememberUpdatedState(onTrimComposition)

            val startPx = ((draftStartMs.toFloat() / effectiveDurationMs.toFloat()) * trackWidthPx).coerceIn(0f, trackWidthPx)
            val endPx = ((draftEndMs.toFloat() / effectiveDurationMs.toFloat()) * trackWidthPx).coerceIn(startPx + 24f, trackWidthPx)
            val blockWidthDp = with(density) { (endPx - startPx).coerceAtLeast(36f).toDp() }
            val blockTopPx = with(density) { 28.dp.toPx() }.roundToInt()

            val blockBg =
              when {
                isSelected -> StudioCoralContainer.copy(alpha = 0.92f)
                isHook -> Color(0xFF2B2219).copy(alpha = 0.88f)
                else -> StudioElevated.copy(alpha = 0.86f)
              }
            val blockBorder =
              when {
                isSelected -> StudioCoralLight
                isHook -> Color(0xFFFFB81C).copy(alpha = 0.65f)
                else -> StudioHairlineStrong
              }

            Box(
              modifier =
                Modifier.offset { IntOffset(startPx.roundToInt(), blockTopPx) }
                  .width(blockWidthDp)
                  .height(84.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(blockBg)
                  .border(if (isSelected) 1.5.dp else 1.dp, blockBorder, RoundedCornerShape(10.dp))
                  .clickable {
                    onSelectComposition(comp.id)
                    onSeekPlayhead(comp.startMs)
                  }
                  .testTag("timeline_caption_block_$idx"),
            ) {
              // Center label content
              Column(
                modifier =
                  Modifier.fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  if (isHook) {
                    Box(
                      modifier =
                        Modifier.size(6.dp)
                          .clip(CircleShape)
                          .background(Color(0xFFFFB81C)),
                    )
                  }
                  if (hasKaraoke) {
                    Box(
                      modifier =
                        Modifier.size(6.dp)
                          .clip(CircleShape)
                          .background(Color(0xFF4CC9F0)),
                    )
                  }
                  Text(
                    text = comp.originalText.ifBlank { "Caption ${idx + 1}" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else StudioTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = "${formatTimelineSec(draftStartMs)}–${formatTimelineSec(draftEndMs)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isSelected) StudioCoralLight else StudioTextSecondary,
                  maxLines = 1,
                  softWrap = false,
                )
              }

              // Draggable LEFT Trim Handle
              Box(
                modifier =
                  Modifier.align(Alignment.CenterStart)
                    .width(22.dp)
                    .fillMaxHeight()
                    .pointerInput(comp.id, effectiveDurationMs, trackWidthPx, prevEnd, nextStart) {
                      var accumStartMs = draftStartMs.toFloat()
                      detectHorizontalDragGestures(
                        onDragStart = {
                          accumStartMs = latestDraftStart.toFloat()
                          onSelectComposition(comp.id)
                          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                          change.consume()
                          val deltaMs = (dragAmount / trackWidthPx) * effectiveDurationMs.toFloat()
                          accumStartMs += deltaMs
                          val (clampedStart, clampedEnd) =
                            clampTrimRange(
                              proposedStartMs = accumStartMs.toLong(),
                              proposedEndMs = latestDraftEnd,
                              totalDurationMs = effectiveDurationMs,
                              prevCompEndMs = prevEnd,
                              nextCompStartMs = nextStart,
                            )
                          draftStartMs = clampedStart
                          draftEndMs = clampedEnd
                          activeTrimReadout = "In: ${formatTimelineSec(clampedStart)}"
                        },
                        onDragEnd = {
                          activeTrimReadout = null
                          latestOnTrim(comp.id, latestDraftStart, latestDraftEnd)
                        },
                        onDragCancel = {
                          activeTrimReadout = null
                          draftStartMs = comp.startMs
                          draftEndMs = comp.endMs
                        },
                      )
                    }
                    .testTag("timeline_trim_left_$idx"),
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier =
                    Modifier.width(4.dp)
                      .height(34.dp)
                      .clip(CircleShape)
                      .background(if (isSelected) Color.White else StudioCoralLight.copy(alpha = 0.72f)),
                )
              }

              // Draggable RIGHT Trim Handle
              Box(
                modifier =
                  Modifier.align(Alignment.CenterEnd)
                    .width(22.dp)
                    .fillMaxHeight()
                    .pointerInput(comp.id, effectiveDurationMs, trackWidthPx, prevEnd, nextStart) {
                      var accumEndMs = draftEndMs.toFloat()
                      detectHorizontalDragGestures(
                        onDragStart = {
                          accumEndMs = latestDraftEnd.toFloat()
                          onSelectComposition(comp.id)
                          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                          change.consume()
                          val deltaMs = (dragAmount / trackWidthPx) * effectiveDurationMs.toFloat()
                          accumEndMs += deltaMs
                          val (clampedStart, clampedEnd) =
                            clampTrimRange(
                              proposedStartMs = latestDraftStart,
                              proposedEndMs = accumEndMs.toLong(),
                              totalDurationMs = effectiveDurationMs,
                              prevCompEndMs = prevEnd,
                              nextCompStartMs = nextStart,
                            )
                          draftStartMs = clampedStart
                          draftEndMs = clampedEnd
                          activeTrimReadout = "Out: ${formatTimelineSec(clampedEnd)}"
                        },
                        onDragEnd = {
                          activeTrimReadout = null
                          latestOnTrim(comp.id, latestDraftStart, latestDraftEnd)
                        },
                        onDragCancel = {
                          activeTrimReadout = null
                          draftStartMs = comp.startMs
                          draftEndMs = comp.endMs
                        },
                      )
                    }
                    .testTag("timeline_trim_right_$idx"),
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier =
                    Modifier.width(4.dp)
                      .height(34.dp)
                      .clip(CircleShape)
                      .background(if (isSelected) Color.White else StudioCoralLight.copy(alpha = 0.72f)),
                )
              }
            }
          }

          // 3. Playhead Needle Overlay
          val playheadX = ((playheadMs.toFloat() / effectiveDurationMs.toFloat()) * trackWidthPx).coerceIn(0f, trackWidthPx)
          Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
              color = Color.White,
              start = Offset(playheadX, 0f),
              end = Offset(playheadX, size.height),
              strokeWidth = 2.4f,
            )
            drawCircle(
              color = StudioCoralLight,
              radius = 5.dp.toPx(),
              center = Offset(playheadX, 8.dp.toPx()),
            )
          }
        }
      }
    }
  }
}

private fun formatTimelineSec(ms: Long): String {
  val clamped = ms.coerceAtLeast(0L)
  val secs = clamped / 1000L
  val tenths = (clamped % 1000L) / 100L
  return "${secs}.${tenths}s"
}

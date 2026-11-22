package com.example.captionstudio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.LayerMotionEffect
import com.example.captionstudio.engine.KineticCompositionRenderer
import com.example.captionstudio.typography.FontRegistry
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

private enum class PreviewBackdropMode(val label: String) {
  DARK("Dark"),
  CHECKER("Grid"),
  LIGHT("Bright"),
}

/**
 * Real-time WYSIWYG interactive preview card embedded inside the typography customization menu.
 * Uses the exact same [KineticCompositionRenderer] as the main video stage & MP4 export so every
 * font, color, outline, shadow, glow, backdrop, and looping motion effect previews faithfully.
 */
@Composable
fun LiveStylePreviewCard(
  activeComp: CaptionComposition,
  activeEl: CompositionElement,
  replayTrigger: Int = 0,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = true,
) {
  val context = LocalContext.current
  val renderer = remember(context) { KineticCompositionRenderer(context) }

  var isExpanded by remember { mutableStateOf(initiallyExpanded) }
  var isPlaying by remember { mutableStateOf(true) }
  var showFullCaption by remember { mutableStateOf(false) }
  var backdropMode by remember { mutableStateOf(PreviewBackdropMode.DARK) }
  var loopTimeMs by remember { mutableLongStateOf(0L) }
  var manualReplayNonce by remember { mutableIntStateOf(0) }

  val loopDurationMs = 2400L

  // Build an isolated centered preview composition so the user sees the exact style clearly
  val previewCompositions =
    remember(activeComp, activeEl, showFullCaption) {
      val previewElements =
        if (showFullCaption) {
          activeComp.elements.map { el ->
            el.copy(
              wordIndices = emptyList(),
              staggerDelayMs = 0,
              fontSizeSp = el.fontSizeSp.coerceIn(14f, 38f),
            )
          }
        } else {
          listOf(
            activeEl.copy(
              wordIndices = emptyList(),
              staggerDelayMs = 0,
              inlineRowGroup = 0,
              offsetXEm = 0f,
              offsetYEm = 0f,
              fontSizeSp = activeEl.fontSizeSp.coerceIn(18f, 44f),
            )
          )
        }
      listOf(
        activeComp.copy(
          id = "live_preview_${activeComp.id}_${activeEl.id}",
          startMs = 0L,
          endMs = loopDurationMs,
          anchorXFraction = 0.50f,
          anchorYFraction = 0.50f,
          entryAnimation =
            if (activeEl.motionEffect == LayerMotionEffect.NONE) activeComp.entryAnimation
            else EntryAnimationType.CUT,
          exitAnimation = ExitAnimationType.FADE_DROP,
          entryDurationMs = activeComp.entryDurationMs.coerceIn(120, 320),
          exitDurationMs = 180,
          elements = previewElements,
          wordMappings = emptyList(),
          backgroundMaskEnabled = false,
        )
      )
    }

  // Automatically replay from 0ms whenever user picks a new motion effect or taps Replay
  LaunchedEffect(activeEl.motionEffect, replayTrigger, manualReplayNonce) {
    loopTimeMs = 0L
    isPlaying = true
  }

  // Smooth, low-overhead frame ticker that only invalidates the Canvas draw phase
  LaunchedEffect(isExpanded, isPlaying, activeEl.motionEffect) {
    if (!isExpanded || !isPlaying) return@LaunchedEffect
    var lastFrame = -1L
    while (isExpanded && isPlaying) {
      withFrameMillis { frameMs ->
        if (lastFrame >= 0L) {
          val delta = (frameMs - lastFrame).coerceIn(0L, 64L)
          loopTimeMs = (loopTimeMs + delta) % loopDurationMs
        }
        lastFrame = frameMs
      }
    }
  }

  val fontDesc = remember(activeEl.fontFamilyId) { FontRegistry.findById(activeEl.fontFamilyId) }
  val fillColor = remember(activeEl.colorHex) { parseComposeHexColor(activeEl.colorHex) }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(StudioObsidian)
        .border(1.dp, StudioHairline, RoundedCornerShape(18.dp)),
  ) {
    // Compact Preview Header Strip with live style badge + collapse toggle
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clickable(role = Role.Button) { isExpanded = !isExpanded }
          .padding(horizontal = 12.dp, vertical = 9.dp)
          .testTag("live_preview_header_toggle"),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Rounded.Visibility,
          contentDescription = null,
          tint = StudioCoralLight,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = stringResource(R.string.preview_live_title),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = StudioTextPrimary,
        )
        Box(
          modifier =
            Modifier.size(9.dp)
              .clip(CircleShape)
              .background(fillColor)
              .border(0.8.dp, Color.White.copy(alpha = 0.4f), CircleShape)
        )
        Text(
          text =
            buildString {
              append(fontDesc.displayName)
              if (activeEl.motionEffect != LayerMotionEffect.NONE) {
                append(" • ")
                append(activeEl.motionEffect.label)
              }
            },
          style = MaterialTheme.typography.labelSmall,
          color = StudioTextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Icon(
        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
        contentDescription = stringResource(R.string.preview_toggle_visibility_cd),
        tint = StudioTextSecondary,
        modifier = Modifier.size(18.dp),
      )
    }

    AnimatedVisibility(
      visible = isExpanded,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Column {
        // Real Canvas Stage rendering the live caption with current style & motion
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(142.dp)
              .padding(horizontal = 10.dp)
              .clip(RoundedCornerShape(14.dp))
              .border(1.dp, StudioHairline, RoundedCornerShape(14.dp))
              .clickable(role = Role.Button) {
                if (isPlaying) {
                  manualReplayNonce++
                } else {
                  isPlaying = true
                }
              }
              .testTag("live_style_preview_canvas"),
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            // Backdrop chosen by user to inspect legibility on Dark, Bright, or Grid footage
            when (backdropMode) {
              PreviewBackdropMode.DARK -> {
                drawRect(
                  brush =
                    Brush.radialGradient(
                      colors = listOf(Color(0xFF1F1E28), Color(0xFF0E0E13)),
                      center = Offset(size.width / 2f, size.height / 2f),
                      radius = size.maxDimension * 0.72f,
                    )
                )
              }
              PreviewBackdropMode.LIGHT -> {
                drawRect(
                  brush =
                    Brush.linearGradient(
                      colors = listOf(Color(0xFFE7E2D8), Color(0xFFCFC8BC)),
                    )
                )
              }
              PreviewBackdropMode.CHECKER -> {
                val tile = 14.dp.toPx()
                val cols = (size.width / tile).toInt() + 1
                val rows = (size.height / tile).toInt() + 1
                for (r in 0 until rows) {
                  for (c in 0 until cols) {
                    val isDarkTile = (r + c) % 2 == 0
                    drawRect(
                      color = if (isDarkTile) Color(0xFF1C1C22) else Color(0xFF282831),
                      topLeft = Offset(c * tile, r * tile),
                      size = Size(tile, tile),
                    )
                  }
                }
              }
            }

            val renderPos = if (isPlaying) loopTimeMs else 900L
            drawIntoCanvas { canvas ->
              renderer.drawAtTime(
                canvas = canvas.nativeCanvas,
                canvasWidth = size.width.toInt(),
                canvasHeight = size.height.toInt(),
                compositions = previewCompositions,
                positionMs = renderPos,
                shadowMultiplier = 1.0f,
                drawSelectionGuide = false,
              )
            }

            // Minimal bottom motion timeline progress hairline
            val frac = (loopTimeMs.toFloat() / loopDurationMs.toFloat()).coerceIn(0f, 1f)
            drawRect(
              color = Color.White.copy(alpha = 0.10f),
              topLeft = Offset(0f, size.height - 2.5.dp.toPx()),
              size = Size(size.width, 2.5.dp.toPx()),
            )
            drawRect(
              color = StudioCoral,
              topLeft = Offset(0f, size.height - 2.5.dp.toPx()),
              size = Size(size.width * frac, 2.5.dp.toPx()),
            )
          }
        }

        // Clean bottom utility row: Play/Pause | Replay | Layer vs Caption | Backdrop toggle
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            IconButton(
              onClick = { isPlaying = !isPlaying },
              modifier =
                Modifier.size(34.dp)
                  .minimumInteractiveComponentSize()
                  .clip(CircleShape)
                  .background(StudioElevated)
                  .border(1.dp, StudioHairline, CircleShape)
                  .testTag("live_preview_play_pause"),
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription =
                  stringResource(if (isPlaying) R.string.preview_pause_cd else R.string.preview_play_cd),
                tint = StudioCoralLight,
                modifier = Modifier.size(17.dp),
              )
            }

            IconButton(
              onClick = { manualReplayNonce++ },
              modifier =
                Modifier.size(34.dp)
                  .minimumInteractiveComponentSize()
                  .clip(CircleShape)
                  .background(StudioElevated)
                  .border(1.dp, StudioHairline, CircleShape)
                  .testTag("live_preview_replay"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Replay,
                contentDescription = stringResource(R.string.preview_replay_cd),
                tint = StudioTextPrimary,
                modifier = Modifier.size(16.dp),
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (activeComp.elements.size > 1) {
              Box(
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(StudioElevated)
                    .border(1.dp, StudioHairlineStrong, CircleShape)
                    .clickable(role = Role.Button) { showFullCaption = !showFullCaption }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("live_preview_scope_toggle"),
              ) {
                Text(
                  text = if (showFullCaption) "Full Caption" else "Layer Only",
                  style = MaterialTheme.typography.labelSmall,
                  color = StudioTextPrimary,
                )
              }
            }

            Row(
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(CircleShape)
                  .background(StudioElevated)
                  .border(1.dp, StudioHairlineStrong, CircleShape)
                  .clickable(role = Role.Button) {
                    backdropMode =
                      when (backdropMode) {
                        PreviewBackdropMode.DARK -> PreviewBackdropMode.CHECKER
                        PreviewBackdropMode.CHECKER -> PreviewBackdropMode.LIGHT
                        PreviewBackdropMode.LIGHT -> PreviewBackdropMode.DARK
                      }
                  }
                  .semantics { contentDescription = "Switch preview backdrop, current ${backdropMode.label}" }
                  .padding(horizontal = 10.dp, vertical = 5.dp)
                  .testTag("live_preview_backdrop_toggle"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Contrast,
                contentDescription = null,
                tint = StudioCoralLight,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = backdropMode.label,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextPrimary,
              )
            }
          }
        }
      }
    }
  }
}

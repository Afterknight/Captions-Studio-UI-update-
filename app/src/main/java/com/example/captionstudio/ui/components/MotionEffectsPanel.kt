package com.example.captionstudio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.ElementStylePatch
import com.example.captionstudio.domain.model.LayerMotionEffect
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The 8 cleanest, most tasteful motion effects surfaced in the UI picker.
 * All legacy [LayerMotionEffect] enum entries remain intact in CompositionModels.kt
 * for backward compatibility with existing saved projects.
 */
internal val CuratedMotionEffects: List<LayerMotionEffect> =
  listOf(
    LayerMotionEffect.NONE,
    LayerMotionEffect.FADE_IN,
    LayerMotionEffect.SLIDE_UP,
    LayerMotionEffect.POP_IN,
    LayerMotionEffect.ZOOM_IN,
    LayerMotionEffect.BOUNCE,
    LayerMotionEffect.FLOAT,
    LayerMotionEffect.TYPEWRITER,
  )

/**
 * Curated motion effects selector accessible directly inside the Pro Inspector.
 * Lets creators pick from 8 clean motion effects, apply them live to the active kinetic layer,
 * and fine-tune Speed and Intensity cleanly.
 */
@Composable
fun MotionEffectsPanel(
  activeEl: CompositionElement,
  onPatchStyle: (ElementStylePatch, Boolean) -> Unit,
  onTriggerPreviewReplay: () -> Unit,
  onOpenAnimationStudio: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Active effect summary pill
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(StudioElevated)
          .border(1.dp, StudioHairline, RoundedCornerShape(14.dp))
          .padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = motionEffectIcon(activeEl.motionEffect),
        contentDescription = null,
        tint = StudioCoralLight,
        modifier = Modifier.size(18.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Active Motion: ${activeEl.motionEffect.label}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = StudioTextPrimary,
        )
        Text(
          text = activeEl.motionEffect.summary,
          style = MaterialTheme.typography.bodySmall,
          color = StudioTextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Balanced 2-column x 4-row grid of the 8 curated motion effects
    val chunkedRows = remember { CuratedMotionEffects.chunked(2) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      chunkedRows.forEach { rowItems ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          rowItems.forEach { effect ->
            val isSelected = activeEl.motionEffect == effect
            MotionEffectCard(
              effect = effect,
              selected = isSelected,
              onClick = {
                onPatchStyle(ElementStylePatch(motionEffect = effect), true)
                onTriggerPreviewReplay()
              },
              modifier = Modifier.weight(1f),
            )
          }
          repeat(2 - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }

    // Speed & Intensity controls appear smoothly whenever a motion effect is active
    AnimatedVisibility(
      visible = activeEl.motionEffect != LayerMotionEffect.NONE,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioElevated)
            .border(1.dp, StudioHairline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AccessibleSliderControl(
          label = stringResource(R.string.motion_speed_label),
          valueText = String.format(Locale.US, "%.1fx", activeEl.motionSpeed),
          value = activeEl.motionSpeed,
          valueRange = 0.4f..2.2f,
          step = 0.2f,
          onValueChange = { onPatchStyle(ElementStylePatch(motionSpeed = it), false) },
          onValueChangeFinished = {
            onPatchStyle(ElementStylePatch(motionSpeed = activeEl.motionSpeed), true)
            onTriggerPreviewReplay()
          },
          testTagPrefix = "motion_speed",
        )

        AccessibleSliderControl(
          label = stringResource(R.string.motion_intensity_label),
          valueText = "${(activeEl.motionIntensity * 100f).roundToInt()}%",
          value = activeEl.motionIntensity,
          valueRange = 0.25f..2.0f,
          step = 0.15f,
          onValueChange = { onPatchStyle(ElementStylePatch(motionIntensity = it), false) },
          onValueChangeFinished = {
            onPatchStyle(ElementStylePatch(motionIntensity = activeEl.motionIntensity), true)
            onTriggerPreviewReplay()
          },
          testTagPrefix = "motion_intensity",
        )
      }
    }

    if (onOpenAnimationStudio != null) {
      OutlinedButton(
        onClick = onOpenAnimationStudio,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("open_caption_entry_exit_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Animation,
          contentDescription = null,
          tint = StudioCoralLight,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "Whole-Caption Entry & Exit Timing",
          style = MaterialTheme.typography.labelMedium,
          color = StudioTextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
          contentDescription = null,
          tint = StudioCoralLight,
          modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}

@Composable
private fun MotionEffectCard(
  effect: LayerMotionEffect,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(14.dp)
  Box(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .height(74.dp)
        .clip(shape)
        .background(if (selected) StudioCoralContainer else StudioElevated)
        .border(
          width = if (selected) 1.6.dp else 1.dp,
          color = if (selected) StudioCoral else StudioHairline,
          shape = shape,
        )
        .clickable(role = Role.RadioButton, onClick = onClick)
        .semantics {
          this.selected = selected
          contentDescription = "${effect.label} motion effect, ${effect.summary}" +
            if (selected) ", selected" else ""
        }
        .padding(horizontal = 6.dp, vertical = 8.dp)
        .testTag("motion_preset_${effect.name.lowercase()}"),
  ) {
    if (selected) {
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .size(15.dp)
            .clip(CircleShape)
            .background(StudioCoral),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Check,
          contentDescription = null,
          tint = StudioObsidian,
          modifier = Modifier.size(10.dp),
        )
      }
    }

    Column(
      modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = motionEffectIcon(effect),
        contentDescription = null,
        tint = if (selected) StudioCoralLight else StudioTextPrimary.copy(alpha = 0.88f),
        modifier = Modifier.size(21.dp),
      )
      Text(
        text = effect.label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) StudioCoralLight else StudioTextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun motionEffectIcon(effect: LayerMotionEffect): ImageVector {
  return when (effect) {
    LayerMotionEffect.NONE -> Icons.Rounded.Block
    LayerMotionEffect.FADE_IN -> Icons.Rounded.Opacity
    LayerMotionEffect.SLIDE_UP -> Icons.Rounded.ArrowUpward
    LayerMotionEffect.SLIDE_DOWN -> Icons.Rounded.ArrowDownward
    LayerMotionEffect.SLIDE_LEFT -> Icons.AutoMirrored.Rounded.ArrowBack
    LayerMotionEffect.POP_IN -> Icons.Rounded.AutoAwesome
    LayerMotionEffect.ZOOM_IN -> Icons.Rounded.ZoomIn
    LayerMotionEffect.BOUNCE -> Icons.Rounded.SportsBasketball
    LayerMotionEffect.WIGGLE -> Icons.Rounded.GraphicEq
    LayerMotionEffect.PULSE -> Icons.Rounded.Animation
    LayerMotionEffect.FLOAT -> Icons.Rounded.Air
    LayerMotionEffect.WAVE -> Icons.Rounded.Waves
    LayerMotionEffect.SWING -> Icons.Rounded.Pending
    LayerMotionEffect.SHAKE -> Icons.Rounded.Vibration
    LayerMotionEffect.TYPEWRITER -> Icons.Rounded.Keyboard
  }
}

package com.example.captionstudio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.ElementStylePatch
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextAlignment
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Organized 4-section typography style inspector (`Text | Color | Effects | Layout`) so every
 * visual property of a kinetic typography layer is editable and accessible without screen clutter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypographyInspectorDeck(
  activeComp: CaptionComposition,
  activeEl: CompositionElement,
  onPatchStyle: (ElementStylePatch, Boolean) -> Unit,
  onUpdateText: (String) -> Unit,
  onDeleteElement: (() -> Unit)? = null,
  onToggleCaptionDepthMask: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedSection by remember { mutableIntStateOf(0) }
  val tabs =
    listOf(
      stringResource(R.string.typo_tab_text),
      stringResource(R.string.typo_tab_color),
      stringResource(R.string.typo_tab_effects),
      stringResource(R.string.typo_tab_layout),
    )

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Sleek 4-pill Apple-style Segmented Control
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(CircleShape)
          .background(StudioObsidian)
          .border(1.dp, StudioHairline, CircleShape)
          .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      tabs.forEachIndexed { idx, label ->
        val isSelected = selectedSection == idx
        Box(
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(if (isSelected) StudioElevated else Color.Transparent)
              .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) StudioHairlineStrong else Color.Transparent,
                shape = CircleShape,
              )
              .clickable(role = Role.Tab) { selectedSection = idx }
              .semantics { selected = isSelected }
              .padding(vertical = 8.dp)
              .testTag("typo_inspector_tab_${label.lowercase()}"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) StudioCoralLight else StudioTextSecondary,
            maxLines = 1,
          )
        }
      }
    }

    AnimatedContent(
      targetState = selectedSection,
      transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
      label = "typography_inspector_section",
    ) { tabIdx ->
      when (tabIdx) {
        0 ->
          TypographyTextTab(
            activeEl = activeEl,
            canDelete = activeComp.elements.size > 1 && onDeleteElement != null,
            onUpdateText = onUpdateText,
            onDeleteElement = onDeleteElement,
            onPatchStyle = onPatchStyle,
          )
        1 ->
          TypographyColorTab(
            activeEl = activeEl,
            onPatchStyle = onPatchStyle,
          )
        2 ->
          TypographyEffectsTab(
            activeEl = activeEl,
            onPatchStyle = onPatchStyle,
          )
        else ->
          TypographyLayoutTab(
            activeComp = activeComp,
            activeEl = activeEl,
            onPatchStyle = onPatchStyle,
            onToggleCaptionDepthMask = onToggleCaptionDepthMask,
          )
      }
    }
  }
}

@Composable
private fun TypographyTextTab(
  activeEl: CompositionElement,
  canDelete: Boolean,
  onUpdateText: (String) -> Unit,
  onDeleteElement: (() -> Unit)?,
  onPatchStyle: (ElementStylePatch, Boolean) -> Unit,
) {
  var elementText by remember(activeEl.id, activeEl.text) { mutableStateOf(activeEl.text) }
  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = StudioCoralContainer,
      selectedLabelColor = StudioCoralLight,
      containerColor = StudioElevated,
      labelColor = StudioTextSecondary,
    )

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Layer text input + Update + optional Delete
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = elementText,
        onValueChange = { elementText = it },
        label = { Text("Layer text") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = StudioCoral,
            unfocusedBorderColor = StudioBorder,
            focusedLabelColor = StudioCoral,
            cursorColor = StudioCoral,
          ),
        modifier = Modifier.weight(1f).testTag("layer_text_input"),
      )
      Button(
        onClick = { onUpdateText(elementText) },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = StudioObsidian),
        modifier = Modifier.height(48.dp).testTag("update_layer_text_button"),
      ) {
        Text("Save", fontWeight = FontWeight.SemiBold)
      }
      if (canDelete && onDeleteElement != null) {
        IconButton(
          onClick = onDeleteElement,
          modifier =
            Modifier.size(44.dp)
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(StudioElevated)
              .border(1.dp, StudioHairline, CircleShape)
              .testTag("delete_layer_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.typo_delete_layer_cd),
            tint = StudioError,
            modifier = Modifier.size(19.dp),
          )
        }
      }
    }

    // Format & Alignment Quick Icon Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StyleGlyphToggle(
          label = "Italic",
          icon = Icons.Rounded.FormatItalic,
          selected = activeEl.isItalic,
          onClick = { onPatchStyle(ElementStylePatch(isItalic = !activeEl.isItalic), true) },
          testTag = "toggle_italic_button",
        )
        StyleGlyphToggle(
          label = "Uppercase",
          glyphText = "AA",
          selected = activeEl.uppercase,
          onClick = { onPatchStyle(ElementStylePatch(uppercase = !activeEl.uppercase), true) },
          testTag = "toggle_uppercase_button",
        )
        StyleGlyphToggle(
          label = "Underline",
          icon = Icons.Rounded.FormatUnderlined,
          selected = activeEl.isUnderlined,
          onClick = { onPatchStyle(ElementStylePatch(isUnderlined = !activeEl.isUnderlined), true) },
          testTag = "toggle_underline_button",
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StyleGlyphToggle(
          label = "Align Left",
          icon = Icons.AutoMirrored.Rounded.FormatAlignLeft,
          selected = activeEl.alignment == TextAlignment.LEFT,
          onClick = { onPatchStyle(ElementStylePatch(alignment = TextAlignment.LEFT), true) },
          testTag = "align_left_button",
        )
        StyleGlyphToggle(
          label = "Align Center",
          icon = Icons.Rounded.FormatAlignCenter,
          selected = activeEl.alignment == TextAlignment.CENTER,
          onClick = { onPatchStyle(ElementStylePatch(alignment = TextAlignment.CENTER), true) },
          testTag = "align_center_button",
        )
        StyleGlyphToggle(
          label = "Align Right",
          icon = Icons.AutoMirrored.Rounded.FormatAlignRight,
          selected = activeEl.alignment == TextAlignment.RIGHT,
          onClick = { onPatchStyle(ElementStylePatch(alignment = TextAlignment.RIGHT), true) },
          testTag = "align_right_button",
        )
      }
    }

    // Font Weight Row
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Font Weight", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf(
          300 to "Light",
          400 to "Regular",
          500 to "Medium",
          600 to "SemiBold",
          700 to "Bold",
          900 to "Black",
        ).forEach { (w, lbl) ->
          FilterChip(
            selected = activeEl.fontWeight == w,
            onClick = { onPatchStyle(ElementStylePatch(fontWeight = w), true) },
            shape = CircleShape,
            colors = chipColors,
            border =
              FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = activeEl.fontWeight == w,
                borderColor = StudioHairline,
                selectedBorderColor = StudioCoral.copy(alpha = 0.7f),
              ),
            label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.testTag("weight_chip_$w"),
          )
        }
      }
    }

    // Size, Tracking, Line Height, Opacity Sliders with Accessible Steppers
    AccessibleSliderControl(
      label = "Font Size",
      valueText = "${activeEl.fontSizeSp.roundToInt()} sp",
      value = activeEl.fontSizeSp,
      valueRange = 12f..68f,
      step = 2f,
      onValueChange = { onPatchStyle(ElementStylePatch(fontSizeSp = it), false) },
      onValueChangeFinished = { onPatchStyle(ElementStylePatch(fontSizeSp = activeEl.fontSizeSp), true) },
      testTagPrefix = "font_size",
    )

    AccessibleSliderControl(
      label = "Letter Spacing",
      valueText = String.format(Locale.US, "%+.2f em", activeEl.letterSpacingEm),
      value = activeEl.letterSpacingEm,
      valueRange = -0.08f..0.30f,
      step = 0.02f,
      onValueChange = { onPatchStyle(ElementStylePatch(letterSpacingEm = it), false) },
      onValueChangeFinished = {
        onPatchStyle(ElementStylePatch(letterSpacingEm = activeEl.letterSpacingEm), true)
      },
      testTagPrefix = "letter_spacing",
    )

    AccessibleSliderControl(
      label = "Line Height",
      valueText = String.format(Locale.US, "%.2fx", activeEl.lineHeightMultiplier),
      value = activeEl.lineHeightMultiplier,
      valueRange = 0.85f..1.60f,
      step = 0.05f,
      onValueChange = { onPatchStyle(ElementStylePatch(lineHeightMultiplier = it), false) },
      onValueChangeFinished = {
        onPatchStyle(ElementStylePatch(lineHeightMultiplier = activeEl.lineHeightMultiplier), true)
      },
      testTagPrefix = "line_height",
    )

    AccessibleSliderControl(
      label = "Opacity",
      valueText = "${(activeEl.opacity * 100f).roundToInt()}%",
      value = activeEl.opacity,
      valueRange = 0.15f..1.0f,
      step = 0.05f,
      onValueChange = { onPatchStyle(ElementStylePatch(opacity = it), false) },
      onValueChangeFinished = { onPatchStyle(ElementStylePatch(opacity = activeEl.opacity), true) },
      testTagPrefix = "opacity",
    )
  }
}

@Composable
private fun TypographyColorTab(
  activeEl: CompositionElement,
  onPatchStyle: (ElementStylePatch, Boolean) -> Unit,
) {
  val hasBackground = !activeEl.backgroundColorHex.isNullOrBlank()

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Text Fill Color", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
      AccessibleColorSwatchRow(
        selectedHex = activeEl.colorHex,
        swatches = StudioPalette.textSwatches,
        onSelectColor = { hex -> onPatchStyle(ElementStylePatch(colorHex = hex), true) },
        swatchTagPrefix = "text_color",
      )
    }

    // Highlight Background Pill Card
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(StudioElevated)
          .border(1.dp, StudioHairline, RoundedCornerShape(16.dp))
          .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Highlight Background Pill", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
          Text(
            "Add a solid or translucent backdrop behind this text layer",
            style = MaterialTheme.typography.bodySmall,
            color = StudioTextSecondary,
          )
        }
        Switch(
          checked = hasBackground,
          onCheckedChange = { enabled ->
            if (enabled) {
              onPatchStyle(
                ElementStylePatch(
                  backgroundColorHex = activeEl.backgroundColorHex ?: StudioPalette.SHADOW,
                ),
                true,
              )
            } else {
              onPatchStyle(ElementStylePatch(clearBackground = true), true)
            }
          },
          colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StudioCoral),
          modifier = Modifier.testTag("toggle_background_pill_switch"),
        )
      }

      AnimatedVisibility(
        visible = hasBackground,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          AccessibleColorSwatchRow(
            selectedHex = activeEl.backgroundColorHex ?: StudioPalette.SHADOW,
            swatches = StudioPalette.effectSwatches,
            onSelectColor = { hex -> onPatchStyle(ElementStylePatch(backgroundColorHex = hex), true) },
            swatchTagPrefix = "bg_pill_color",
          )
          AccessibleSliderControl(
            label = "Backdrop Opacity",
            valueText = "${(activeEl.backgroundAlpha * 100f).roundToInt()}%",
            value = activeEl.backgroundAlpha,
            valueRange = 0.15f..1.0f,
            step = 0.05f,
            onValueChange = { onPatchStyle(ElementStylePatch(backgroundAlpha = it), false) },
            onValueChangeFinished = {
              onPatchStyle(ElementStylePatch(backgroundAlpha = activeEl.backgroundAlpha), true)
            },
            testTagPrefix = "bg_opacity",
          )
          AccessibleSliderControl(
            label = "Corner Roundness",
            valueText = "${activeEl.backgroundCornerRadiusDp.roundToInt()} dp",
            value = activeEl.backgroundCornerRadiusDp,
            valueRange = 0f..28f,
            step = 2f,
            onValueChange = { onPatchStyle(ElementStylePatch(backgroundCornerRadiusDp = it), false) },
            onValueChangeFinished = {
              onPatchStyle(
                ElementStylePatch(backgroundCornerRadiusDp = activeEl.backgroundCornerRadiusDp),
                true,
              )
            },
            testTagPrefix = "bg_radius",
          )
        }
      }
    }
  }
}

@Composable
private fun TypographyEffectsTab(
  activeEl: CompositionElement,
  onPatchStyle: (ElementStylePatch, Boolean) -> Unit,
) {
  val hasStroke = activeEl.strokeWidthFactor > 0.05f
  val hasGlow = !activeEl.glowColorHex.isNullOrBlank()

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // 1. Outline / Stroke
    EffectSectionCard(
      title = "Text Outline",
      subtitle = "Crisp rounded border around letters for busy footage",
      checked = hasStroke,
      switchTag = "toggle_stroke_switch",
      onCheckedChange = { enabled ->
        onPatchStyle(ElementStylePatch(strokeWidthFactor = if (enabled) 2.2f else 0f), true)
      },
    ) {
      AccessibleSliderControl(
        label = "Outline Thickness",
        valueText = String.format(Locale.US, "%.1f", activeEl.strokeWidthFactor),
        value = activeEl.strokeWidthFactor,
        valueRange = 0.5f..8.0f,
        step = 0.5f,
        onValueChange = { onPatchStyle(ElementStylePatch(strokeWidthFactor = it), false) },
        onValueChangeFinished = {
          onPatchStyle(ElementStylePatch(strokeWidthFactor = activeEl.strokeWidthFactor), true)
        },
        testTagPrefix = "stroke_width",
      )
      Text("Outline Color", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
      AccessibleColorSwatchRow(
        selectedHex = activeEl.strokeColorHex,
        swatches = StudioPalette.effectSwatches,
        onSelectColor = { hex -> onPatchStyle(ElementStylePatch(strokeColorHex = hex), true) },
        swatchTagPrefix = "stroke_color",
      )
    }

    // 2. Drop Shadow
    EffectSectionCard(
      title = "Drop Shadow",
      subtitle = "Soft directional shadow for cinematic separation",
      checked = activeEl.shadowEnabled,
      switchTag = "toggle_shadow_switch",
      onCheckedChange = { enabled ->
        onPatchStyle(ElementStylePatch(shadowEnabled = enabled), true)
      },
    ) {
      AccessibleSliderControl(
        label = "Shadow Softness",
        valueText = "${(activeEl.shadowIntensity * 100f).roundToInt()}%",
        value = activeEl.shadowIntensity,
        valueRange = 0.2f..2.0f,
        step = 0.1f,
        onValueChange = { onPatchStyle(ElementStylePatch(shadowIntensity = it), false) },
        onValueChangeFinished = {
          onPatchStyle(ElementStylePatch(shadowIntensity = activeEl.shadowIntensity), true)
        },
        testTagPrefix = "shadow_intensity",
      )
      Text("Shadow Color", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
      AccessibleColorSwatchRow(
        selectedHex = activeEl.shadowColorHex,
        swatches = StudioPalette.effectSwatches,
        onSelectColor = { hex -> onPatchStyle(ElementStylePatch(shadowColorHex = hex), true) },
        swatchTagPrefix = "shadow_color",
      )
    }

    // 3. Neon / Warm Glow
    EffectSectionCard(
      title = "Ambient Glow",
      subtitle = "Outer neon or warm bloom halo around highlighted words",
      checked = hasGlow,
      switchTag = "toggle_glow_switch",
      onCheckedChange = { enabled ->
        if (enabled) {
          onPatchStyle(
            ElementStylePatch(
              glowColorHex = activeEl.glowColorHex ?: activeEl.colorHex.takeIf { it != StudioPalette.SHADOW } ?: StudioPalette.YELLOW,
            ),
            true,
          )
        } else {
          onPatchStyle(ElementStylePatch(clearGlow = true), true)
        }
      },
    ) {
      AccessibleSliderControl(
        label = "Glow Intensity",
        valueText = "${(activeEl.glowIntensity * 100f).roundToInt()}%",
        value = activeEl.glowIntensity,
        valueRange = 0.3f..2.0f,
        step = 0.1f,
        onValueChange = { onPatchStyle(ElementStylePatch(glowIntensity = it), false) },
        onValueChangeFinished = {
          onPatchStyle(ElementStylePatch(glowIntensity = activeEl.glowIntensity), true)
        },
        testTagPrefix = "glow_intensity",
      )
      Text("Glow Color", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
      AccessibleColorSwatchRow(
        selectedHex = activeEl.glowColorHex ?: StudioPalette.YELLOW,
        swatches = StudioPalette.textSwatches,
        onSelectColor = { hex -> onPatchStyle(ElementStylePatch(glowColorHex = hex), true) },
        swatchTagPrefix = "glow_color",
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypographyLayoutTab(
  activeComp: CaptionComposition,
  activeEl: CompositionElement,
  onPatchStyle: (ElementStylePatch, Boolean) -> Unit,
  onToggleCaptionDepthMask: () -> Unit,
) {
  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = StudioCoralContainer,
      selectedLabelColor = StudioCoralLight,
      containerColor = StudioElevated,
      labelColor = StudioTextSecondary,
    )

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Placement Regions
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Caption Safe Region", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        SafeRegion.entries.filter { it != SafeRegion.CUSTOM }.forEach { region ->
          FilterChip(
            selected = activeComp.region == region,
            onClick = { onPatchStyle(ElementStylePatch(safeRegion = region), true) },
            shape = CircleShape,
            colors = chipColors,
            border =
              FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = activeComp.region == region,
                borderColor = StudioHairline,
                selectedBorderColor = StudioCoral.copy(alpha = 0.7f),
              ),
            label = { Text(region.label, style = MaterialTheme.typography.labelSmall) },
          )
        }
      }
    }

    // Fine Transform: Tilt, Horizontal & Vertical Nudge
    AccessibleSliderControl(
      label = "Tilt / Rotation",
      valueText = "${activeEl.rotationDeg.roundToInt()}°",
      value = activeEl.rotationDeg,
      valueRange = -25f..25f,
      step = 1f,
      onValueChange = { onPatchStyle(ElementStylePatch(rotationDeg = it), false) },
      onValueChangeFinished = {
        onPatchStyle(ElementStylePatch(rotationDeg = activeEl.rotationDeg), true)
      },
      testTagPrefix = "rotation",
    )

    AccessibleSliderControl(
      label = "Horizontal Nudge (X)",
      valueText = String.format(Locale.US, "%+.2f em", activeEl.offsetXEm),
      value = activeEl.offsetXEm,
      valueRange = -0.50f..0.50f,
      step = 0.05f,
      onValueChange = { onPatchStyle(ElementStylePatch(offsetXEm = it), false) },
      onValueChangeFinished = {
        onPatchStyle(ElementStylePatch(offsetXEm = activeEl.offsetXEm), true)
      },
      testTagPrefix = "offset_x",
    )

    AccessibleSliderControl(
      label = "Vertical Nudge (Y)",
      valueText = String.format(Locale.US, "%+.2f em", activeEl.offsetYEm),
      value = activeEl.offsetYEm,
      valueRange = -0.50f..0.50f,
      step = 0.05f,
      onValueChange = { onPatchStyle(ElementStylePatch(offsetYEm = it), false) },
      onValueChangeFinished = {
        onPatchStyle(ElementStylePatch(offsetYEm = activeEl.offsetYEm), true)
      },
      testTagPrefix = "offset_y",
    )

    if (activeEl.rotationDeg != 0f || activeEl.offsetXEm != 0f || activeEl.offsetYEm != 0f) {
      OutlinedButton(
        onClick = {
          onPatchStyle(
            ElementStylePatch(rotationDeg = 0f, offsetXEm = 0f, offsetYEm = 0f),
            true,
          )
        },
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(42.dp),
      ) {
        Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = StudioCoralLight, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Center & Level Layer", color = StudioTextPrimary, style = MaterialTheme.typography.labelMedium)
      }
    }

    // Per-Layer Depth Order (In Front vs Behind Subject)
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(StudioElevated)
          .border(1.dp, StudioHairline, RoundedCornerShape(16.dp))
          .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("Layer Depth Order", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DepthLayer.entries.forEach { layer ->
          val isChosen = activeEl.depthLayer == layer
          FilterChip(
            selected = isChosen,
            onClick = { onPatchStyle(ElementStylePatch(depthLayer = layer), true) },
            shape = CircleShape,
            colors = chipColors,
            border =
              FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isChosen,
                borderColor = StudioHairline,
                selectedBorderColor = StudioCoral.copy(alpha = 0.7f),
              ),
            label = { Text(layer.label, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.weight(1f),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Subject Depth Mask", style = MaterialTheme.typography.labelLarge, color = StudioTextPrimary)
          Text(
            "Tucks behind-subject layers behind the speaker",
            style = MaterialTheme.typography.bodySmall,
            color = StudioTextSecondary,
          )
        }
        Switch(
          checked = activeComp.hasBehindSubjectElements(),
          onCheckedChange = { onToggleCaptionDepthMask() },
          colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StudioCoral),
          modifier = Modifier.testTag("toggle_depth_mask_chip"),
        )
      }
    }
  }
}

@Composable
private fun EffectSectionCard(
  title: String,
  subtitle: String,
  checked: Boolean,
  switchTag: String,
  onCheckedChange: (Boolean) -> Unit,
  content: @Composable () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(StudioElevated)
        .border(
          width = 1.dp,
          color = if (checked) StudioCoral.copy(alpha = 0.45f) else StudioHairline,
          shape = RoundedCornerShape(16.dp),
        )
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
      }
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StudioCoral),
        modifier = Modifier.testTag(switchTag),
      )
    }

    AnimatedVisibility(
      visible = checked,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
      }
    }
  }
}

@Composable
fun StyleGlyphToggle(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  glyphText: String? = null,
  testTag: String? = null,
) {
  Box(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .size(40.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) StudioCoralContainer else StudioElevated)
        .border(
          width = if (selected) 1.3.dp else 1.dp,
          color = if (selected) StudioCoral else StudioHairline,
          shape = RoundedCornerShape(12.dp),
        )
        .clickable(role = Role.Checkbox, onClick = onClick)
        .semantics {
          this.selected = selected
          contentDescription = label + if (selected) ", enabled" else ", disabled"
        }
        .let { base -> if (testTag != null) base.testTag(testTag) else base },
    contentAlignment = Alignment.Center,
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) StudioCoralLight else StudioTextPrimary,
        modifier = Modifier.size(19.dp),
      )
    } else if (glyphText != null) {
      Text(
        text = glyphText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) StudioCoralLight else StudioTextPrimary,
      )
    }
  }
}

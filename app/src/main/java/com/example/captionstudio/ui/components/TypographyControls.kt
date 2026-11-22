package com.example.captionstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.ColorSwatchOption
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.typography.FontRegistry
import com.example.captionstudio.typography.StudioFontDescriptor
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

/**
 * Compact header letting users pick which kinetic typography layer (word/phrase) in the active
 * caption they are styling, plus a single-tap Apply Scope toggle and Reset action.
 */
@Composable
fun ActiveLayerSelectorBar(
  activeComp: CaptionComposition,
  activeEl: CompositionElement?,
  applyScope: ApplyScope,
  onSelectElement: (String) -> Unit,
  onScopeChange: (ApplyScope) -> Unit,
  onAddElement: (() -> Unit)? = null,
  onResetStyle: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val scopeShortLabel =
    when (applyScope) {
      ApplyScope.SINGLE_ELEMENT -> stringResource(R.string.typo_scope_layer)
      ApplyScope.SINGLE_COMPOSITION -> stringResource(R.string.typo_scope_caption)
      ApplyScope.ALL_COMPOSITIONS -> stringResource(R.string.typo_scope_all)
    }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Layers,
          contentDescription = null,
          tint = StudioCoralLight,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = stringResource(R.string.typo_select_layer),
          style = MaterialTheme.typography.labelMedium,
          color = StudioTextSecondary,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        // Scope cycler pill (This Layer -> This Caption -> All Captions)
        val nextScope =
          when (applyScope) {
            ApplyScope.SINGLE_ELEMENT -> ApplyScope.SINGLE_COMPOSITION
            ApplyScope.SINGLE_COMPOSITION -> ApplyScope.ALL_COMPOSITIONS
            ApplyScope.ALL_COMPOSITIONS -> ApplyScope.SINGLE_ELEMENT
          }
        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(
                if (applyScope == ApplyScope.SINGLE_ELEMENT) StudioElevated
                else StudioCoralContainer
              )
              .border(
                width = 1.dp,
                color =
                  if (applyScope == ApplyScope.SINGLE_ELEMENT) StudioHairlineStrong
                  else StudioCoral.copy(alpha = 0.7f),
                shape = CircleShape,
              )
              .clickable(
                role = Role.Button,
                onClickLabel = "Change edit scope",
                onClick = { onScopeChange(nextScope) },
              )
              .padding(horizontal = 11.dp, vertical = 6.dp)
              .testTag("typography_scope_toggle"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Apply: $scopeShortLabel",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color =
              if (applyScope == ApplyScope.SINGLE_ELEMENT) StudioTextPrimary else StudioCoralLight,
            maxLines = 1,
          )
        }

        if (onResetStyle != null && activeEl != null) {
          IconButton(
            onClick = onResetStyle,
            modifier =
              Modifier.size(36.dp)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(StudioElevated)
                .border(1.dp, StudioHairline, CircleShape)
                .testTag("reset_layer_style_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.RestartAlt,
              contentDescription = stringResource(R.string.typo_reset_layer_cd),
              tint = StudioTextSecondary,
              modifier = Modifier.size(17.dp),
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      activeComp.elements.forEach { el ->
        val isChosen = el.id == activeEl?.id
        val dotColor = remember(el.colorHex) { parseComposeHexColor(el.colorHex) }
        val fontDesc = remember(el.fontFamilyId) { FontRegistry.findById(el.fontFamilyId) }
        val chipInteraction = remember { MutableInteractionSource() }

        Row(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .studioPressScale(interactionSource = chipInteraction)
              .clip(CircleShape)
              .background(if (isChosen) StudioCoralContainer else StudioElevated)
              .border(
                width = if (isChosen) 1.4.dp else 1.dp,
                color = if (isChosen) StudioCoral else StudioHairline,
                shape = CircleShape,
              )
              .clickable(
                interactionSource = chipInteraction,
                indication = ripple(bounded = true, color = Color.White),
                onClick = { onSelectElement(el.id) },
              )
              .semantics {
                selected = isChosen
                contentDescription =
                  "Layer ${el.text}, font ${fontDesc.displayName}" +
                    if (isChosen) ", selected" else ""
              }
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .testTag("layer_chip_${el.id}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          Box(
            modifier =
              Modifier.size(9.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(0.8.dp, Color.White.copy(alpha = 0.35f), CircleShape)
          )
          Text(
            text = el.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isChosen) StudioCoralLight else StudioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (onAddElement != null) {
        Row(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(StudioCard)
              .border(1.dp, StudioHairlineStrong, CircleShape)
              .clickable(onClick = onAddElement)
              .padding(horizontal = 11.dp, vertical = 8.dp)
              .testTag("quick_add_layer_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = StudioCoralLight,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = stringResource(R.string.typo_add_layer),
            style = MaterialTheme.typography.labelMedium,
            color = StudioTextPrimary,
          )
        }
      }
    }
  }
}

/**
 * Horizontal scrolling font selector that renders each available typeface in its real font family
 * so creators can browse and switch kinetic layer typefaces live with a single tap.
 */
@Composable
fun HorizontalFontSelectorStrip(
  selectedFontId: String,
  onSelectFont: (String) -> Unit,
  modifier: Modifier = Modifier,
  fontsVersion: Int = 0,
  showRoleFilters: Boolean = true,
  compact: Boolean = false,
  previewItalic: Boolean = false,
  previewUppercase: Boolean = false,
) {
  var roleFilter by remember { mutableStateOf<FontRole?>(null) }
  val fonts =
    remember(roleFilter, fontsVersion) {
      FontRegistry.allFonts().filter { roleFilter == null || it.role == roleFilter }
    }
  val listState = rememberLazyListState()

  LaunchedEffect(selectedFontId, roleFilter, fonts.size) {
    val idx = fonts.indexOfFirst { it.id == selectedFontId }
    if (idx >= 0) {
      val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == idx }
      if (!visible) {
        listState.animateScrollToItem(index = (idx - 1).coerceAtLeast(0))
      }
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (showRoleFilters) {
      val chipColors =
        FilterChipDefaults.filterChipColors(
          selectedContainerColor = StudioCoralContainer,
          selectedLabelColor = StudioCoralLight,
          containerColor = StudioElevated,
          labelColor = StudioTextSecondary,
        )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilterChip(
          selected = roleFilter == null,
          onClick = { roleFilter = null },
          shape = CircleShape,
          colors = chipColors,
          border =
            FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = roleFilter == null,
              borderColor = StudioHairline,
              selectedBorderColor = StudioCoral.copy(alpha = 0.7f),
            ),
          label = { Text("All (${FontRegistry.allFonts().size})", style = MaterialTheme.typography.labelSmall) },
          modifier = Modifier.testTag("font_filter_all"),
        )
        listOf(
          FontRole.HEAVY to "Display",
          FontRole.SUPPORTING to "Clean Sans",
          FontRole.EXPRESSIVE to "Script & Serif",
        ).forEach { (role, title) ->
          FilterChip(
            selected = roleFilter == role,
            onClick = { roleFilter = if (roleFilter == role) null else role },
            shape = CircleShape,
            colors = chipColors,
            border =
              FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = roleFilter == role,
                borderColor = StudioHairline,
                selectedBorderColor = StudioCoral.copy(alpha = 0.7f),
              ),
            label = { Text(title, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.testTag("font_filter_${role.name.lowercase()}"),
          )
        }
      }
    }

    LazyRow(
      state = listState,
      modifier = Modifier.fillMaxWidth().testTag("horizontal_font_selector"),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(end = 4.dp),
    ) {
      items(fonts, key = { it.id }) { font ->
        FontSelectorTile(
          font = font,
          isSelected = font.id == selectedFontId,
          compact = compact,
          previewItalic = previewItalic,
          previewUppercase = previewUppercase,
          onClick = { onSelectFont(font.id) },
        )
      }
    }
  }
}

@Composable
private fun FontSelectorTile(
  font: StudioFontDescriptor,
  isSelected: Boolean,
  compact: Boolean,
  previewItalic: Boolean,
  previewUppercase: Boolean,
  onClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val shape = RoundedCornerShape(16.dp)
  val cardWidth = if (compact) 94.dp else 108.dp
  val cardHeight = if (compact) 70.dp else 82.dp
  val glyphSample = if (previewUppercase) "AA" else "Aa"

  Box(
    modifier =
      Modifier.width(cardWidth)
        .height(cardHeight)
        .studioPressScale(interactionSource = interactionSource)
        .clip(shape)
        .background(if (isSelected) Color(0xFF201516) else StudioElevated)
        .border(
          width = if (isSelected) 1.8.dp else 1.dp,
          color = if (isSelected) StudioCoral else StudioHairline,
          shape = shape,
        )
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          role = Role.RadioButton,
          onClick = onClick,
        )
        .semantics {
          selected = isSelected
          contentDescription =
            "${font.displayName} typeface, ${font.styleNote}" +
              if (isSelected) ", selected" else ""
        }
        .padding(horizontal = 8.dp, vertical = 7.dp)
        .testTag("font_selector_chip_${font.id}"),
  ) {
    if (isSelected) {
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .size(16.dp)
            .clip(CircleShape)
            .background(StudioCoral),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Check,
          contentDescription = null,
          tint = StudioObsidian,
          modifier = Modifier.size(11.dp),
        )
      }
    }

    Column(
      modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = glyphSample,
        fontFamily = font.composeFontFamily,
        fontStyle = if (previewItalic && font.supportsItalic) FontStyle.Italic else FontStyle.Normal,
        fontSize = if (compact) 22.sp else 26.sp,
        lineHeight = if (compact) 25.sp else 29.sp,
        color = if (isSelected) StudioTextPrimary else StudioTextPrimary.copy(alpha = 0.92f),
        maxLines = 1,
      )
      Spacer(Modifier.height(3.dp))
      Text(
        text = font.displayName,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isSelected) StudioCoralLight else StudioTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Accessible precision slider with flanking - / + stepper buttons (48dp touch targets) and
 * crisp live value readout so users can either scrub smoothly or tap exact increments.
 */
@Composable
fun AccessibleSliderControl(
  label: String,
  valueText: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  step: Float,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit = {},
  modifier: Modifier = Modifier,
  testTagPrefix: String? = null,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = StudioTextSecondary,
      )
      Text(
        text = valueText,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = StudioCoralLight,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      val decDesc = stringResource(R.string.typo_decrease_cd, label)
      val incDesc = stringResource(R.string.typo_increase_cd, label)

      IconButton(
        onClick = {
          val next = (value - step).coerceIn(valueRange.start, valueRange.endInclusive)
          onValueChange(next)
          onValueChangeFinished()
        },
        enabled = value > valueRange.start + 0.0001f,
        modifier =
          Modifier.size(36.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(StudioElevated)
            .border(1.dp, StudioHairline, CircleShape)
            .let { base -> if (testTagPrefix != null) base.testTag("${testTagPrefix}_dec") else base },
      ) {
        Icon(
          imageVector = Icons.Rounded.Remove,
          contentDescription = decDesc,
          tint = StudioTextPrimary,
          modifier = Modifier.size(16.dp),
        )
      }

      Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        colors =
          SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = StudioCoral,
            inactiveTrackColor = StudioElevated,
          ),
        modifier =
          Modifier.weight(1f)
            .semantics {
              contentDescription = label
              stateDescription = valueText
            }
            .let { base -> if (testTagPrefix != null) base.testTag("${testTagPrefix}_slider") else base },
      )

      IconButton(
        onClick = {
          val next = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
          onValueChange(next)
          onValueChangeFinished()
        },
        enabled = value < valueRange.endInclusive - 0.0001f,
        modifier =
          Modifier.size(36.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(StudioElevated)
            .border(1.dp, StudioHairline, CircleShape)
            .let { base -> if (testTagPrefix != null) base.testTag("${testTagPrefix}_inc") else base },
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = incDesc,
          tint = StudioTextPrimary,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

/**
 * Horizontal scrolling swatch bar backed by [StudioColorSwatchSelector] (circular color wheel,
 * brand palette grid, and custom hex input).
 */
@Composable
fun AccessibleColorSwatchRow(
  selectedHex: String,
  swatches: List<ColorSwatchOption>,
  onSelectColor: (String) -> Unit,
  modifier: Modifier = Modifier,
  allowCustomPicker: Boolean = true,
  swatchTagPrefix: String = "swatch",
) {
  if (allowCustomPicker) {
    StudioColorSwatchSelector(
      selectedHex = selectedHex,
      swatches = swatches,
      onColorChange = { hex, _ -> onSelectColor(hex) },
      modifier = modifier,
      swatchTagPrefix = swatchTagPrefix,
    )
  } else {
    Row(
      modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      swatches.forEach { swatch ->
        val isSelected = selectedHex.equals(swatch.hex, ignoreCase = true)
        val colorVal = remember(swatch.hex) { parseComposeHexColor(swatch.hex) }
        val checkTint = if (colorVal.luminance() > 0.52f) StudioObsidian else Color.White

        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(38.dp)
              .clip(CircleShape)
              .background(colorVal)
              .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) StudioCoral else StudioHairlineStrong,
                shape = CircleShape,
              )
              .clickable(role = Role.RadioButton) { onSelectColor(swatch.hex) }
              .semantics {
                selected = isSelected
                contentDescription = "${swatch.label} color" + if (isSelected) ", selected" else ""
              }
              .testTag("${swatchTagPrefix}_${swatch.label.lowercase().replace(" ", "_")}"),
          contentAlignment = Alignment.Center,
        ) {
          if (isSelected) {
            Icon(
              imageVector = Icons.Rounded.Check,
              contentDescription = null,
              tint = checkTint,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

fun parseComposeHexColor(hex: String): Color {
  return try {
    Color(android.graphics.Color.parseColor(hex))
  } catch (_: Exception) {
    Color.White
  }
}

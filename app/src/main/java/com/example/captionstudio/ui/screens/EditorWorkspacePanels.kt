package com.example.captionstudio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.ElementStylePatch
import com.example.captionstudio.domain.model.GeminiSettings
import com.example.captionstudio.domain.model.GeminiUsageMode
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.media.AutoTranscribeUiState
import com.example.captionstudio.media.OfflineVoicePackStatus
import com.example.captionstudio.media.OfflineVoskModelManager
import com.example.captionstudio.media.SpeechRecognitionState
import com.example.captionstudio.media.VoskTextSegment
import com.example.captionstudio.presets.PresetCatalog
import com.example.captionstudio.typography.CuratedFontPairing
import com.example.captionstudio.typography.FontRegistry
import com.example.captionstudio.ui.components.AccessibleSliderControl
import com.example.captionstudio.ui.components.ActiveLayerSelectorBar
import com.example.captionstudio.ui.components.AutoTranscribeControlCard
import com.example.captionstudio.ui.components.HorizontalFontSelectorStrip
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioSubtlePill
import com.example.captionstudio.ui.components.StyleGlyphToggle
import com.example.captionstudio.ui.components.TypographyInspectorDeck
import com.example.captionstudio.ui.components.studioPressScale
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
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWarning
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import kotlin.math.roundToInt

/**
 * Signature "Caption Style" glass card matching the reference mockup with stylized Aa tiles
 * that apply presets live in one tap, plus "See all ->" to open the full Presets gallery.
 */
@Composable
internal fun StyleQuickPanel(
  project: CaptionProject,
  activeComp: CaptionComposition?,
  selectedElementId: String?,
  applyScope: ApplyScope,
  fontsVersion: Int,
  isBusy: Boolean,
  autoTranscribeState: AutoTranscribeUiState,
  geminiSettings: GeminiSettings,
  onSelectElement: (String) -> Unit,
  onScopeChange: (ApplyScope) -> Unit,
  onSelectFontForLayer: (String, String) -> Unit,
  onApplyPreset: (String) -> Unit,
  onOpenPresets: () -> Unit,
  onOpenFontTab: () -> Unit,
  onGenerate: (Boolean) -> Unit,
  onAutoTranscribe: (Boolean) -> Unit,
  onSelectComposition: (CaptionComposition) -> Unit,
  onOpenTimingTab: () -> Unit,
) {
  val activeEl =
    activeComp?.elements?.firstOrNull { it.id == selectedElementId }
      ?: activeComp?.elements?.firstOrNull()

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    // Primary "Caption Style" Card matching reference mockup
    StudioGlassCard(
      modifier = Modifier.fillMaxWidth(),
      cornerRadius = 22.dp,
      containerColor = Color(0xFF111115),
      borderColor = StudioHairline,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = StudioTextPrimary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Caption Style",
              style = MaterialTheme.typography.titleSmall,
              fontSize = 15.sp,
              fontWeight = FontWeight.Medium,
              color = StudioTextPrimary,
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.clip(CircleShape)
                .clickable(onClick = onOpenPresets)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("open_presets_button"),
          ) {
            Text(
              text = "See all",
              style = MaterialTheme.typography.labelMedium,
              color = StudioTextSecondary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = "See all caption presets",
              tint = StudioTextSecondary,
              modifier = Modifier.size(14.dp),
            )
          }
        }

        // First 4 presets rendered in clean 4-column visual symmetry matching mockup (scrollable for all 8)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          PresetCatalog.presets.forEachIndexed { index, preset ->
            val selected = preset.id == project.presetId
            CaptionStyleTile(
              presetId = preset.id,
              presetName = preset.name,
              styleVariantIndex = index,
              selected = selected,
              onClick = { onApplyPreset(preset.id) },
            )
          }
        }
      }
    }

    // Immediate Horizontal Scrolling Font Selector for the Selected Kinetic Layer
    if (activeComp != null && activeEl != null) {
      StudioGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        containerColor = Color(0xFF111115),
        borderColor = StudioHairline,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val activeFont = remember(activeEl.fontFamilyId, fontsVersion) { FontRegistry.findById(activeEl.fontFamilyId) }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Layer Typeface",
                style = MaterialTheme.typography.titleSmall,
                color = StudioTextPrimary,
              )
              Text(
                text = "\"${activeEl.text}\" • ${activeFont.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = StudioCoralLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier.clip(CircleShape)
                  .clickable(onClick = onOpenFontTab)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("style_open_font_panel_button"),
            ) {
              Text(
                text = "More",
                style = MaterialTheme.typography.labelMedium,
                color = StudioTextSecondary,
              )
              Spacer(Modifier.width(3.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Open full font controls",
                tint = StudioTextSecondary,
                modifier = Modifier.size(14.dp),
              )
            }
          }

          if (activeComp.elements.size > 1) {
            ActiveLayerSelectorBar(
              activeComp = activeComp,
              activeEl = activeEl,
              applyScope = applyScope,
              onSelectElement = onSelectElement,
              onScopeChange = onScopeChange,
            )
          }

          HorizontalFontSelectorStrip(
            selectedFontId = activeEl.fontFamilyId,
            onSelectFont = { fontId -> onSelectFontForLayer(activeEl.id, fontId) },
            fontsVersion = fontsVersion,
            showRoleFilters = false,
            compact = true,
            previewItalic = activeEl.isItalic,
            previewUppercase = activeEl.uppercase,
          )
        }
      }
    }

    // Contextual auto-transcribe prompt when video has audio and no transcript yet
    if (project.videoUriString.isNotBlank() && project.transcriptText.isBlank()) {
      StudioGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        borderColor = StudioCoral.copy(alpha = 0.35f),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Auto-Transcribe Audio",
            style = MaterialTheme.typography.titleSmall,
            color = StudioCoralLight,
          )
          Text(
            text = "Extract speech directly from your video with millisecond word sync.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioTextSecondary,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = { onAutoTranscribe(false) },
              enabled = !autoTranscribeState.isRunning && project.metadata.hasAudio,
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
              modifier = Modifier.weight(1f).height(44.dp),
            ) {
              Text("Transcribe Offline", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
            }
            if (geminiSettings.enabled) {
              OutlinedButton(
                onClick = { onAutoTranscribe(true) },
                enabled = !autoTranscribeState.isRunning && project.metadata.hasAudio,
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(44.dp),
              ) {
                Text("AI Transcribe", maxLines = 1, softWrap = false, color = StudioTextPrimary)
              }
            }
          }
        }
      }
    }

    // Compact Caption Timeline Strip + Quick Generate Action
    StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (project.compositions.isEmpty()) "Captions"
              else "Captions (${project.compositions.size})",
            style = MaterialTheme.typography.titleSmall,
            color = StudioTextPrimary,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.clip(CircleShape)
                .clickable(onClick = onOpenTimingTab)
                .padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Text(
              text = "Edit timeline",
              style = MaterialTheme.typography.labelMedium,
              color = StudioCoralLight,
            )
            Spacer(Modifier.width(3.dp))
            Icon(
              Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
              tint = StudioCoralLight,
              modifier = Modifier.size(13.dp),
            )
          }
        }

        if (project.compositions.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            project.compositions.forEach { comp ->
              val isCurrent = comp.id == activeComp?.id
              Surface(
                color = if (isCurrent) StudioCoralContainer else StudioElevated,
                shape = RoundedCornerShape(12.dp),
                modifier =
                  Modifier.border(
                      1.dp,
                      if (isCurrent) StudioCoral else StudioHairline,
                      RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelectComposition(comp) },
              ) {
                Text(
                  text = comp.originalText.take(22),
                  style = MaterialTheme.typography.labelMedium,
                  color = if (isCurrent) StudioCoralLight else StudioTextPrimary,
                  maxLines = 1,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { onGenerate(false) },
            enabled = !isBusy,
            shape = CircleShape,
            modifier = Modifier.weight(1f).height(44.dp).testTag("generate_compositions_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                color = StudioCoral,
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
              )
              Spacer(Modifier.width(8.dp))
              Text("Composing...", color = StudioTextPrimary, maxLines = 1, softWrap = false)
            } else {
              Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = StudioCoral,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text =
                  if (geminiSettings.enabled && geminiSettings.usageMode != GeminiUsageMode.OFFLINE)
                    "Regenerate (${geminiSettings.usageMode.label})"
                  else "Generate Captions",
                color = StudioTextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          if (geminiSettings.enabled && geminiSettings.usageMode != GeminiUsageMode.OFFLINE) {
            OutlinedButton(
              onClick = { onGenerate(true) },
              enabled = !isBusy,
              shape = CircleShape,
              modifier = Modifier.height(44.dp),
            ) {
              Text("Offline", color = StudioTextSecondary, maxLines = 1, softWrap = false)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CaptionStyleTile(
  presetId: String,
  presetName: String,
  styleVariantIndex: Int,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val shape = RoundedCornerShape(16.dp)

  Box(
    modifier =
      Modifier.width(76.dp)
        .height(74.dp)
        .studioPressScale(interactionSource = interactionSource)
        .clip(shape)
        .background(if (selected) Color(0xFF171416) else Color(0xFF151519))
        .border(
          width = if (selected) 1.6.dp else 1.dp,
          color = if (selected) StudioCoral else StudioHairline,
          shape = shape,
        )
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onClick,
        )
        .testTag("quick_style_$presetId"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      when (styleVariantIndex % 4) {
        0 -> {
          // Clean solid Aa
          Text(
            text = "Aa",
            fontSize = 23.sp,
            fontWeight = FontWeight.Medium,
            color = StudioTextPrimary,
          )
        }
        1 -> {
          // Underlined Aa
          Text(
            text = "Aa",
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
            textDecoration = TextDecoration.Underline,
          )
        }
        2 -> {
          // Soft pill highlight Aa
          Box(
            modifier =
              Modifier.clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Aa",
              fontSize = 19.sp,
              fontWeight = FontWeight.Medium,
              color = StudioTextPrimary,
            )
          }
        }
        else -> {
          // Outlined hollow Aa
          Text(
            text = "Aa",
            style =
              TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = StudioTextPrimary.copy(alpha = 0.88f),
                drawStyle = Stroke(width = 2.4f),
              ),
          )
        }
      }
      if (styleVariantIndex >= 4) {
        Spacer(Modifier.height(2.dp))
        Text(
          text = presetName.lowercase().replaceFirstChar { it.uppercase() },
          style = MaterialTheme.typography.labelSmall,
          fontSize = 9.sp,
          color = if (selected) StudioCoralLight else StudioTextSecondary,
          maxLines = 1,
        )
      }
    }
  }
}

/**
 * Complete Kinetic Typography Customizer (Tool Tile 2: Customize)
 * Organizes every typography property into 4 calm segmented tabs (`Text | Color | Effects | Layout`)
 * so everything is customizable and accessible without feeling cluttered.
 */
@Composable
internal fun ThemeWorkspacePanel(
  activeComp: CaptionComposition?,
  selectedElementId: String?,
  applyScope: ApplyScope,
  onScopeChange: (ApplyScope) -> Unit,
  onSelectElement: (String) -> Unit,
  onAddElement: () -> Unit,
  onUpdateElementText: (String, String) -> Unit,
  onDeleteElement: (String) -> Unit,
  onPatchStyle: (String, ElementStylePatch, Boolean) -> Unit,
  onResetElementStyle: (String) -> Unit,
  onToggleDepthMask: () -> Unit,
  onOpenTypographyStudio: () -> Unit,
  onOpenAnimationStudio: () -> Unit,
  onOpenPresets: () -> Unit,
) {
  if (activeComp == null) {
    StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("No caption selected", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
        Text(
          "Generate or select a caption to customize colors, motion effects, outlines, shadows, glow, and depth.",
          style = MaterialTheme.typography.bodySmall,
          color = StudioTextSecondary,
        )
      }
    }
    return
  }

  val activeEl =
    activeComp.elements.firstOrNull { it.id == selectedElementId } ?: activeComp.elements.firstOrNull()

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ActiveLayerSelectorBar(
          activeComp = activeComp,
          activeEl = activeEl,
          applyScope = applyScope,
          onSelectElement = onSelectElement,
          onScopeChange = onScopeChange,
          onAddElement = onAddElement,
          onResetStyle = activeEl?.let { el -> { onResetElementStyle(el.id) } },
        )

        if (activeEl != null) {
          TypographyInspectorDeck(
            activeComp = activeComp,
            activeEl = activeEl,
            onPatchStyle = { patch, record -> onPatchStyle(activeEl.id, patch, record) },
            onUpdateText = { newText -> onUpdateElementText(activeEl.id, newText) },
            onDeleteElement =
              if (activeComp.elements.size > 1) {
                { onDeleteElement(activeEl.id) }
              } else null,
            onToggleCaptionDepthMask = onToggleDepthMask,
            onOpenAnimationStudio = onOpenAnimationStudio,
            showInlinePreview = true,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onOpenPresets,
            shape = CircleShape,
            modifier = Modifier.weight(1f).height(44.dp),
          ) {
            Text("All Presets", color = StudioTextPrimary, maxLines = 1, softWrap = false)
          }
          OutlinedButton(
            onClick = onOpenTypographyStudio,
            shape = CircleShape,
            modifier = Modifier.weight(1f).height(44.dp),
          ) {
            Icon(Icons.Outlined.Palette, contentDescription = null, tint = StudioCoral, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Full Studio", color = StudioTextPrimary, maxLines = 1, softWrap = false)
          }
        }
      }
    }
  }
}

/**
 * Font & Horizontal Typeface Selector Panel (Tool Tile 3: Font)
 * Lets users browse all bundled & downloaded fonts in a horizontal scrolling strip,
 * quickly tune size/weight/style, or apply curated 3-font pairings in one tap.
 */
@Composable
internal fun FontWorkspacePanel(
  project: CaptionProject,
  activeComp: CaptionComposition?,
  selectedElementId: String?,
  applyScope: ApplyScope,
  fontsVersion: Int,
  onScopeChange: (ApplyScope) -> Unit,
  onSelectElement: (String) -> Unit,
  onAddElement: () -> Unit,
  onPatchStyle: (String, ElementStylePatch, Boolean) -> Unit,
  onResetElementStyle: (String) -> Unit,
  onApplyPairing: (CuratedFontPairing) -> Unit,
  onOpenTypographyStudio: () -> Unit,
) {
  val activeEl =
    activeComp?.elements?.firstOrNull { it.id == selectedElementId }
      ?: activeComp?.elements?.firstOrNull()

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    // Card 1: Horizontal Scrolling Font Selector for the Selected Kinetic Layer
    StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (activeComp != null && activeEl != null) {
          ActiveLayerSelectorBar(
            activeComp = activeComp,
            activeEl = activeEl,
            applyScope = applyScope,
            onSelectElement = onSelectElement,
            onScopeChange = onScopeChange,
            onAddElement = onAddElement,
            onResetStyle = { onResetElementStyle(activeEl.id) },
          )

          val activeFont =
            remember(activeEl.fontFamilyId, fontsVersion) {
              FontRegistry.findById(activeEl.fontFamilyId)
            }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Typeface",
              style = MaterialTheme.typography.titleSmall,
              color = StudioTextPrimary,
            )
            StudioSubtlePill(text = activeFont.displayName, tint = StudioCoralLight)
          }

          HorizontalFontSelectorStrip(
            selectedFontId = activeEl.fontFamilyId,
            onSelectFont = { fontId ->
              onPatchStyle(activeEl.id, ElementStylePatch(fontFamilyId = fontId), true)
            },
            fontsVersion = fontsVersion,
            showRoleFilters = true,
            compact = false,
            previewItalic = activeEl.isItalic,
            previewUppercase = activeEl.uppercase,
          )

          // Quick Typeface Polish Strip (Size + Format Toggles) right under font selector
          AccessibleSliderControl(
            label = "Size",
            valueText = "${activeEl.fontSizeSp.roundToInt()} sp",
            value = activeEl.fontSizeSp,
            valueRange = 12f..68f,
            step = 2f,
            onValueChange = { onPatchStyle(activeEl.id, ElementStylePatch(fontSizeSp = it), false) },
            onValueChangeFinished = {
              onPatchStyle(activeEl.id, ElementStylePatch(fontSizeSp = activeEl.fontSizeSp), true)
            },
            testTagPrefix = "font_panel_size",
          )

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
                onClick = {
                  onPatchStyle(activeEl.id, ElementStylePatch(isItalic = !activeEl.isItalic), true)
                },
              )
              StyleGlyphToggle(
                label = "Uppercase",
                glyphText = "AA",
                selected = activeEl.uppercase,
                onClick = {
                  onPatchStyle(activeEl.id, ElementStylePatch(uppercase = !activeEl.uppercase), true)
                },
              )
              StyleGlyphToggle(
                label = "Underline",
                icon = Icons.Rounded.FormatUnderlined,
                selected = activeEl.isUnderlined,
                onClick = {
                  onPatchStyle(activeEl.id, ElementStylePatch(isUnderlined = !activeEl.isUnderlined), true)
                },
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf(400 to "Reg", 600 to "Semi", 900 to "Black").forEach { (w, lbl) ->
                val isSelectedWeight = activeEl.fontWeight == w
                Box(
                  modifier =
                    Modifier.clip(CircleShape)
                      .background(if (isSelectedWeight) StudioCoralContainer else StudioElevated)
                      .border(
                        1.dp,
                        if (isSelectedWeight) StudioCoral else StudioHairline,
                        CircleShape,
                      )
                      .clickable {
                        onPatchStyle(activeEl.id, ElementStylePatch(fontWeight = w), true)
                      }
                      .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                  Text(
                    text = lbl,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelectedWeight) StudioCoralLight else StudioTextPrimary,
                  )
                }
              }
            }
          }
        } else {
          Text(
            text = "Select or generate a caption to change individual layer typefaces.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioTextSecondary,
          )
        }
      }
    }

    // Card 2: Curated 3-Font Pairings & Full Browser
    StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Curated Font Pairings",
            style = MaterialTheme.typography.titleSmall,
            color = StudioTextPrimary,
          )
          StudioSubtlePill(text = "${FontRegistry.allFonts().size} fonts")
        }

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FontRegistry.curatedPairings.forEach { pairing ->
            val isCurrent =
              project.fontOverrides.heavyFontId == pairing.heavyFontId &&
                project.fontOverrides.supportingFontId == pairing.supportingFontId
            val heavyFont = remember(pairing.heavyFontId) { FontRegistry.findById(pairing.heavyFontId) }
            val expressiveFont = remember(pairing.expressiveFontId) { FontRegistry.findById(pairing.expressiveFontId) }

            Box(
              modifier =
                Modifier.width(172.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .background(if (isCurrent) StudioCoralContainer else StudioElevated)
                  .border(
                    1.dp,
                    if (isCurrent) StudioCoral else StudioHairline,
                    RoundedCornerShape(16.dp),
                  )
                  .clickable { onApplyPairing(pairing) }
                  .padding(14.dp),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                  Text(
                    text = "Aa",
                    fontFamily = heavyFont.composeFontFamily,
                    fontSize = 24.sp,
                    color = StudioTextPrimary,
                  )
                  Text(
                    text = "script",
                    fontFamily = expressiveFont.composeFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 16.sp,
                    color = StudioCoralLight,
                  )
                }
                Text(
                  text = pairing.name,
                  style = MaterialTheme.typography.titleSmall,
                  color = if (isCurrent) StudioCoralLight else StudioTextPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = pairing.subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioTextSecondary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }

        OutlinedButton(
          onClick = onOpenTypographyStudio,
          shape = CircleShape,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_typography_studio_button"),
        ) {
          Text(
            text = "Open Full Typography & Font Downloader",
            color = StudioTextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.width(6.dp))
          Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = StudioCoral,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

/**
 * Timing, Captions, Transcript & Motion Hub (Tool Tile 4: Timing)
 * Preserves 100% of timeline editing, Vosk/Gemini auto-transcription, Live Mic, SRT/VTT import,
 * word timing adjustments, segment editing, and motion animation controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimingWorkspacePanel(
  project: CaptionProject,
  activeComp: CaptionComposition?,
  playheadMs: Long,
  totalDurationMs: Long,
  isBusy: Boolean,
  geminiSettings: GeminiSettings,
  speechState: SpeechRecognitionState,
  autoTranscribeState: AutoTranscribeUiState,
  transcribedSegments: List<VoskTextSegment>,
  speechLanguageTag: String,
  voicePackStatuses: List<OfflineVoicePackStatus>,
  onDownloadOfflinePack: () -> Unit,
  onSeekMs: (Long) -> Unit,
  onSelectComposition: (CaptionComposition) -> Unit,
  onJumpPrevCaption: () -> Unit,
  onJumpNextCaption: () -> Unit,
  onGenerate: (Boolean) -> Unit,
  onAutoTranscribe: (Boolean) -> Unit,
  onSplit: () -> Unit,
  onMerge: () -> Unit,
  onDelete: () -> Unit,
  onAddElement: () -> Unit,
  onUpdateTiming: (String, Long, Long) -> Unit,
  onToggleDepthMask: () -> Unit,
  onUpdateTranscript: (String) -> Unit,
  onUpdateSegmentText: (String, String) -> Unit,
  onImportSubtitles: () -> Unit,
  onToggleDictation: () -> Unit,
  onEditWordTiming: (Int) -> Unit,
  onOpenAnimationStudio: () -> Unit,
  onOpenPresets: () -> Unit,
) {
  var subTab by remember { mutableIntStateOf(0) }
  var editingSegment by remember { mutableStateOf<VoskTextSegment?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Sleek Apple-style Segmented Control: Captions | Transcript | Motion
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(CircleShape)
          .background(StudioCard)
          .border(1.dp, StudioHairline, CircleShape)
          .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      listOf("Captions", "Transcript", "Motion").forEachIndexed { idx, label ->
        val selected = subTab == idx
        Box(
          modifier =
            Modifier.weight(1f)
              .clip(CircleShape)
              .background(if (selected) StudioElevated else Color.Transparent)
              .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) StudioHairlineStrong else Color.Transparent,
                shape = CircleShape,
              )
              .clickable { subTab = idx }
              .padding(vertical = 9.dp)
              .testTag("editor_tab_${label.lowercase()}"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) StudioTextPrimary else StudioTextSecondary,
            maxLines = 1,
          )
        }
      }
    }

    AnimatedContent(
      targetState = subTab,
      transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
      label = "timing_subtab",
    ) { tab ->
      when (tab) {
        0 -> {
          // Captions & Timeline Editor
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (activeComp != null) {
              StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      IconButton(onClick = onJumpPrevCaption, modifier = Modifier.size(36.dp)) {
                        Icon(
                          Icons.Filled.SkipPrevious,
                          contentDescription = "Previous caption",
                          tint = StudioTextPrimary,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                      Text(
                        text = "${formatEditorTimecode(activeComp.startMs)} – ${formatEditorTimecode(activeComp.endMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = StudioCoralLight,
                      )
                      IconButton(onClick = onJumpNextCaption, modifier = Modifier.size(36.dp)) {
                        Icon(
                          Icons.Filled.SkipNext,
                          contentDescription = "Next caption",
                          tint = StudioTextPrimary,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                    StudioSubtlePill(text = activeComp.region.label)
                  }

                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                      onClick = onSplit,
                      shape = CircleShape,
                      modifier = Modifier.weight(1f).height(40.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                      Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(14.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Split", style = MaterialTheme.typography.labelSmall, color = StudioTextPrimary, maxLines = 1)
                    }
                    OutlinedButton(
                      onClick = onMerge,
                      shape = CircleShape,
                      modifier = Modifier.weight(1f).height(40.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                      Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(14.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Merge", style = MaterialTheme.typography.labelSmall, color = StudioTextPrimary, maxLines = 1)
                    }
                    OutlinedButton(
                      onClick = onAddElement,
                      shape = CircleShape,
                      modifier = Modifier.weight(1f).height(40.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                      Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Add", style = MaterialTheme.typography.labelSmall, color = StudioTextPrimary, maxLines = 1)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                      Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete caption", tint = StudioError)
                    }
                  }

                  Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    OutlinedButton(
                      onClick = {
                        onUpdateTiming(activeComp.id, (activeComp.startMs - 150L).coerceAtLeast(0L), activeComp.endMs)
                      },
                      shape = CircleShape,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                      modifier = Modifier.height(34.dp),
                    ) {
                      Text("-150ms Start", style = MaterialTheme.typography.labelSmall, color = StudioTextPrimary)
                    }
                    OutlinedButton(
                      onClick = {
                        onUpdateTiming(activeComp.id, activeComp.startMs, activeComp.endMs + 200L)
                      },
                      shape = CircleShape,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                      modifier = Modifier.height(34.dp),
                    ) {
                      Text("+200ms End", style = MaterialTheme.typography.labelSmall, color = StudioTextPrimary)
                    }
                    FilterChip(
                      selected = activeComp.hasBehindSubjectElements(),
                      onClick = onToggleDepthMask,
                      shape = CircleShape,
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = StudioCoralContainer,
                          selectedLabelColor = StudioCoralLight,
                        ),
                      label = {
                        Text(
                          if (activeComp.hasBehindSubjectElements()) "Behind Subject: On"
                          else "Behind Subject: Off",
                          style = MaterialTheme.typography.labelSmall,
                        )
                      },
                    )
                  }
                }
              }
            }

            // Composition List
            if (project.compositions.isEmpty()) {
              StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("No captions yet", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                  Text(
                    "Transcribe your video in the Transcript tab or generate captions from text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary,
                  )
                  Button(
                    onClick = { onGenerate(false) },
                    enabled = !isBusy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
                  ) {
                    Text("Generate Captions")
                  }
                }
              }
            } else {
              project.compositions.forEach { comp ->
                val selected = comp.id == activeComp?.id
                StudioGlassCard(
                  modifier =
                    Modifier.fillMaxWidth().clickable { onSelectComposition(comp) },
                  cornerRadius = 16.dp,
                  containerColor = if (selected) StudioElevated else StudioCard,
                  borderColor = if (selected) StudioCoral else StudioHairline,
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Text(
                        text = "${formatEditorTimecode(comp.startMs)} – ${formatEditorTimecode(comp.endMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioCoralLight,
                      )
                      Text(
                        text = if (comp.hasBehindSubjectElements()) "Masked • ${comp.region.label}" else comp.region.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary,
                      )
                    }
                    Text(
                      text = comp.originalText,
                      style = MaterialTheme.typography.bodyLarge,
                      color = StudioTextPrimary,
                    )
                  }
                }
              }
            }
          }
        }

        1 -> {
          // Transcript, Auto-Transcribe Waveform, Live Mic, SRT/VTT & Word Timings
          var draftText by remember(project.id, project.transcriptText) { mutableStateOf(project.transcriptText) }
          val activePack = remember(speechLanguageTag) { OfflineVoskModelManager.packForLanguage(speechLanguageTag) }
          val activePackStatus =
            remember(voicePackStatuses, activePack.id) {
              voicePackStatuses.firstOrNull { it.pack.id == activePack.id }
            }

          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AutoTranscribeControlCard(
              hasVideo = project.videoUriString.isNotBlank(),
              hasAudio = project.metadata.hasAudio,
              geminiEnabled = geminiSettings.enabled,
              transcribeState = autoTranscribeState,
              playheadMs = playheadMs,
              totalDurationMs = totalDurationMs,
              onSeekMs = onSeekMs,
              onAutoTranscribeOffline = { onAutoTranscribe(false) },
              onAutoTranscribeOnline = { onAutoTranscribe(true) },
              activePackLabel = "Offline Pack: ${activePack.displayName} (~${activePack.approxSizeMb} MB)",
              isOfflinePackInstalled = activePackStatus?.isInstalled ?: true,
              isDownloadingPack = activePackStatus?.isDownloading == true,
              packDownloadProgress = activePackStatus?.downloadProgress ?: 0f,
              onDownloadOfflinePack = onDownloadOfflinePack,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = onToggleDictation,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = if (speechState.isListening) StudioError else StudioElevated,
                    contentColor = Color.White,
                  ),
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(44.dp).testTag("dictate_speech_button"),
              ) {
                Icon(
                  if (speechState.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (speechState.isListening) "Stop Mic" else "Live Mic", maxLines = 1, softWrap = false)
              }
              OutlinedButton(
                onClick = onImportSubtitles,
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(44.dp).testTag("import_subtitles_button"),
              ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, tint = StudioTextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import SRT/VTT", color = StudioTextPrimary, maxLines = 1, softWrap = false)
              }
            }

            if (speechState.partialTranscript.isNotBlank() || speechState.errorMessage != null) {
              Text(
                text = speechState.errorMessage ?: "Listening: ${speechState.partialTranscript}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (speechState.errorMessage != null) StudioError else StudioCoralLight,
              )
            }

            StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                  value = draftText,
                  onValueChange = { draftText = it },
                  label = { Text("Spoken Transcript") },
                  placeholder = { Text("Auto-transcribe from video, dictate live, or paste script...") },
                  minLines = 3,
                  maxLines = 6,
                  shape = RoundedCornerShape(14.dp),
                  colors =
                    OutlinedTextFieldDefaults.colors(
                      focusedBorderColor = StudioCoral,
                      unfocusedBorderColor = StudioBorder,
                      focusedLabelColor = StudioCoral,
                      cursorColor = StudioCoral,
                    ),
                  modifier = Modifier.fillMaxWidth().testTag("transcript_input"),
                )
                Button(
                  onClick = {
                    onUpdateTranscript(draftText)
                    onGenerate(true)
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
                  shape = CircleShape,
                  modifier = Modifier.fillMaxWidth().height(46.dp).testTag("apply_transcript_compose_button"),
                ) {
                  Text("Save & Generate Captions", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
              }
            }

            if (project.wordTimings.isNotEmpty()) {
              val alignedCount = project.wordTimings.count { !it.isEstimated }
              val allAligned = alignedCount == project.wordTimings.size
              StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                    text =
                      if (allAligned) "Word Timings ($alignedCount locked • tap to adjust)"
                      else "Word Timings ($alignedCount locked, ${project.wordTimings.size - alignedCount} estimated • tap to edit)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (allAligned) StudioSuccess else StudioWarning,
                  )
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    project.wordTimings.forEachIndexed { idx, wt ->
                      val isPlayheadInWord = playheadMs in wt.startMs..wt.endMs
                      Surface(
                        color = if (isPlayheadInWord) StudioCoralContainer else StudioElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier =
                          Modifier.border(
                              1.dp,
                              when {
                                isPlayheadInWord -> StudioCoral
                                !wt.isEstimated -> StudioSuccess.copy(alpha = 0.4f)
                                else -> StudioHairline
                              },
                              RoundedCornerShape(10.dp),
                            )
                            .clickable { onEditWordTiming(idx) },
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                          Box(
                            modifier =
                              Modifier.size(6.dp)
                                .clip(CircleShape)
                                .background(if (!wt.isEstimated) StudioSuccess else StudioWarning)
                          )
                          Text(
                            text = "${wt.word} · ${wt.startMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPlayheadInWord) StudioCoralLight else StudioTextPrimary,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            if (transcribedSegments.isNotEmpty()) {
              Text(
                text = "Transcribed Segments (${transcribedSegments.size})",
                style = MaterialTheme.typography.titleSmall,
                color = StudioTextPrimary,
                modifier = Modifier.padding(top = 4.dp),
              )
              transcribedSegments.forEach { seg ->
                val isActiveSeg = playheadMs in seg.startMs..seg.endMs
                StudioGlassCard(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable { onSeekMs(seg.startMs + 40L) }
                      .testTag("vosk_segment_card_${seg.index}"),
                  cornerRadius = 16.dp,
                  containerColor = if (isActiveSeg) StudioElevated else StudioCard,
                  borderColor = if (isActiveSeg) StudioCoral else StudioHairline,
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                          text = "${formatEditorTimecode(seg.startMs)} – ${formatEditorTimecode(seg.endMs)}",
                          style = MaterialTheme.typography.labelSmall,
                          color = StudioCoralLight,
                        )
                        val confPct = (seg.averageConfidence * 100f).toInt().coerceIn(0, 100)
                        if (confPct > 0) {
                          Text(
                            text = "$confPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (confPct >= 80) StudioSuccess else StudioTextSecondary,
                          )
                        }
                      }
                      Text(text = seg.text, style = MaterialTheme.typography.bodyMedium, color = StudioTextPrimary)
                    }
                    OutlinedButton(
                      onClick = {
                        onSeekMs(seg.startMs + 40L)
                        editingSegment = seg
                      },
                      shape = CircleShape,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                      modifier = Modifier.height(34.dp),
                    ) {
                      Text("Edit", style = MaterialTheme.typography.labelSmall, color = StudioTextPrimary)
                    }
                  }
                }
              }
            }
          }
        }

        else -> {
          // Motion & Easing Summary
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Motion & Transitions", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
              if (activeComp != null) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  StudioSubtlePill(text = "In: ${activeComp.entryAnimation.label} (${activeComp.entryDurationMs}ms)")
                  StudioSubtlePill(text = "Out: ${activeComp.exitAnimation.label}")
                }
                Text(
                  text = "Easing curve: ${activeComp.easing.label}",
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioTextSecondary,
                )
              }
              Button(
                onClick = onOpenAnimationStudio,
                colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_animation_studio_button"),
              ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Customize Entry, Exit & Easing", fontWeight = FontWeight.SemiBold)
              }
              OutlinedButton(
                onClick = onOpenPresets,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(46.dp),
              ) {
                Text("Browse All 8 Editorial Presets", color = StudioTextPrimary)
              }
            }
          }
        }
      }
    }
  }

  editingSegment?.let { seg ->
    var editedText by remember(seg.id) { mutableStateOf(seg.text) }
    AlertDialog(
      onDismissRequest = { editingSegment = null },
      containerColor = StudioCard,
      shape = RoundedCornerShape(24.dp),
      title = {
        Text(
          "Edit Segment (${formatEditorTimecode(seg.startMs)}–${formatEditorTimecode(seg.endMs)})",
          style = MaterialTheme.typography.titleMedium,
          color = StudioTextPrimary,
        )
      },
      text = {
        OutlinedTextField(
          value = editedText,
          onValueChange = { editedText = it },
          label = { Text("Segment text") },
          minLines = 2,
          maxLines = 4,
          shape = RoundedCornerShape(14.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = StudioCoral,
              unfocusedBorderColor = StudioBorder,
              focusedLabelColor = StudioCoral,
              cursorColor = StudioCoral,
            ),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateSegmentText(seg.id, editedText)
            editingSegment = null
          },
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
        ) {
          Text("Apply", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { editingSegment = null }) {
          Text("Cancel", color = StudioTextSecondary)
        }
      },
    )
  }
}

internal fun formatEditorTimecode(ms: Long): String {
  val safe = ms.coerceAtLeast(0L)
  val totalSec = safe / 1000L
  val min = totalSec / 60L
  val sec = totalSec % 60L
  return "%02d:%02d".format(min, sec)
}

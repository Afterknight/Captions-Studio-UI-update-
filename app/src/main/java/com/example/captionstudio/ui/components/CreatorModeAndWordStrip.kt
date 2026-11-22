package com.example.captionstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeZonePlatform
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.presets.PresetCatalog
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

/**
 * Progressive-disclosure Creator Mode workspace (Hick's Law simplification):
 * 1-tap Presets (including user's Cloned Styles) + 3 intuitive macro sliders (Size, Height, Energy)
 * + Descript-style interactive word-tap editor + quick Safe-Zone & Behind-Subject toggles.
 */
@Composable
fun CreatorModeQuickPanel(
  project: CaptionProject,
  activeComp: CaptionComposition?,
  playheadMs: Long,
  customPresets: List<PresetDefinition>,
  safeZonePlatform: SafeZonePlatform,
  isBusy: Boolean,
  onApplyPreset: (String) -> Unit,
  onOpenStyleCloner: () -> Unit,
  onOpenAllPresets: () -> Unit,
  onScaleAllText: (Float) -> Unit,
  onSetAllHeight: (Float) -> Unit,
  onSetAllEnergy: (Float) -> Unit,
  onCycleSafeZone: () -> Unit,
  onToggleDepthMask: () -> Unit,
  onAutoTranscribe: () -> Unit,
  onWordClickSeek: (Long) -> Unit,
  onWordQuickEdit: (wordIndex: Int, newText: String, emphasis: HierarchyLevel, behindSubject: Boolean) -> Unit,
  onWordDelete: (wordIndex: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val allPresets = remember(customPresets) { customPresets + PresetCatalog.presets }
  val avgSizeNorm =
    remember(activeComp?.id) {
      val dom = activeComp?.elements?.firstOrNull { it.hierarchyLevel == HierarchyLevel.DOMINANT }
      ((dom?.fontSizeSp ?: 52f) / 52f).coerceIn(0.70f, 1.40f)
    }
  var sizeSlider by remember(activeComp?.id) { mutableFloatStateOf(avgSizeNorm) }
  var heightSlider by remember(activeComp?.id) {
    mutableFloatStateOf(activeComp?.anchorYFraction ?: 0.72f)
  }
  var energySlider by remember(activeComp?.id) {
    val speedNorm =
      1f - (((activeComp?.entryDurationMs ?: 160) - 90f) / 180f).coerceIn(0f, 1f)
    mutableFloatStateOf(speedNorm)
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    // 1-Tap Style Strip + Reference Video Style Cloner Trigger
    StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
      Column(
        modifier = Modifier.padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "1-Tap Caption Styles",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
          )
          Text(
            text = "See all",
            style = MaterialTheme.typography.labelMedium,
            color = StudioCoralLight,
            modifier = Modifier.clickable { onOpenAllPresets() },
          )
        }

        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Signature "Clone Reel Style" pill first!
          item(key = "clone_style_pill") {
            Surface(
              onClick = onOpenStyleCloner,
              color = StudioCoralContainer,
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.height(44.dp)
                  .border(1.dp, StudioCoral.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                  .testTag("creator_clone_style_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  Icons.Filled.AutoFixHigh,
                  contentDescription = null,
                  tint = StudioCoralLight,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Clone Reel",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = StudioCoralLight,
                )
              }
            }
          }

          items(allPresets, key = { it.id }) { preset ->
            val selected = project.presetId == preset.id
            Surface(
              onClick = { onApplyPreset(preset.id) },
              color = if (selected) StudioCoral else StudioElevated,
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.height(44.dp)
                  .border(
                    1.dp,
                    if (selected) StudioCoralLight else StudioHairline,
                    RoundedCornerShape(14.dp),
                  ),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (preset.isCustomCloned) {
                  Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (selected) Color.White else StudioCoralLight,
                    modifier = Modifier.size(13.dp),
                  )
                }
                Text(
                  text = preset.name.lowercase().replaceFirstChar { it.uppercase() },
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                  color = if (selected) Color.White else StudioTextPrimary,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }
    }

    // 3 Macro Sliders: Size | Height | Energy
    StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Quick Look Macros",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = StudioTextPrimary,
        )
        CreatorMacroSlider(
          label = "Size",
          valueText = "${(sizeSlider * 100).toInt()}%",
          value = sizeSlider,
          range = 0.72f..1.35f,
          onValueChange = {
            sizeSlider = it
            onScaleAllText(it)
          },
        )
        CreatorMacroSlider(
          label = "Height",
          valueText = "${(heightSlider * 100).toInt()}%",
          value = heightSlider,
          range = 0.14f..0.86f,
          onValueChange = {
            heightSlider = it
            onSetAllHeight(it)
          },
        )
        CreatorMacroSlider(
          label = "Energy",
          valueText =
            when {
              energySlider > 0.72f -> "Punchy"
              energySlider > 0.38f -> "Balanced"
              else -> "Cinematic"
            },
          value = energySlider,
          range = 0f..1f,
          onValueChange = {
            energySlider = it
            onSetAllEnergy(it)
          },
        )

        // Quick Creator Pills: Behind-Subject, Safe Zone, Auto-Transcribe
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val behindActive = activeComp?.hasBehindSubjectElements() == true
          CreatorQuickToggleChip(
            icon = Icons.Filled.Layers,
            label = if (behindActive) "Behind: ON" else "Behind",
            active = behindActive,
            onClick = onToggleDepthMask,
            modifier = Modifier.weight(1f).testTag("creator_behind_toggle"),
          )
          CreatorQuickToggleChip(
            icon = Icons.Filled.PhoneAndroid,
            label =
              if (safeZonePlatform == SafeZonePlatform.NONE) "Safe Zone"
              else safeZonePlatform.shortLabel,
            active = safeZonePlatform != SafeZonePlatform.NONE,
            onClick = onCycleSafeZone,
            modifier = Modifier.weight(1f).testTag("creator_safe_zone_toggle"),
          )
          CreatorQuickToggleChip(
            icon = Icons.Filled.GraphicEq,
            label = if (isBusy) "Working…" else "Transcribe",
            active = false,
            onClick = onAutoTranscribe,
            modifier = Modifier.weight(1f).testTag("creator_transcribe_button"),
          )
        }
      }
    }

    // Descript-Style Word-Tap Transcript Editor
    DescriptWordTapStrip(
      wordTimings = project.wordTimings,
      activeComp = activeComp,
      playheadMs = playheadMs,
      onSeekWord = onWordClickSeek,
      onQuickEditWord = onWordQuickEdit,
      onDeleteWord = onWordDelete,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DescriptWordTapStrip(
  wordTimings: List<WordTiming>,
  activeComp: CaptionComposition?,
  playheadMs: Long,
  onSeekWord: (Long) -> Unit,
  onQuickEditWord: (wordIndex: Int, newText: String, emphasis: HierarchyLevel, behindSubject: Boolean) -> Unit,
  onDeleteWord: (wordIndex: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingWordIdx by remember { mutableStateOf<Int?>(null) }

  StudioGlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 20.dp) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Tap Any Word to Edit",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
          )
          Text(
            text = "Fix typos, boost to Hero/Accent, or put behind subject",
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextSecondary,
          )
        }
        Surface(color = StudioElevated, shape = CircleShape) {
          Text(
            text = "${wordTimings.size} words",
            style = MaterialTheme.typography.labelSmall,
            color = StudioCoralLight,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      if (wordTimings.isEmpty()) {
        Text(
          text = "Tap Transcribe or add captions in Timing to edit word-by-word.",
          style = MaterialTheme.typography.bodySmall,
          color = StudioTextSecondary,
        )
      } else {
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          wordTimings.take(48).forEachIndexed { idx, wt ->
            val isSpeakingNow = playheadMs in wt.startMs..wt.endMs
            val isHero = wt.emphasisScore >= 0.75f
            val bgColor =
              when {
                isSpeakingNow -> StudioCoral
                isHero -> StudioCoralContainer
                else -> StudioElevated
              }
            val borderColor =
              when {
                isSpeakingNow -> StudioCoralLight
                isHero -> StudioCoral.copy(alpha = 0.65f)
                else -> StudioHairline
              }
            Surface(
              onClick = {
                onSeekWord(wt.startMs)
                editingWordIdx = idx
              },
              color = bgColor,
              shape = RoundedCornerShape(10.dp),
              modifier =
                Modifier.border(1.dp, borderColor, RoundedCornerShape(10.dp))
                  .testTag("descript_word_chip_$idx"),
            ) {
              Text(
                text = wt.word,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSpeakingNow || isHero) FontWeight.Bold else FontWeight.Medium,
                color = if (isSpeakingNow) Color.White else StudioTextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              )
            }
          }
        }
      }
    }
  }

  editingWordIdx?.let { idx ->
    val wt = wordTimings.getOrNull(idx)
    if (wt != null) {
      var textInput by remember(idx) { mutableStateOf(wt.word) }
      var selectedLevel by remember(idx) {
        mutableStateOf(
          if (wt.emphasisScore >= 0.75f) HierarchyLevel.DOMINANT else HierarchyLevel.SUPPORTING
        )
      }
      var behindSubject by remember(idx) {
        mutableStateOf(activeComp?.hasBehindSubjectElements() == true && wt.emphasisScore >= 0.75f)
      }
      val chipColors =
        FilterChipDefaults.filterChipColors(
          selectedContainerColor = StudioCoralContainer,
          selectedLabelColor = StudioCoralLight,
        )

      AlertDialog(
        onDismissRequest = { editingWordIdx = null },
        containerColor = StudioCard,
        shape = RoundedCornerShape(24.dp),
        title = {
          Text(
            text = "Edit Word",
            style = MaterialTheme.typography.titleLarge,
            color = StudioTextPrimary,
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
              value = textInput,
              onValueChange = { textInput = it },
              label = { Text("Word spelling") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = StudioCoral,
                  unfocusedBorderColor = StudioBorder,
                  focusedLabelColor = StudioCoral,
                  cursorColor = StudioCoral,
                ),
              modifier = Modifier.fillMaxWidth().testTag("descript_word_edit_input"),
            )

            Text(
              "Word Emphasis",
              style = MaterialTheme.typography.labelMedium,
              color = StudioTextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              FilterChip(
                selected = selectedLevel == HierarchyLevel.SUPPORTING,
                onClick = { selectedLevel = HierarchyLevel.SUPPORTING },
                shape = CircleShape,
                colors = chipColors,
                label = { Text("Normal") },
              )
              FilterChip(
                selected = selectedLevel == HierarchyLevel.DOMINANT,
                onClick = { selectedLevel = HierarchyLevel.DOMINANT },
                shape = CircleShape,
                colors = chipColors,
                label = { Text("Hero") },
              )
              FilterChip(
                selected = selectedLevel == HierarchyLevel.EXPRESSIVE,
                onClick = { selectedLevel = HierarchyLevel.EXPRESSIVE },
                shape = CircleShape,
                colors = chipColors,
                label = { Text("Accent") },
              )
            }

            FilterChip(
              selected = behindSubject,
              onClick = { behindSubject = !behindSubject },
              shape = CircleShape,
              colors = chipColors,
              leadingIcon = {
                Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              label = { Text(if (behindSubject) "Depth: Behind Subject" else "Depth: In Front") },
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val clean = textInput.trim()
              if (clean.isNotEmpty()) {
                onQuickEditWord(idx, clean, selectedLevel, behindSubject)
              }
              editingWordIdx = null
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
            modifier = Modifier.testTag("descript_word_save_button"),
          ) {
            Text("Apply", fontWeight = FontWeight.SemiBold)
          }
        },
        dismissButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
              onClick = {
                onDeleteWord(idx)
                editingWordIdx = null
              }
            ) {
              Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Delete word",
                tint = StudioError,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text("Remove", color = StudioError)
            }
            TextButton(onClick = { editingWordIdx = null }) {
              Text("Cancel", color = StudioTextSecondary)
            }
          }
        },
      )
    }
  }
}

@Composable
private fun CreatorMacroSlider(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(label, style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
      Text(
        valueText,
        style = MaterialTheme.typography.labelMedium,
        color = StudioCoralLight,
        fontWeight = FontWeight.SemiBold,
      )
    }
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = onValueChange,
      valueRange = range,
      colors =
        SliderDefaults.colors(
          thumbColor = StudioCoralLight,
          activeTrackColor = StudioCoral,
          inactiveTrackColor = StudioObsidian,
        ),
      modifier = Modifier.height(28.dp),
    )
  }
}

@Composable
private fun CreatorQuickToggleChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    color = if (active) StudioCoralContainer else StudioElevated,
    shape = CircleShape,
    modifier =
      modifier
        .height(44.dp)
        .border(1.dp, if (active) StudioCoral else StudioHairline, CircleShape),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (active) StudioCoralLight else StudioTextSecondary,
        modifier = Modifier.size(15.dp),
      )
      Spacer(Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) StudioCoralLight else StudioTextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

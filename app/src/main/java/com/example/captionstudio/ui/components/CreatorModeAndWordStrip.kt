package com.example.captionstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.captionstudio.data.preferences.AsrEnginePreference
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.HookPacingTemplate
import com.example.captionstudio.domain.model.KaraokeHighlightMode
import com.example.captionstudio.domain.model.PresetCategory
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
  totalDurationMs: Long = 8000L,
  waveformBins: List<Float> = emptyList(),
  beatTimestampsMs: List<Long> = emptyList(),
  isSilenceTrimmed: Boolean = false,
  savedSilenceMs: Long = 0L,
  customPresets: List<PresetDefinition>,
  safeZonePlatform: SafeZonePlatform,
  isBusy: Boolean,
  asrEnginePreference: AsrEnginePreference = AsrEnginePreference.AUTO,
  isUnder4GbRam: Boolean = false,
  speechLanguageTag: String = "en-US",
  onSetAsrEnginePreference: (AsrEnginePreference) -> Unit = {},
  onSetSpeechLanguageTag: (String) -> Unit = {},
  onApplyPreset: (String) -> Unit,
  onScaleAllText: (Float) -> Unit,
  onSetAllHeight: (Float) -> Unit,
  onSetAllEnergy: (Float) -> Unit,
  onSetAllKaraokeMode: (KaraokeHighlightMode) -> Unit = {},
  onApplyHookPacing: (HookPacingTemplate) -> Unit = {},
  onCycleSafeZone: () -> Unit,
  onToggleDepthMask: () -> Unit,
  onAutoTranscribe: () -> Unit,
  onBeatSync: () -> Unit = {},
  onTrimSilences: () -> Unit = {},
  onRestoreOriginalCut: () -> Unit = {},
  onOpenScriptEditor: () -> Unit,
  onSelectComposition: (String) -> Unit = {},
  onUpdateTiming: (String, Long, Long) -> Unit = { _, _, _ -> },
  onWordClickSeek: (Long) -> Unit,
  onWordQuickEdit: (wordIndex: Int, newText: String, emphasis: HierarchyLevel, behindSubject: Boolean) -> Unit,
  onWordDelete: (wordIndex: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var styleCategoryFilter by remember { mutableStateOf("all") }
  val allPresets =
    remember(customPresets, styleCategoryFilter) {
      when (styleCategoryFilter) {
        "normal" -> PresetCatalog.normalPresets
        "kinetic" -> customPresets + PresetCatalog.kineticPresets
        else -> customPresets + PresetCatalog.presets
      }
    }
  val activeKaraokeMode =
    activeComp?.elements?.firstOrNull()?.karaokeMode ?: KaraokeHighlightMode.OFF

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
    // 1-Tap Style Strip with Normal | Kinetic | All filter pills
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
            text = "1-Tap Caption Styles (${PresetCatalog.presets.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          StudioSubtlePill(text = "${allPresets.size} looks", tint = StudioCoralLight)
        }

        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(
            "all" to "All",
            "normal" to "Normal (${PresetCatalog.normalPresets.size})",
            "kinetic" to "Kinetic (${PresetCatalog.kineticPresets.size})",
          ).forEach { (key, label) ->
            val isChosen = styleCategoryFilter == key
            Surface(
              onClick = { styleCategoryFilter = key },
              color = if (isChosen) StudioCoralContainer else StudioElevated,
              shape = CircleShape,
              modifier =
                Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                  .minimumInteractiveComponentSize()
                  .border(1.dp, if (isChosen) StudioCoral else StudioHairline, CircleShape)
                  .testTag("creator_style_filter_$key"),
            ) {
              Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Medium,
                  color = if (isChosen) StudioCoralLight else StudioTextSecondary,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
        }

        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(allPresets, key = { it.id }) { preset ->
            val selected = project.presetId == preset.id
            Surface(
              onClick = { onApplyPreset(preset.id) },
              color = if (selected) StudioCoral else StudioElevated,
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.heightIn(min = 48.dp)
                  .border(
                    1.dp,
                    if (selected) StudioCoralLight else StudioHairline,
                    RoundedCornerShape(14.dp),
                  ),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp),
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
                } else {
                  Box(
                    modifier =
                      Modifier.size(8.dp)
                        .clip(CircleShape)
                        .background(
                          if (preset.category == PresetCategory.NORMAL) Color(0xFF8BE9FD)
                          else StudioCoralLight
                        )
                  )
                }
                Text(
                  text = preset.name.lowercase().replaceFirstChar { it.uppercase() },
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                  color = if (selected) Color.White else StudioTextPrimary,
                  maxLines = 1,
                  softWrap = false,
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

        // Quick Creator Pills: Horizontally scrollable so labels NEVER stack vertically on 360dp phones
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val behindActive = activeComp?.hasBehindSubjectElements() == true
          CreatorQuickToggleChip(
            icon = Icons.Filled.Layers,
            label = if (behindActive) "Behind: ON" else "Behind",
            active = behindActive,
            onClick = onToggleDepthMask,
            modifier = Modifier.testTag("creator_behind_toggle"),
          )
          CreatorQuickToggleChip(
            icon = Icons.Filled.PhoneAndroid,
            label =
              if (safeZonePlatform == SafeZonePlatform.NONE) "Safe Zone"
              else "Zone: ${safeZonePlatform.shortLabel}",
            active = safeZonePlatform != SafeZonePlatform.NONE,
            onClick = onCycleSafeZone,
            modifier = Modifier.testTag("creator_safe_zone_toggle"),
          )
          CreatorQuickToggleChip(
            icon = Icons.Filled.GraphicEq,
            label = if (isBusy) "Working…" else "Transcribe",
            active = false,
            onClick = onAutoTranscribe,
            modifier = Modifier.testTag("creator_transcribe_button"),
          )
          AsrEngineSelectorPill(
            preference = asrEnginePreference,
            isUnder4GbRam = isUnder4GbRam,
            onSelectPreference = onSetAsrEnginePreference,
            modifier = Modifier.testTag("creator_asr_engine_selector"),
          )
          CreatorLanguageSelectorPill(
            selectedLanguageTag = speechLanguageTag,
            onSelectLanguage = onSetSpeechLanguageTag,
            modifier = Modifier.testTag("creator_language_selector"),
          )
          CreatorQuickToggleChip(
            icon = Icons.Filled.Bolt,
            label = "🧲 Beat Sync",
            active = beatTimestampsMs.isNotEmpty(),
            onClick = onBeatSync,
            modifier = Modifier.testTag("creator_beat_sync_button"),
          )
          if (isSilenceTrimmed) {
            val savedLabel =
              if (savedSilenceMs >= 100L) "Restore Original Cut (-${String.format(java.util.Locale.US, "%.1f", savedSilenceMs / 1000f)}s)"
              else "Restore Original Cut"
            CreatorQuickToggleChip(
              icon = Icons.Filled.Restore,
              label = savedLabel,
              active = true,
              onClick = onRestoreOriginalCut,
              modifier = Modifier.testTag("creator_restore_cut_button"),
            )
          } else {
            CreatorQuickToggleChip(
              icon = Icons.Filled.ContentCut,
              label = "✂️ Trim Silences",
              active = false,
              onClick = onTrimSilences,
              modifier = Modifier.testTag("creator_trim_silences_button"),
            )
          }
        }

        // 1-Tap Active-Word Karaoke Mode Strip
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Active-Word Karaoke",
            style = MaterialTheme.typography.labelMedium,
            color = StudioTextSecondary,
          )
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            KaraokeHighlightMode.entries.forEach { mode ->
              val isChosen = activeKaraokeMode == mode
              Surface(
                onClick = { onSetAllKaraokeMode(mode) },
                color = if (isChosen) StudioCoralContainer else StudioElevated,
                shape = CircleShape,
                modifier =
                  Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .minimumInteractiveComponentSize()
                    .border(1.dp, if (isChosen) StudioCoral else StudioHairline, CircleShape)
                    .testTag("creator_karaoke_${mode.name.lowercase()}"),
              ) {
                Box(
                  modifier = Modifier.padding(horizontal = 14.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isChosen) StudioCoralLight else StudioTextPrimary,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }
          }
        }
      }
    }

    // Batch Hook (0–3s) & B-Roll Pacing Templates
    StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
      Column(
        modifier = Modifier.padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            Icons.Filled.Bolt,
            contentDescription = null,
            tint = Color(0xFFFFE14D),
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "Hook & B-Roll Pacing (0–3s vs Body)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(HookPacingTemplate.entries, key = { it.id }) { tpl ->
            Surface(
              onClick = { onApplyHookPacing(tpl) },
              color = StudioElevated,
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.width(196.dp)
                  .heightIn(min = 66.dp)
                  .border(1.dp, StudioHairlineStrong, RoundedCornerShape(14.dp))
                  .testTag("creator_hook_template_${tpl.id}"),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Text(
                  text = tpl.title,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                  color =
                    if (tpl == HookPacingTemplate.UNIFORM_RESET) StudioTextSecondary
                    else StudioCoralLight,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = tpl.subtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color = StudioTextSecondary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }

    // Visual Waveform Caption Timeline (drag left/right handles to trim caption blocks directly over audio)
    if (project.compositions.isNotEmpty()) {
      VisualCaptionTimeline(
        compositions = project.compositions,
        waveformBins = waveformBins,
        totalDurationMs = totalDurationMs,
        playheadMs = playheadMs,
        selectedCompositionId = activeComp?.id,
        onSeekPlayhead = onWordClickSeek,
        onSelectComposition = onSelectComposition,
        onTrimComposition = onUpdateTiming,
        beatTimestampsMs = beatTimestampsMs,
      )
    }

    // Descript-Style Word-Tap Transcript Editor (derives editable words from compositions if wordTimings empty)
    val fallbackWords =
      remember(project.wordTimings, project.compositions) {
        if (project.wordTimings.isNotEmpty()) {
          project.wordTimings
        } else {
          project.compositions.flatMap { comp ->
            if (comp.wordMappings.isNotEmpty()) {
              comp.wordMappings
            } else {
              val tokens =
                comp.originalText.ifBlank { comp.elements.joinToString(" ") { it.text } }
                  .trim()
                  .split(Regex("\\s+"))
                  .filter { it.isNotBlank() }
              val dur = (comp.endMs - comp.startMs).coerceAtLeast(200L)
              val slice = if (tokens.isEmpty()) dur else dur / tokens.size
              tokens.mapIndexed { idx, w ->
                val ws = comp.startMs + slice * idx
                val we = if (idx == tokens.lastIndex) comp.endMs else (ws + slice).coerceAtLeast(ws + 80L)
                WordTiming(word = w, startMs = ws, endMs = we, isEstimated = true)
              }
            }
          }
        }
      }

    DescriptWordTapStrip(
      wordTimings = fallbackWords,
      activeComp = activeComp,
      playheadMs = playheadMs,
      hasVideo = project.videoUriString.isNotBlank(),
      isBusy = isBusy,
      onAutoTranscribe = onAutoTranscribe,
      onOpenScriptEditor = onOpenScriptEditor,
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
  hasVideo: Boolean = true,
  isBusy: Boolean = false,
  onAutoTranscribe: () -> Unit = {},
  onOpenScriptEditor: () -> Unit = {},
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Tap Any Word to Edit",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
          )
        }
      }

      if (wordTimings.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "No caption words yet. Auto-transcribe offline or type a script to edit word-by-word.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioTextSecondary,
          )
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (hasVideo) {
              Button(
                onClick = onAutoTranscribe,
                enabled = !isBusy,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 48.dp).testTag("word_strip_transcribe_button"),
              ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Transcribe Offline", maxLines = 1, softWrap = false)
              }
            }
            Surface(
              onClick = onOpenScriptEditor,
              color = StudioElevated,
              shape = CircleShape,
              modifier =
                Modifier.heightIn(min = 48.dp)
                  .border(1.dp, StudioHairlineStrong, CircleShape)
                  .testTag("word_strip_open_script_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  Icons.Filled.Edit,
                  contentDescription = null,
                  tint = StudioCoralLight,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Type Script",
                  style = MaterialTheme.typography.labelLarge,
                  color = StudioTextPrimary,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
        }
      } else {
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
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
            Box(
              modifier =
                Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                  .minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    onSeekWord(wt.startMs)
                    editingWordIdx = idx
                  }
                  .testTag("descript_word_chip_$idx"),
              contentAlignment = Alignment.Center,
            ) {
              Surface(
                color = bgColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(10.dp)),
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
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = selectedLevel == HierarchyLevel.SUPPORTING,
                onClick = { selectedLevel = HierarchyLevel.SUPPORTING },
                shape = CircleShape,
                colors = chipColors,
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text("Normal", maxLines = 1, softWrap = false) },
              )
              FilterChip(
                selected = selectedLevel == HierarchyLevel.DOMINANT,
                onClick = { selectedLevel = HierarchyLevel.DOMINANT },
                shape = CircleShape,
                colors = chipColors,
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text("Hero", maxLines = 1, softWrap = false) },
              )
              FilterChip(
                selected = selectedLevel == HierarchyLevel.EXPRESSIVE,
                onClick = { selectedLevel = HierarchyLevel.EXPRESSIVE },
                shape = CircleShape,
                colors = chipColors,
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text("Accent", maxLines = 1, softWrap = false) },
              )
            }

            FilterChip(
              selected = behindSubject,
              onClick = { behindSubject = !behindSubject },
              shape = CircleShape,
              colors = chipColors,
              modifier = Modifier.heightIn(min = 48.dp),
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
            modifier = Modifier.heightIn(min = 48.dp).testTag("descript_word_save_button"),
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
              },
              modifier = Modifier.heightIn(min = 48.dp),
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
            TextButton(
              onClick = { editingWordIdx = null },
              modifier = Modifier.heightIn(min = 48.dp),
            ) {
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
      modifier = Modifier.heightIn(min = 48.dp),
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
        .heightIn(min = 48.dp)
        .border(1.dp, if (active) StudioCoral else StudioHairline, CircleShape),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (active) StudioCoralLight else StudioTextSecondary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = if (active) StudioCoralLight else StudioTextPrimary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Compact, M3-compliant (48dp touch target) offline ASR engine selector pill letting any user
 * (even on <4 GB RAM devices) switch cleanly between `Auto (Whisper / Vosk)`, `Whisper Small`,
 * and `Vosk Fast`.
 */
@Composable
fun AsrEngineSelectorPill(
  preference: AsrEnginePreference,
  isUnder4GbRam: Boolean,
  onSelectPreference: (AsrEnginePreference) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val pillLabel =
    when (preference) {
      AsrEnginePreference.AUTO ->
        if (isUnder4GbRam) stringResource(R.string.asr_engine_pill_auto_vosk)
        else stringResource(R.string.asr_engine_pill_auto_whisper)
      AsrEnginePreference.WHISPER_SMALL -> stringResource(R.string.asr_engine_pill_whisper)
      AsrEnginePreference.VOSK_FAST -> stringResource(R.string.asr_engine_pill_vosk)
    }
  val selectorCd = stringResource(R.string.asr_engine_selector_cd)
  val isManualOverride = preference != AsrEnginePreference.AUTO

  Box(modifier = modifier) {
    Surface(
      onClick = { expanded = true },
      color = if (isManualOverride) StudioCoralContainer else StudioElevated,
      shape = CircleShape,
      modifier =
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
          .minimumInteractiveComponentSize()
          .border(
            1.dp,
            if (isManualOverride) StudioCoral else StudioHairline,
            CircleShape,
          )
          .semantics { contentDescription = "$selectorCd: $pillLabel" },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Memory,
          contentDescription = null,
          tint = if (isManualOverride) StudioCoralLight else StudioTextSecondary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = pillLabel,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          color = if (isManualOverride) StudioCoralLight else StudioTextPrimary,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(
          imageVector = Icons.Filled.ArrowDropDown,
          contentDescription = null,
          tint = if (isManualOverride) StudioCoralLight else StudioTextSecondary,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      containerColor = StudioCard,
    ) {
      val options =
        listOf(
          Triple(
            AsrEnginePreference.AUTO,
            stringResource(R.string.asr_engine_option_auto),
            if (isUnder4GbRam) stringResource(R.string.asr_engine_option_auto_sub_vosk)
            else stringResource(R.string.asr_engine_option_auto_sub_whisper),
          ),
          Triple(
            AsrEnginePreference.WHISPER_SMALL,
            stringResource(R.string.asr_engine_option_whisper),
            stringResource(R.string.asr_engine_option_whisper_sub),
          ),
          Triple(
            AsrEnginePreference.VOSK_FAST,
            stringResource(R.string.asr_engine_option_vosk),
            stringResource(R.string.asr_engine_option_vosk_sub),
          ),
        )
      options.forEach { (option, title, subtitle) ->
        val isChosen = preference == option
        DropdownMenuItem(
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isChosen) StudioCoralLight else StudioTextPrimary,
              )
              Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
              )
            }
          },
          trailingIcon = {
            if (isChosen) {
              Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = StudioCoralLight,
                modifier = Modifier.size(18.dp),
              )
            }
          },
          onClick = {
            expanded = false
            onSelectPreference(option)
          },
          modifier = Modifier.testTag("asr_engine_option_${option.name.lowercase()}"),
        )
      }
    }
  }
}

@Composable
private fun CreatorLanguageSelectorPill(
  selectedLanguageTag: String,
  onSelectLanguage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val languages =
    remember {
      listOf(
        "en-US" to "English (US)",
        "en-IN" to "English (IN)",
        "hi-IN" to "Hindi / Hinglish",
        "es-ES" to "Spanish",
        "fr-FR" to "French",
        "de-DE" to "German",
        "auto" to "Auto Detect",
      )
    }
  val shortCode =
    when {
      selectedLanguageTag.equals("auto", ignoreCase = true) -> "AUTO"
      else -> selectedLanguageTag.substringBefore("-").uppercase()
    }
  val langCd = stringResource(R.string.asr_language_selector_cd)

  Box(modifier = modifier) {
    Surface(
      onClick = { expanded = true },
      color = StudioElevated,
      shape = CircleShape,
      modifier =
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
          .minimumInteractiveComponentSize()
          .border(1.dp, StudioHairline, CircleShape)
          .semantics { contentDescription = "$langCd: $selectedLanguageTag" },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Language,
          contentDescription = null,
          tint = StudioTextSecondary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = shortCode,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          color = StudioTextPrimary,
          maxLines = 1,
          softWrap = false,
        )
        Icon(
          imageVector = Icons.Filled.ArrowDropDown,
          contentDescription = null,
          tint = StudioTextSecondary,
          modifier = Modifier.size(16.dp),
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      containerColor = StudioCard,
    ) {
      languages.forEach { (tag, label) ->
        val isChosen = selectedLanguageTag.equals(tag, ignoreCase = true)
        DropdownMenuItem(
          text = {
            Text(
              text = label,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Medium,
              color = if (isChosen) StudioCoralLight else StudioTextPrimary,
            )
          },
          trailingIcon = {
            if (isChosen) {
              Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = StudioCoralLight,
                modifier = Modifier.size(18.dp),
              )
            }
          },
          onClick = {
            expanded = false
            onSelectLanguage(tag)
          },
          modifier = Modifier.testTag("creator_lang_option_${tag.lowercase()}"),
        )
      }
    }
  }
}

package com.example.captionstudio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.captionstudio.domain.model.AspectRatioOption
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.engine.KineticCompositionRenderer
import com.example.captionstudio.engine.LocalCompositionEngine
import com.example.captionstudio.media.ClonedStyleReport
import com.example.captionstudio.media.ReferenceCropBand
import com.example.captionstudio.media.ReferenceStyleCloner
import com.example.captionstudio.media.StyleCloneProgress
import com.example.captionstudio.presets.PresetCatalog
import com.example.captionstudio.presets.StyleDeepLinkCodec
import com.example.captionstudio.typography.FontRegistry
import com.example.captionstudio.ui.components.ExportedVideoReviewPlayer
import com.example.captionstudio.ui.components.StudioCoralPillButton
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioMinimalTopBar
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.launch

/**
 * Split-Screen "Style Match Studio" for 100% On-Device Reference Video Style Cloning.
 * Lets creators pick any Reel/Short screen recording, extracts colors, motion physics, font
 * archetype, layout anchor, and behind-subject depth locally for $0, and previews it live
 * side-by-side before saving or applying to their project.
 */
@Composable
fun StyleClonerScreen(
  hasActiveProject: Boolean,
  onSaveAndApplyPreset: (preset: PresetDefinition, applyToActiveProject: Boolean) -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val renderer = remember(context) { KineticCompositionRenderer(context) }

  var referenceUri by remember { mutableStateOf<Uri?>(null) }
  var selectedBand by remember { mutableStateOf(ReferenceCropBand.FULL_FRAME) }
  var isScanning by remember { mutableStateOf(false) }
  var scanProgress by remember { mutableStateOf<StyleCloneProgress?>(null) }
  var report by remember { mutableStateOf<ClonedStyleReport?>(null) }
  var editablePreset by remember { mutableStateOf<PresetDefinition?>(null) }
  var presetNameInput by remember { mutableStateOf("Cloned Reel Look") }

  fun runScan(uri: Uri, band: ReferenceCropBand) {
    isScanning = true
    scanProgress = StyleCloneProgress("Preparing 15fps local scanner…", 0.04f)
    scope.launch {
      val result =
        ReferenceStyleCloner.cloneStyleFromReferenceVideo(
          context = context,
          videoUri = uri,
          cropBand = band,
          customStyleName = presetNameInput,
        ) { p ->
          scanProgress = p
        }
      report = result
      editablePreset = result.preset
      presetNameInput = result.preset.name.lowercase().replaceFirstChar { it.uppercase() }
      isScanning = false
    }
  }

  val videoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        referenceUri = uri
        runScan(uri, selectedBand)
      }
    }

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = StudioCoralContainer,
      selectedLabelColor = StudioCoralLight,
    )

  Scaffold(
    containerColor = StudioObsidian,
    topBar = {
      StudioMinimalTopBar(
        title = "Reference Style Cloner",
        subtitle = "100% On-Device • Zero Cost",
        onBack = onBack,
        backButtonTag = "style_cloner_back_button",
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Step 1: Pick Reference Reel & Focus Band
        item {
          StudioGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            borderColor = StudioHairlineStrong,
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Filled.AutoFixHigh,
                  contentDescription = null,
                  tint = StudioCoralLight,
                  modifier = Modifier.size(20.dp),
                )
                Text(
                  text = "Clone Any Viral Caption Style",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = StudioTextPrimary,
                )
              }
              Text(
                text =
                  "Pick a 2–10s clip or screen recording of a Reel/Short. Caption Studio scans up to 15fps locally to clone its colors, font vibe, motion curve, anchor & depth layer.",
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextSecondary,
              )

              Text(
                text = "Caption Focus Band",
                style = MaterialTheme.typography.labelMedium,
                color = StudioTextSecondary,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                ReferenceCropBand.entries.forEach { band ->
                  FilterChip(
                    selected = selectedBand == band,
                    onClick = {
                      selectedBand = band
                      referenceUri?.let { if (!isScanning) runScan(it, band) }
                    },
                    shape = CircleShape,
                    colors = chipColors,
                    label = {
                      Text(
                        band.label.replace(" Video", "").replace(" Band", "").replace(" Half", ""),
                        maxLines = 1,
                      )
                    },
                    modifier = Modifier.weight(1f),
                  )
                }
              }

              StudioCoralPillButton(
                text =
                  if (referenceUri == null) "Pick Reference Video" else "Choose Different Reference",
                leadingIcon = Icons.Filled.Movie,
                showTrailingArrow = true,
                enabled = !isScanning,
                onClick = {
                  videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                  )
                },
                modifier = Modifier.testTag("pick_reference_video_button"),
              )
            }
          }
        }

        // Labor-Illusion Live Scan Progress Card
        item {
          AnimatedVisibility(visible = isScanning && scanProgress != null) {
            val prog = scanProgress
            StudioGlassCard(
              modifier = Modifier.fillMaxWidth(),
              cornerRadius = 22.dp,
              borderColor = StudioCoral.copy(alpha = 0.6f),
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = prog?.stageLabel.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextPrimary,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    text = "${((prog?.progressFraction ?: 0f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioCoralLight,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
                LinearProgressIndicator(
                  progress = { (prog?.progressFraction ?: 0f).coerceIn(0f, 1f) },
                  color = StudioCoral,
                  trackColor = StudioObsidian,
                  modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
                if (!prog?.detectedPreviewText.isNullOrBlank()) {
                  Text(
                    text = "Detected: \"${prog?.detectedPreviewText}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioSuccess,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        // Split-Screen "Style Match Studio" Verification + Fine-Tuning
        val activeReport = report
        val draftPreset = editablePreset
        if (!isScanning && activeReport != null && draftPreset != null) {
          item {
            StudioGlassCard(
              modifier = Modifier.fillMaxWidth(),
              cornerRadius = 22.dp,
              borderColor = StudioSuccess.copy(alpha = 0.55f),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(
                      text = "Style Match Studio",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = StudioTextPrimary,
                    )
                    Text(
                      text =
                        "${activeReport.framesScanned} frames • ${activeReport.sceneCutsDetected} scene cuts analyzed",
                      style = MaterialTheme.typography.labelSmall,
                      color = StudioTextSecondary,
                    )
                  }
                  Surface(color = StudioCoralContainer, shape = CircleShape) {
                    Text(
                      text = "${activeReport.confidencePercent}% DNA Match",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = StudioCoralLight,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                  }
                }

                // Side-by-side Split Preview: Reference Clip vs Live Animated Cloned Style
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      "REFERENCE",
                      style = MaterialTheme.typography.labelSmall,
                      color = StudioTextSecondary,
                    )
                    referenceUri?.let { uri ->
                      ExportedVideoReviewPlayer(
                        videoUri = uri,
                        aspectRatio = AspectRatioOption.PORTRAIT_9_16,
                      )
                    }
                  }
                  Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      "CLONED LIVE",
                      style = MaterialTheme.typography.labelSmall,
                      color = StudioCoralLight,
                      fontWeight = FontWeight.SemiBold,
                    )
                    LiveAnimatedClonedPreview(
                      preset = draftPreset,
                      renderer = renderer,
                      modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                  }
                }
              }
            }
          }

          // Extracted Style DNA Breakdown & Quick Fine-Tune Controls
          item {
            StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                Text(
                  text = "Extracted Style DNA",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = StudioTextPrimary,
                )

                StyleDnaRow("Motion Physics", activeReport.motionSummary)
                StyleDnaRow("Depth & Placement", activeReport.depthSummary)
                StyleDnaRow("Palette & Outline", activeReport.colorSummary)

                // Extracted Color Swatches
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    "Swatches:",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextSecondary,
                  )
                  listOfNotNull(
                    draftPreset.primaryColorHex,
                    draftPreset.dominantColorHex,
                    draftPreset.strokeColorHex.takeIf { draftPreset.strokeWidthFactor > 0f },
                    draftPreset.backgroundPillColorHex,
                  ).distinct().forEach { hex ->
                    Box(
                      modifier =
                        Modifier.size(22.dp)
                          .clip(CircleShape)
                          .background(
                            runCatching { Color(android.graphics.Color.parseColor(hex)) }
                              .getOrDefault(Color.White)
                          )
                          .border(1.dp, StudioHairlineStrong, CircleShape)
                    )
                  }
                }

                // Top-3 Closest Font Quick-Swap Chips
                Text(
                  "Closest Matched Typefaces (Tap to swap)",
                  style = MaterialTheme.typography.labelMedium,
                  color = StudioTextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  activeReport.alternativeHeavyFontIds.distinct().take(3).forEach { fontId ->
                    val desc = FontRegistry.findById(fontId)
                    FilterChip(
                      selected = draftPreset.heavyFontId == fontId,
                      onClick = {
                        editablePreset = draftPreset.copy(heavyFontId = fontId)
                      },
                      shape = CircleShape,
                      colors = chipColors,
                      label = { Text(desc.displayName, maxLines = 1) },
                    )
                  }
                }

                // Behind-Subject & Uppercase quick toggles
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      Icons.Filled.Layers,
                      contentDescription = null,
                      tint = StudioCoralLight,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      "Behind-Subject Masking",
                      style = MaterialTheme.typography.bodyMedium,
                      color = StudioTextPrimary,
                    )
                  }
                  Switch(
                    checked = draftPreset.preferBehindSubject,
                    onCheckedChange = { enabled ->
                      editablePreset = draftPreset.copy(preferBehindSubject = enabled)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = StudioCoral),
                  )
                }

                OutlinedTextField(
                  value = presetNameInput,
                  onValueChange = { presetNameInput = it },
                  label = { Text("Save Preset As") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  colors =
                    OutlinedTextFieldDefaults.colors(
                      focusedBorderColor = StudioCoral,
                      unfocusedBorderColor = StudioBorder,
                      focusedLabelColor = StudioCoral,
                      cursorColor = StudioCoral,
                    ),
                  modifier = Modifier.fillMaxWidth().testTag("cloned_preset_name_input"),
                )

                val finalizedPreset =
                  draftPreset.copy(
                    name = presetNameInput.ifBlank { "Cloned Style" }.trim().uppercase()
                  )

                StudioCoralPillButton(
                  text =
                    if (hasActiveProject) "Save & Apply to Project" else "Save to My Presets",
                  leadingIcon = Icons.Filled.Check,
                  showTrailingArrow = false,
                  onClick = {
                    onSaveAndApplyPreset(finalizedPreset, hasActiveProject)
                    onBack()
                  },
                  modifier = Modifier.testTag("save_cloned_style_button"),
                )

                OutlinedButton(
                  onClick = {
                    val deepLink = StyleDeepLinkCodec.encodeToDeepLink(finalizedPreset)
                    val send =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                          Intent.EXTRA_TEXT,
                          "Try my Caption Studio style \"${finalizedPreset.name}\": $deepLink",
                        )
                      }
                    context.startActivity(Intent.createChooser(send, "Share Style Deep Link"))
                  },
                  shape = CircleShape,
                  modifier = Modifier.fillMaxWidth().height(48.dp).testTag("share_cloned_style_link_button"),
                ) {
                  Icon(Icons.Filled.Share, contentDescription = null, tint = StudioCoralLight)
                  Spacer(Modifier.width(8.dp))
                  Text("Share Zero-Server Style Link", color = StudioTextPrimary)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StyleDnaRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
    Spacer(Modifier.width(12.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = StudioTextPrimary,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun LiveAnimatedClonedPreview(
  preset: PresetDefinition,
  renderer: KineticCompositionRenderer,
  modifier: Modifier = Modifier,
) {
  var tickMs by remember(preset.id, preset.heavyFontId, preset.preferBehindSubject) {
    mutableLongStateOf(0L)
  }

  LaunchedEffect(preset.id, preset.heavyFontId) {
    var lastFrame = -1L
    while (true) {
      withFrameMillis { frameMs ->
        if (lastFrame >= 0L) {
          val dt = (frameMs - lastFrame).coerceIn(0L, 64L)
          tickMs = (tickMs + dt) % 2800L
        }
        lastFrame = frameMs
      }
    }
  }

  val previewComps =
    remember(preset) {
      val sample = preset.sampleSentence.ifBlank { "Why do many creators add crazy text" }
      val timings = LocalCompositionEngine.buildProvisionalWordTimings(sample, 2800L)
      LocalCompositionEngine.generateCompositions(
          transcript = sample,
          wordTimings = timings,
          totalDurationMs = 2800L,
          preset = preset,
          analysisReport = VideoAnalysisReport(safeRegionsRanked = listOf(SafeRegion.CENTER)),
        )
        .let { base ->
          PresetCatalog.applyPresetToCompositions(
            preset = preset,
            compositions = base,
            analysisReport = VideoAnalysisReport(safeRegionsRanked = listOf(SafeRegion.CENTER)),
          )
        }
        .map { it.copy(anchorXFraction = 0.5f, anchorYFraction = 0.52f) }
    }

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(16.dp))
        .background(
          Brush.verticalGradient(listOf(Color(0xFF211E2B), Color(0xFF121218), Color(0xFF0A0A0E)))
        )
        .border(1.dp, StudioCoral.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawIntoCanvas { canvas ->
        renderer.drawAtTime(
          canvas = canvas.nativeCanvas,
          canvasWidth = size.width.toInt(),
          canvasHeight = size.height.toInt(),
          compositions = previewComps,
          positionMs = tickMs,
          shadowMultiplier = preset.shadowRadiusFactor,
        )
      }
    }
  }
}

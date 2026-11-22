package com.example.captionstudio.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.captionstudio.domain.model.AspectRatioOption
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.ExportFrameRate
import com.example.captionstudio.domain.model.ExportPreferences
import com.example.captionstudio.domain.model.ExportQualityMode
import com.example.captionstudio.domain.model.ExportResolution
import com.example.captionstudio.media.ExportUiState
import com.example.captionstudio.ui.components.ExportedVideoReviewPlayer
import com.example.captionstudio.ui.components.StudioCoralPillButton
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioMinimalTopBar
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@Composable
fun ExportScreen(
  project: CaptionProject?,
  exportState: ExportUiState,
  onUpdatePreferences: (ExportPreferences) -> Unit,
  onStartExport: () -> Unit,
  onCancelExport: () -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val prefs = project?.exportPreferences ?: ExportPreferences()
  val hasVideo = !project?.videoUriString.isNullOrBlank()

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = StudioCoralContainer,
      selectedLabelColor = StudioCoralLight,
    )

  Scaffold(
    containerColor = StudioObsidian,
    topBar = {
      StudioMinimalTopBar(
        title = "Export Video",
        subtitle = project?.title,
        onBack = onBack,
        backButtonTag = "export_back_button",
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 660.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Quick Social Presets
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Quick Presets", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = {
                    onUpdatePreferences(
                      prefs.copy(
                        aspectRatio = AspectRatioOption.PORTRAIT_9_16,
                        resolution = ExportResolution.RES_1080P,
                        frameRate = ExportFrameRate.FPS_30,
                      )
                    )
                  },
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text("Reels 9:16", color = StudioTextPrimary, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                  onClick = {
                    onUpdatePreferences(
                      prefs.copy(
                        aspectRatio = AspectRatioOption.LANDSCAPE_16_9,
                        resolution = ExportResolution.RES_1080P,
                        frameRate = ExportFrameRate.FPS_60,
                      )
                    )
                  },
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text("YouTube 60p", color = StudioTextPrimary, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                  onClick = {
                    onUpdatePreferences(
                      prefs.copy(
                        aspectRatio = AspectRatioOption.SQUARE_1_1,
                        resolution = ExportResolution.RES_1080P,
                        frameRate = ExportFrameRate.FPS_30,
                      )
                    )
                  },
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text("Square 1:1", color = StudioTextPrimary, maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }

        // Format Settings
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatioOption.entries.forEach { ar ->
                  FilterChip(
                    selected = prefs.aspectRatio == ar,
                    onClick = { onUpdatePreferences(prefs.copy(aspectRatio = ar)) },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(ar.label) },
                  )
                }
              }

              Text("Resolution", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportResolution.entries.forEach { res ->
                  FilterChip(
                    selected = prefs.resolution == res,
                    onClick = { onUpdatePreferences(prefs.copy(resolution = res)) },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(res.label) },
                  )
                }
              }

              Text("Frame Rate", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFrameRate.entries.forEach { fr ->
                  FilterChip(
                    selected = prefs.frameRate == fr,
                    onClick = { onUpdatePreferences(prefs.copy(frameRate = fr)) },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(fr.label) },
                  )
                }
              }

              Text("Quality", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportQualityMode.entries.forEach { q ->
                  FilterChip(
                    selected = prefs.qualityMode == q,
                    onClick = { onUpdatePreferences(prefs.copy(qualityMode = q)) },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text("${q.label} (${q.bitrateMbps} Mbps)") },
                  )
                }
              }
            }
          }
        }

        // Export CTA & State
        item {
          when (exportState) {
            is ExportUiState.Idle -> {
              if (!hasVideo) {
                Text(
                  text = "Attach a video in the editor before exporting to MP4.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = StudioError,
                  modifier = Modifier.padding(bottom = 10.dp),
                )
              }
              StudioCoralPillButton(
                text = "Render & Save to Gallery",
                leadingIcon = Icons.Filled.MovieCreation,
                showTrailingArrow = true,
                enabled = hasVideo,
                onClick = onStartExport,
                modifier = Modifier.testTag("start_export_button"),
              )
            }

            is ExportUiState.Exporting -> {
              StudioGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                borderColor = StudioCoral.copy(alpha = 0.6f),
              ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(exportState.stageLabel, color = StudioTextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text("${exportState.progressPercent}%", color = StudioCoralLight, fontWeight = FontWeight.SemiBold)
                  }
                  LinearProgressIndicator(
                    progress = { exportState.progressPercent / 100f },
                    color = StudioCoral,
                    trackColor = StudioObsidian,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                  )
                  OutlinedButton(
                    onClick = onCancelExport,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("cancel_export_button"),
                  ) {
                    Text("Cancel Export", color = StudioTextPrimary)
                  }
                }
              }
            }

            is ExportUiState.Completed -> {
              StudioGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                borderColor = StudioSuccess.copy(alpha = 0.65f),
              ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StudioSuccess)
                    Spacer(Modifier.width(8.dp))
                    Text("Saved to Gallery", style = MaterialTheme.typography.titleMedium, color = StudioSuccess)
                  }
                  Text(
                    "${exportState.fileName}\n${exportState.fileSizeBytes / 1024} KB • ${exportState.verifiedDurationMs / 1000.0}s • Audio ${if (exportState.hasAudio) "preserved" else "none"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary,
                  )
                  ExportedVideoReviewPlayer(
                    videoUri = exportState.galleryUri,
                    aspectRatio = prefs.aspectRatio,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                      onClick = {
                        val shareIntent =
                          Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, exportState.galleryUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                          }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Captioned Video"))
                      },
                      shape = CircleShape,
                      colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
                      modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                      Icon(Icons.Filled.Share, contentDescription = null)
                      Spacer(Modifier.width(6.dp))
                      Text("Share", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                      onClick = onStartExport,
                      shape = CircleShape,
                      modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                      Text("Export Again", color = StudioTextPrimary)
                    }
                  }
                }
              }
            }

            is ExportUiState.Failed -> {
              StudioGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                borderColor = StudioError.copy(alpha = 0.7f),
              ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text("Export Failed", style = MaterialTheme.typography.titleMedium, color = StudioError)
                  Text(exportState.message, style = MaterialTheme.typography.bodyMedium, color = StudioTextSecondary)
                  StudioCoralPillButton(
                    text = "Retry Export",
                    onClick = onStartExport,
                    showTrailingArrow = false,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

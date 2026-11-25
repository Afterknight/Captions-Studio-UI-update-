package com.example.captionstudio.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.captionstudio.domain.model.AspectRatioOption
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.ExportFrameRate
import com.example.captionstudio.domain.model.ExportPreferences
import com.example.captionstudio.domain.model.ExportQualityMode
import com.example.captionstudio.domain.model.ExportResolution
import com.example.captionstudio.media.ExportUiState
import com.example.captionstudio.presets.PresetCatalog
import com.example.captionstudio.presets.StyleDeepLinkCodec
import com.example.captionstudio.ui.components.ExportedVideoReviewPlayer
import com.example.captionstudio.ui.components.StudioCoralPillButton
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioMinimalTopBar
import com.example.captionstudio.ui.components.StudioProTopBarPill
import com.example.captionstudio.ui.components.StudioSubtlePill
import com.example.captionstudio.ui.components.StudioTaskProgressPill
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
  isProUnlocked: Boolean = false,
  onOpenProSheet: () -> Unit = {},
  onUpdatePreferences: (ExportPreferences) -> Unit,
  onStartExport: () -> Unit,
  onCancelExport: () -> Unit,
  onSaveCurrentStyleAsPreset: () -> Unit = {},
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val prefs = project?.exportPreferences ?: ExportPreferences()
  val hasVideo = !project?.videoUriString.isNullOrBlank()
  var savedLookToast by remember { mutableStateOf(false) }

  // Request POST_NOTIFICATIONS cleanly on Android 13+ before starting ForegroundService export
  val notifPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      onStartExport()
    }

  fun launchExportSafely() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return
      }
    }
    onStartExport()
  }

  // Peak-End Rule: Tactile haptic pulse the instant export completes
  LaunchedEffect(exportState) {
    if (exportState is ExportUiState.Completed) {
      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
        title = "Export Video",
        subtitle = project?.title,
        onBack = onBack,
        backButtonTag = "export_back_button",
        trailingContent = {
          StudioProTopBarPill(
            isProUnlocked = isProUnlocked,
            onClick = onOpenProSheet,
          )
        },
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
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
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
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                  modifier = Modifier.heightIn(min = 48.dp),
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
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                  modifier = Modifier.heightIn(min = 48.dp),
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
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                  modifier = Modifier.heightIn(min = 48.dp),
                ) {
                  Text("Square 1:1", color = StudioTextPrimary, maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }

        // Format Settings + Tasteful Removable Watermark
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                AspectRatioOption.entries.forEach { ar ->
                  FilterChip(
                    selected = prefs.aspectRatio == ar,
                    onClick = { onUpdatePreferences(prefs.copy(aspectRatio = ar)) },
                    shape = CircleShape,
                    colors = chipColors,
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(ar.label, maxLines = 1, softWrap = false) },
                  )
                }
              }

              Text("Resolution", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ExportResolution.entries.forEach { res ->
                  FilterChip(
                    selected = prefs.resolution == res,
                    onClick = { onUpdatePreferences(prefs.copy(resolution = res)) },
                    shape = CircleShape,
                    colors = chipColors,
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(res.label, maxLines = 1, softWrap = false) },
                  )
                }
              }

              Text("Frame Rate", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ExportFrameRate.entries.forEach { fr ->
                  FilterChip(
                    selected = prefs.frameRate == fr,
                    onClick = { onUpdatePreferences(prefs.copy(frameRate = fr)) },
                    shape = CircleShape,
                    colors = chipColors,
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(fr.label, maxLines = 1, softWrap = false) },
                  )
                }
              }

              Text("Quality", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ExportQualityMode.entries.forEach { q ->
                  FilterChip(
                    selected = prefs.qualityMode == q,
                    onClick = { onUpdatePreferences(prefs.copy(qualityMode = q)) },
                    shape = CircleShape,
                    colors = chipColors,
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text("${q.label} (${q.bitrateMbps} Mbps)", maxLines = 1, softWrap = false) },
                  )
                }
              }

              val effectiveShowWatermark = if (isProUnlocked) prefs.showWatermark else true
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(
                  modifier = Modifier.weight(1f).padding(end = 12.dp),
                  verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(
                      text = stringResource(R.string.export_watermark_title),
                      style = MaterialTheme.typography.titleSmall,
                      color = StudioTextPrimary,
                    )
                    if (!isProUnlocked) {
                      StudioSubtlePill(
                        text = stringResource(R.string.pro_pill_label),
                        tint = StudioCoralLight,
                      )
                    }
                  }
                  Text(
                    text =
                      stringResource(
                        if (isProUnlocked) R.string.export_watermark_subtitle_pro
                        else R.string.export_watermark_subtitle_free
                      ),
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary,
                  )
                }
                Switch(
                  checked = effectiveShowWatermark,
                  onCheckedChange = { checked ->
                    if (!checked && !isProUnlocked) {
                      onOpenProSheet()
                    } else {
                      onUpdatePreferences(prefs.copy(showWatermark = checked))
                    }
                  },
                  colors = SwitchDefaults.colors(checkedTrackColor = StudioCoral),
                  modifier = Modifier.testTag("export_watermark_switch"),
                )
              }
            }
          }
        }

        // Export CTA & State (Background ForegroundService + Peak-End Celebration)
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
                onClick = { launchExportSafely() },
                modifier = Modifier.testTag("start_export_button"),
              )
            }

            is ExportUiState.Exporting -> {
              StudioTaskProgressPill(
                stepLabel =
                  exportState.stageLabel.ifBlank {
                    "Rendering ${prefs.resolution.label} MP4..."
                  },
                progressFraction = exportState.progressPercent / 100f,
                subtitle = stringResource(R.string.export_background_safe_hint),
                onCancel = onCancelExport,
                cancelContentDescription = "Cancel export",
                cancelButtonTag = "cancel_export_button",
              )
            }

            is ExportUiState.Completed -> {
              StudioGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                borderColor = StudioSuccess.copy(alpha = 0.7f),
              ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StudioSuccess)
                    Spacer(Modifier.width(8.dp))
                    Column {
                      Text(
                        stringResource(R.string.export_peak_ready_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StudioSuccess,
                      )
                      Text(
                        "Saved to Gallery • ${exportState.fileSizeBytes / 1024} KB • ${"%.1f".format(exportState.verifiedDurationMs / 1000.0)}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary,
                      )
                    }
                  }

                  ExportedVideoReviewPlayer(
                    videoUri = exportState.galleryUri,
                    aspectRatio = prefs.aspectRatio,
                  )

                  // Peak-End 1-Tap Direct Social Platform Share Row
                  Text(
                    text = "Direct Post To",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextSecondary,
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    DirectSocialSharePill(
                      label = "Reels",
                      onClick = {
                        shareVideoToPlatform(
                          context,
                          exportState.galleryUri,
                          listOf("com.instagram.android"),
                          "Share to Instagram Reels",
                        )
                      },
                      modifier = Modifier.testTag("direct_share_reels"),
                    )
                    DirectSocialSharePill(
                      label = "TikTok",
                      onClick = {
                        shareVideoToPlatform(
                          context,
                          exportState.galleryUri,
                          listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
                          "Share to TikTok",
                        )
                      },
                      modifier = Modifier.testTag("direct_share_tiktok"),
                    )
                    DirectSocialSharePill(
                      label = "Shorts",
                      onClick = {
                        shareVideoToPlatform(
                          context,
                          exportState.galleryUri,
                          listOf("com.google.android.youtube"),
                          "Share to YouTube Shorts",
                        )
                      },
                      modifier = Modifier.testTag("direct_share_shorts"),
                    )
                    DirectSocialSharePill(
                      label = "WhatsApp",
                      onClick = {
                        shareVideoToPlatform(
                          context,
                          exportState.galleryUri,
                          listOf("com.whatsapp"),
                          "Share to WhatsApp",
                        )
                      },
                      modifier = Modifier.testTag("direct_share_whatsapp"),
                    )
                  }

                  // Peak-End Viral & Retention Actions: Save Look + Share Style Link
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                      onClick = {
                        onSaveCurrentStyleAsPreset()
                        savedLookToast = true
                      },
                      shape = CircleShape,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_look_preset_button"),
                    ) {
                      Icon(
                        Icons.Filled.BookmarkAdd,
                        contentDescription = null,
                        tint = StudioCoralLight,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(Modifier.width(6.dp))
                      Text(
                        if (savedLookToast) "Saved!" else "Save Preset",
                        color = StudioTextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    OutlinedButton(
                      onClick = {
                        val activePreset = PresetCatalog.findById(project?.presetId ?: PresetCatalog.DEFAULT_PRESET_ID)
                        val link = StyleDeepLinkCodec.encodeToDeepLink(activePreset)
                        val send =
                          Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                              Intent.EXTRA_TEXT,
                              "Made with Caption Studio — copy my exact caption style: $link",
                            )
                          }
                        context.startActivity(Intent.createChooser(send, "Share Style Deep Link"))
                      },
                      shape = CircleShape,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_share_style_link"),
                    ) {
                      Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        tint = StudioCoralLight,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(Modifier.width(6.dp))
                      Text(
                        "Style Link",
                        color = StudioTextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }

                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                      onClick = {
                        shareVideoToPlatform(context, exportState.galleryUri, emptyList(), "Share Captioned Video")
                      },
                      shape = CircleShape,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                      Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(
                        "More Apps",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    OutlinedButton(
                      onClick = { launchExportSafely() },
                      shape = CircleShape,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                      Text(
                        "Export Again",
                        color = StudioTextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                      )
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
                    onClick = { launchExportSafely() },
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

@Composable
private fun DirectSocialSharePill(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    shape = CircleShape,
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = StudioCoralContainer,
        contentColor = StudioCoralLight,
      ),
    modifier = modifier.heightIn(min = 48.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      softWrap = false,
    )
  }
}

private fun shareVideoToPlatform(
  context: Context,
  videoUri: Uri,
  targetPackages: List<String>,
  chooserTitle: String,
) {
  val pm = context.packageManager
  val installedPkg =
    targetPackages.firstOrNull { pkg ->
      runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
    }
  val shareIntent =
    Intent(Intent.ACTION_SEND).apply {
      type = "video/mp4"
      putExtra(Intent.EXTRA_STREAM, videoUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      if (installedPkg != null) {
        setPackage(installedPkg)
      }
    }
  try {
    if (installedPkg != null) {
      context.startActivity(shareIntent)
    } else {
      context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
  } catch (_: Throwable) {
    val fallback =
      Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, videoUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(Intent.createChooser(fallback, chooserTitle))
  }
}

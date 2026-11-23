package com.example.captionstudio.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.ElementStylePatch
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.GeminiSettings
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.HookPacingTemplate
import com.example.captionstudio.domain.model.KaraokeHighlightMode
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.PreviewQualityMode
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.SafeZonePlatform
import com.example.captionstudio.media.AutoTranscribeUiState
import com.example.captionstudio.media.OfflineVoicePackStatus
import com.example.captionstudio.media.SpeechRecognitionState
import com.example.captionstudio.media.VoskTextSegment
import com.example.captionstudio.typography.CuratedFontPairing
import com.example.captionstudio.ui.AiConfirmationAction
import com.example.captionstudio.ui.PendingAiConfirmation
import com.example.captionstudio.ui.components.CreatorModeQuickPanel
import com.example.captionstudio.ui.components.KineticPreviewStage
import com.example.captionstudio.ui.components.StudioCircleIconButton
import com.example.captionstudio.ui.components.StudioCoralPillButton
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
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.max

private enum class EditorTool(val label: String, val tag: String) {
  STYLE("Style", "editor_tool_style"),
  THEME("Theme", "editor_tool_theme"),
  FONT("Font", "editor_tool_font"),
  TIMING("Timing", "editor_tool_timing"),
}

@Composable
fun EditorScreen(
  project: CaptionProject?,
  selectedCompositionId: String?,
  selectedElementId: String?,
  playheadMs: Long,
  canUndo: Boolean,
  canRedo: Boolean,
  isBusy: Boolean,
  statusBanner: String?,
  showSafeGuides: Boolean,
  geminiSettings: GeminiSettings,
  previewQualityMode: PreviewQualityMode,
  speechState: SpeechRecognitionState,
  autoTranscribeState: AutoTranscribeUiState,
  pendingAiConfirmation: PendingAiConfirmation?,
  onClearBanner: () -> Unit,
  onToggleSafeGuides: () -> Unit,
  onAttachVideo: (Uri) -> Unit,
  onPlayheadUpdate: (Long) -> Unit,
  onSelectComposition: (String) -> Unit,
  onSelectElement: (String, String) -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onUpdateTranscript: (String) -> Unit,
  onImportSubtitles: (String) -> Unit,
  onAutoTranscribeVideo: (preferOnline: Boolean) -> Unit,
  onStartDictation: () -> Unit,
  onStopDictation: () -> Unit,
  onAdjustWordTiming: (Int, Long, Long) -> Unit,
  transcribedSegments: List<VoskTextSegment> = emptyList(),
  onUpdateSegmentText: (String, String) -> Unit = { _, _ -> },
  onGenerateCompositions: (forceLocal: Boolean) -> Unit,
  onConfirmAiGeneration: () -> Unit,
  onDismissAiConfirmation: () -> Unit,
  onUpdatePosition: (String, Float, Float, SafeRegion, Boolean) -> Unit,
  onUpdateTiming: (String, Long, Long) -> Unit,
  onSplitComposition: (String) -> Unit,
  onMergeWithNext: (String) -> Unit,
  onDeleteComposition: (String) -> Unit,
  onAddCustomElement: (String, String, FontRole) -> Unit,
  onUpdateElementText: (String, String, String) -> Unit,
  onDeleteElement: (String, String) -> Unit = { _, _ -> },
  onPatchElementStyle: (String, String, ApplyScope, ElementStylePatch, Boolean) -> Unit,
  onResetElementStyle: (String, String) -> Unit = { _, _ -> },
  fontsVersion: Int = 0,
  onToggleDepthMask: (String) -> Unit = {},
  onApplyPreset: (String) -> Unit = {},
  onApplyPairing: (CuratedFontPairing) -> Unit = {},
  speechLanguageTag: String = "en-US",
  voicePackStatuses: List<OfflineVoicePackStatus> = emptyList(),
  onDownloadVoicePackForLanguage: (String) -> Unit = {},
  isCreatorMode: Boolean = true,
  onSetCreatorMode: (Boolean) -> Unit = {},
  customPresets: List<PresetDefinition> = emptyList(),
  safeZonePlatform: SafeZonePlatform = SafeZonePlatform.NONE,
  onCycleSafeZone: () -> Unit = {},
  onOpenStyleCloner: () -> Unit = {},
  onScaleAllText: (Float) -> Unit = {},
  onSetAllHeight: (Float) -> Unit = {},
  onSetAllEnergy: (Float) -> Unit = {},
  onPinchScaleComposition: (String, Float, Boolean) -> Unit = { _, _, _ -> },
  onApplyHookPacing: (HookPacingTemplate) -> Unit = {},
  onSetAllKaraokeMode: (KaraokeHighlightMode) -> Unit = {},
  onQuickEditWord: (Int, String, HierarchyLevel, Boolean) -> Unit = { _, _, _, _ -> },
  onDeleteWord: (Int) -> Unit = {},
  onOpenPresets: () -> Unit,
  onOpenTypography: () -> Unit,
  onOpenAnimation: () -> Unit,
  onOpenAiSettings: () -> Unit,
  onOpenExport: () -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  var isPlaying by remember { mutableStateOf(false) }
  var isMuted by remember { mutableStateOf(false) }
  var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
  var showFullscreenPreview by remember { mutableStateOf(false) }
  var activeTool by remember { mutableStateOf(EditorTool.STYLE) }
  var applyScope by remember { mutableStateOf(ApplyScope.SINGLE_ELEMENT) }
  var showAddElementDialog by remember { mutableStateOf(false) }
  var editingWordIndex by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(statusBanner) {
    if (!statusBanner.isNullOrBlank()) {
      snackbarHostState.showSnackbar(statusBanner)
      onClearBanner()
    }
  }

  val attachVideoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        onAttachVideo(uri)
      }
    }

  val subtitlePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        val text =
          runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
          }.getOrNull().orEmpty()
        if (text.isNotEmpty()) onImportSubtitles(text)
      }
    }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) onStartDictation()
    }

  if (project == null) {
    Box(modifier = Modifier.fillMaxSize().background(StudioObsidian), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = StudioCoral)
    }
    return
  }

  val activeComp =
    project.compositions.firstOrNull { playheadMs in it.startMs..it.endMs }
      ?: project.compositions.firstOrNull { it.id == selectedCompositionId }
      ?: project.compositions.firstOrNull()

  val totalDuration = max(project.metadata.durationMs, project.compositions.maxOfOrNull { it.endMs } ?: 8000L)

  Scaffold(
    containerColor = StudioObsidian,
    snackbarHost = {
      SnackbarHost(snackbarHostState) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = StudioElevated,
          contentColor = StudioTextPrimary,
          shape = RoundedCornerShape(16.dp),
        )
      }
    },
    topBar = {
      EditorMinimalTopBar(
        onBack = onBack,
        onOpenExport = onOpenExport,
      )
    },
  ) { innerPadding ->
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val isWide = maxWidth >= 820.dp

      val stageCard: @Composable (Modifier) -> Unit = { mod ->
        Box(
          modifier =
            mod
              .clip(RoundedCornerShape(24.dp))
              .background(Color(0xFF0C0C10))
              .border(1.dp, StudioHairline, RoundedCornerShape(24.dp)),
          contentAlignment = Alignment.Center,
        ) {
          KineticPreviewStage(
            videoUriString = if (showFullscreenPreview) "" else project.videoUriString,
            durationMs = totalDuration,
            aspectRatio = project.exportPreferences.aspectRatio,
            presetId = project.presetId,
            compositions = project.compositions,
            analysisReport = project.analysisReport,
            selectedCompositionId = activeComp?.id,
            playheadMs = playheadMs,
            isPlaying = isPlaying && !showFullscreenPreview,
            isMuted = isMuted,
            playbackSpeed = playbackSpeed,
            previewQuality = previewQualityMode,
            showSafeGuides = showSafeGuides,
            safeZonePlatform = safeZonePlatform,
            onPlayheadUpdate = onPlayheadUpdate,
            onPlayingChanged = { isPlaying = it },
            onDragCompositionAnchor = { id, ax, ay ->
              onUpdatePosition(id, ax, ay, SafeRegion.CUSTOM, false)
            },
            onPinchScaleComposition = onPinchScaleComposition,
            modifier = Modifier.fillMaxHeight(),
          )

          // Top-left discreet glass utility pill: Undo | Redo | Speed | Mute
          Row(
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = Color.Black.copy(alpha = 0.54f),
              shape = CircleShape,
              modifier = Modifier.border(1.dp, StudioHairline, CircleShape),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = onUndo,
                  enabled = canUndo,
                  modifier = Modifier.size(36.dp).testTag("undo_button"),
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.30f),
                    modifier = Modifier.size(16.dp),
                  )
                }
                IconButton(
                  onClick = onRedo,
                  enabled = canRedo,
                  modifier = Modifier.size(36.dp).testTag("redo_button"),
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.30f),
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }

            Surface(
              color = Color.Black.copy(alpha = 0.54f),
              shape = CircleShape,
              modifier =
                Modifier.border(1.dp, StudioHairline, CircleShape)
                  .clickable {
                    playbackSpeed =
                      when (playbackSpeed) {
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 0.5f
                        else -> 1.0f
                      }
                  }
                  .testTag("playback_speed_button"),
            ) {
              Text(
                text = "${playbackSpeed}x",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
              )
            }

            Surface(
              color = Color.Black.copy(alpha = 0.54f),
              shape = CircleShape,
              modifier =
                Modifier.size(34.dp)
                  .border(1.dp, StudioHairline, CircleShape)
                  .clickable { isMuted = !isMuted }
                  .testTag("mute_toggle_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                  contentDescription = if (isMuted) "Unmute video" else "Mute video",
                  tint = if (isMuted) StudioCoral else Color.White,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }

          // Top-right discreet glass utility pills: AI, Guides, Fullscreen, Video
          Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            StageOverlayIconPill(
              icon = Icons.Filled.AutoAwesome,
              contentDescription = "AI Settings",
              tint = if (geminiSettings.enabled) StudioCoralLight else Color.White,
              onClick = onOpenAiSettings,
              modifier = Modifier.testTag("open_ai_settings_button"),
            )
            Surface(
              color = Color.Black.copy(alpha = 0.56f),
              shape = CircleShape,
              modifier =
                Modifier.height(34.dp)
                  .border(
                    1.dp,
                    if (safeZonePlatform != SafeZonePlatform.NONE || showSafeGuides) StudioCoral.copy(alpha = 0.65f)
                    else StudioHairline,
                    CircleShape,
                  )
                  .clickable {
                    onCycleSafeZone()
                  }
                  .testTag("safe_zone_cycle_button"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = if (safeZonePlatform != SafeZonePlatform.NONE) 8.dp else 8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.GridOn,
                  contentDescription = "Cycle safe-zone overlays",
                  tint =
                    if (safeZonePlatform != SafeZonePlatform.NONE || showSafeGuides) StudioCoralLight
                    else Color.White,
                  modifier = Modifier.size(15.dp),
                )
                if (safeZonePlatform != SafeZonePlatform.NONE) {
                  Text(
                    text = safeZonePlatform.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioCoralLight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }
            StageOverlayIconPill(
              icon = Icons.Filled.Fullscreen,
              contentDescription = "Fullscreen preview",
              tint = Color.White,
              onClick = { showFullscreenPreview = true },
              modifier = Modifier.testTag("fullscreen_preview_button"),
            )
            StageOverlayIconPill(
              icon = Icons.Filled.Movie,
              contentDescription = if (project.videoUriString.isBlank()) "Attach video" else "Replace video",
              tint = if (project.videoUriString.isBlank()) StudioCoralLight else Color.White,
              onClick = {
                attachVideoLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
              },
            )
          }

          // Integrated Minimal Bottom Scrubber Overlay matching mockup
          Row(
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                  Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                  )
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            IconButton(
              onClick = { isPlaying = !isPlaying },
              modifier = Modifier.size(40.dp).testTag("play_pause_button"),
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
              )
            }

            Slider(
              value = playheadMs.toFloat().coerceIn(0f, totalDuration.toFloat()),
              onValueChange = {
                isPlaying = false
                onPlayheadUpdate(it.toLong())
              },
              valueRange = 0f..totalDuration.toFloat(),
              colors =
                SliderDefaults.colors(
                  thumbColor = Color.White,
                  activeTrackColor = Color.White,
                  inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                ),
              modifier = Modifier.weight(1f).height(26.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = formatEditorTimecode(playheadMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = " / ${formatEditorTimecode(totalDuration)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.56f),
              )
            }
          }
        }
      }

      val controlsDeck: @Composable (Modifier) -> Unit = { mod ->
        Column(
          modifier = mod,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Live Auto-Transcription Progress Banner (only visible while running)
          AnimatedVisibility(visible = autoTranscribeState.isRunning) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(StudioCard)
                  .border(1.dp, StudioCoral.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                  .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              CircularProgressIndicator(
                color = StudioCoral,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = autoTranscribeState.stageLabel.ifBlank { "Transcribing audio..." },
                style = MaterialTheme.typography.labelMedium,
                color = StudioTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Text(
                text = "${(autoTranscribeState.progressFraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = StudioCoralLight,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          // Progressive-Disclosure Mode Switcher: Creator | Pro + Clone Style (scrollable-safe on 360dp)
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier =
                Modifier.clip(CircleShape)
                  .background(StudioCard)
                  .border(1.dp, StudioHairlineStrong, CircleShape)
                  .padding(3.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Surface(
                color = if (isCreatorMode) StudioCoral else Color.Transparent,
                shape = CircleShape,
                modifier =
                  Modifier.heightIn(min = 42.dp)
                    .clip(CircleShape)
                    .clickable { onSetCreatorMode(true) }
                    .testTag("editor_mode_creator"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = "Creator",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCreatorMode) Color.White else StudioTextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                  )
                }
              }
              Surface(
                color = if (!isCreatorMode) StudioCoral else Color.Transparent,
                shape = CircleShape,
                modifier =
                  Modifier.heightIn(min = 42.dp)
                    .clip(CircleShape)
                    .clickable { onSetCreatorMode(false) }
                    .testTag("editor_mode_pro"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = "Pro Studio",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!isCreatorMode) Color.White else StudioTextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                  )
                }
              }
            }

            Surface(
              color = StudioCoralContainer.copy(alpha = 0.55f),
              shape = CircleShape,
              modifier =
                Modifier.heightIn(min = 48.dp)
                  .clip(CircleShape)
                  .border(1.dp, StudioCoral.copy(alpha = 0.45f), CircleShape)
                  .clickable(onClick = onOpenStyleCloner)
                  .testTag("editor_clone_reel_button"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint = StudioCoralLight,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = "Clone Reel",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = StudioCoralLight,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }

          if (isCreatorMode) {
            CreatorModeQuickPanel(
              project = project,
              activeComp = activeComp,
              playheadMs = playheadMs,
              totalDurationMs = totalDuration,
              waveformBins = autoTranscribeState.waveformBins,
              customPresets = customPresets,
              safeZonePlatform = safeZonePlatform,
              isBusy = isBusy,
              onApplyPreset = onApplyPreset,
              onOpenAllPresets = onOpenPresets,
              onScaleAllText = onScaleAllText,
              onSetAllHeight = onSetAllHeight,
              onSetAllEnergy = onSetAllEnergy,
              onSetAllKaraokeMode = onSetAllKaraokeMode,
              onApplyHookPacing = onApplyHookPacing,
              onCycleSafeZone = onCycleSafeZone,
              onToggleDepthMask = { activeComp?.let { onToggleDepthMask(it.id) } },
              onAutoTranscribe = { onAutoTranscribeVideo(false) },
              onOpenScriptEditor = {
                onSetCreatorMode(false)
                activeTool = EditorTool.TIMING
              },
              onSelectComposition = { compId ->
                isPlaying = false
                project.compositions.firstOrNull { it.id == compId }?.let { c ->
                  onPlayheadUpdate(c.startMs + 80L)
                }
                onSelectComposition(compId)
              },
              onUpdateTiming = onUpdateTiming,
              onWordClickSeek = { ms ->
                isPlaying = false
                onPlayheadUpdate(ms)
              },
              onWordQuickEdit = onQuickEditWord,
              onWordDelete = onDeleteWord,
            )
          } else {
            // Four Signature Apple-Minimal Tool Tiles: Style | Theme | Font | Timing
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              EditorToolTile(
                label = EditorTool.STYLE.label,
                selected = activeTool == EditorTool.STYLE,
                glyphText = "TT",
                onClick = { activeTool = EditorTool.STYLE },
                modifier = Modifier.weight(1f).testTag(EditorTool.STYLE.tag),
              )
              EditorToolTile(
                label = EditorTool.THEME.label,
                selected = activeTool == EditorTool.THEME,
                icon = Icons.Outlined.Palette,
                onClick = { activeTool = EditorTool.THEME },
                modifier = Modifier.weight(1f).testTag(EditorTool.THEME.tag),
              )
              EditorToolTile(
                label = EditorTool.FONT.label,
                selected = activeTool == EditorTool.FONT,
                glyphText = "Aa",
                onClick = { activeTool = EditorTool.FONT },
                modifier = Modifier.weight(1f).testTag(EditorTool.FONT.tag),
              )
              EditorToolTile(
                label = EditorTool.TIMING.label,
                selected = activeTool == EditorTool.TIMING,
                icon = Icons.Rounded.Tune,
                onClick = { activeTool = EditorTool.TIMING },
                modifier = Modifier.weight(1f).testTag(EditorTool.TIMING.tag),
              )
            }

            // Active Tool Workspace Content with silky spring/fade transitions
            AnimatedContent(
            targetState = activeTool,
            transitionSpec = {
              (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                slideInVertically(initialOffsetY = { it / 16 }, animationSpec = tween(220))) togetherWith
                fadeOut(tween(140))
            },
            label = "editor_tool_content",
          ) { tool ->
            when (tool) {
              EditorTool.STYLE ->
                StyleQuickPanel(
                  project = project,
                  activeComp = activeComp,
                  selectedElementId = selectedElementId,
                  applyScope = applyScope,
                  fontsVersion = fontsVersion,
                  isBusy = isBusy,
                  autoTranscribeState = autoTranscribeState,
                  geminiSettings = geminiSettings,
                  onSelectElement = { elId -> activeComp?.let { onSelectElement(it.id, elId) } },
                  onScopeChange = { applyScope = it },
                  onSelectFontForLayer = { elId, fontId ->
                    activeComp?.let { comp ->
                      onPatchElementStyle(
                        comp.id,
                        elId,
                        applyScope,
                        ElementStylePatch(fontFamilyId = fontId),
                        true,
                      )
                    }
                  },
                  onApplyPreset = onApplyPreset,
                  onApplyHookPacing = onApplyHookPacing,
                  onOpenPresets = onOpenPresets,
                  onOpenFontTab = { activeTool = EditorTool.FONT },
                  onGenerate = onGenerateCompositions,
                  onAutoTranscribe = onAutoTranscribeVideo,
                  onSelectComposition = { comp ->
                    isPlaying = false
                    onPlayheadUpdate(comp.startMs + 80L)
                    onSelectComposition(comp.id)
                  },
                  onOpenTimingTab = { activeTool = EditorTool.TIMING },
                )

              EditorTool.THEME ->
                ThemeWorkspacePanel(
                  activeComp = activeComp,
                  selectedElementId = selectedElementId,
                  applyScope = applyScope,
                  onScopeChange = { applyScope = it },
                  onSelectElement = { elId -> activeComp?.let { onSelectElement(it.id, elId) } },
                  onAddElement = { if (activeComp != null) showAddElementDialog = true },
                  onUpdateElementText = { elId, text ->
                    activeComp?.let { onUpdateElementText(it.id, elId, text) }
                  },
                  onDeleteElement = { elId ->
                    activeComp?.let { onDeleteElement(it.id, elId) }
                  },
                  onPatchStyle = { elId, patch, record ->
                    activeComp?.let { comp ->
                      onPatchElementStyle(comp.id, elId, applyScope, patch, record)
                    }
                  },
                  onResetElementStyle = { elId ->
                    activeComp?.let { comp -> onResetElementStyle(comp.id, elId) }
                  },
                  onToggleDepthMask = { activeComp?.let { onToggleDepthMask(it.id) } },
                  onOpenTypographyStudio = onOpenTypography,
                  onOpenAnimationStudio = onOpenAnimation,
                  onOpenPresets = onOpenPresets,
                )

              EditorTool.FONT ->
                FontWorkspacePanel(
                  project = project,
                  activeComp = activeComp,
                  selectedElementId = selectedElementId,
                  applyScope = applyScope,
                  fontsVersion = fontsVersion,
                  onScopeChange = { applyScope = it },
                  onSelectElement = { elId -> activeComp?.let { onSelectElement(it.id, elId) } },
                  onAddElement = { if (activeComp != null) showAddElementDialog = true },
                  onPatchStyle = { elId, patch, record ->
                    activeComp?.let { comp ->
                      onPatchElementStyle(comp.id, elId, applyScope, patch, record)
                    }
                  },
                  onResetElementStyle = { elId ->
                    activeComp?.let { comp -> onResetElementStyle(comp.id, elId) }
                  },
                  onApplyPairing = onApplyPairing,
                  onOpenTypographyStudio = onOpenTypography,
                )

              EditorTool.TIMING ->
                TimingWorkspacePanel(
                  project = project,
                  activeComp = activeComp,
                  playheadMs = playheadMs,
                  totalDurationMs = totalDuration,
                  isBusy = isBusy,
                  geminiSettings = geminiSettings,
                  speechState = speechState,
                  autoTranscribeState = autoTranscribeState,
                  transcribedSegments = transcribedSegments,
                  speechLanguageTag = speechLanguageTag,
                  voicePackStatuses = voicePackStatuses,
                  onDownloadOfflinePack = { onDownloadVoicePackForLanguage(speechLanguageTag) },
                  onSeekMs = { ms ->
                    isPlaying = false
                    onPlayheadUpdate(ms)
                  },
                  onSelectComposition = { comp ->
                    isPlaying = false
                    onPlayheadUpdate(comp.startMs + 80L)
                    onSelectComposition(comp.id)
                  },
                  onJumpPrevCaption = {
                    val prev = project.compositions.lastOrNull { it.startMs < playheadMs - 100L }
                    if (prev != null) {
                      onPlayheadUpdate(prev.startMs)
                      onSelectComposition(prev.id)
                    }
                  },
                  onJumpNextCaption = {
                    val next = project.compositions.firstOrNull { it.startMs > playheadMs + 100L }
                    if (next != null) {
                      onPlayheadUpdate(next.startMs)
                      onSelectComposition(next.id)
                    }
                  },
                  onGenerate = onGenerateCompositions,
                  onAutoTranscribe = onAutoTranscribeVideo,
                  onSplit = { activeComp?.let { onSplitComposition(it.id) } },
                  onMerge = { activeComp?.let { onMergeWithNext(it.id) } },
                  onDelete = { activeComp?.let { onDeleteComposition(it.id) } },
                  onAddElement = { if (activeComp != null) showAddElementDialog = true },
                  onUpdateTiming = onUpdateTiming,
                  onToggleDepthMask = { activeComp?.let { onToggleDepthMask(it.id) } },
                  onUpdateTranscript = onUpdateTranscript,
                  onUpdateSegmentText = onUpdateSegmentText,
                  onImportSubtitles = {
                    subtitlePickerLauncher.launch(arrayOf("text/*", "application/x-subrip"))
                  },
                  onToggleDictation = {
                    if (speechState.isListening) {
                      onStopDictation()
                    } else {
                      val hasMic =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                          PackageManager.PERMISSION_GRANTED
                      if (hasMic) onStartDictation()
                      else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                  },
                  onEditWordTiming = { idx ->
                    project.wordTimings.getOrNull(idx)?.let { wt ->
                      isPlaying = false
                      onPlayheadUpdate(wt.startMs)
                    }
                    editingWordIndex = idx
                  },
                  onOpenAnimationStudio = onOpenAnimation,
                  onOpenPresets = onOpenPresets,
                )
              }
            }
          }
        }
      }

      if (isWide) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          stageCard(Modifier.weight(1f).fillMaxHeight())
          controlsDeck(
            Modifier.weight(1.05f)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState())
              .padding(bottom = 12.dp)
          )
        }
      } else {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .navigationBarsPadding()
              .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          stageCard(Modifier.fillMaxWidth().height(328.dp))
          controlsDeck(Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
      }
    }
  }

  // Fullscreen Review Dialog
  if (showFullscreenPreview) {
    Dialog(
      onDismissRequest = { showFullscreenPreview = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().background(StudioObsidian).statusBarsPadding().navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = project.title,
            style = MaterialTheme.typography.titleMedium,
            color = StudioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          StudioCircleIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close fullscreen preview",
            onClick = { showFullscreenPreview = false },
          )
        }
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          KineticPreviewStage(
            videoUriString = project.videoUriString,
            durationMs = totalDuration,
            aspectRatio = project.exportPreferences.aspectRatio,
            presetId = project.presetId,
            compositions = project.compositions,
            analysisReport = project.analysisReport,
            selectedCompositionId = activeComp?.id,
            playheadMs = playheadMs,
            isPlaying = isPlaying,
            isMuted = isMuted,
            playbackSpeed = playbackSpeed,
            previewQuality = previewQualityMode,
            showSafeGuides = showSafeGuides,
            safeZonePlatform = safeZonePlatform,
            onPlayheadUpdate = onPlayheadUpdate,
            onPlayingChanged = { isPlaying = it },
            onDragCompositionAnchor = { id, ax, ay ->
              onUpdatePosition(id, ax, ay, SafeRegion.CUSTOM, false)
            },
            onPinchScaleComposition = onPinchScaleComposition,
            modifier = Modifier.fillMaxHeight(),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier.size(46.dp).clip(CircleShape).background(StudioCoral),
          ) {
            Icon(
              if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play",
              tint = Color.White,
            )
          }
          Slider(
            value = playheadMs.toFloat().coerceIn(0f, totalDuration.toFloat()),
            onValueChange = {
              isPlaying = false
              onPlayheadUpdate(it.toLong())
            },
            valueRange = 0f..totalDuration.toFloat(),
            colors =
              SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f),
              ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
          )
          IconButton(onClick = { isMuted = !isMuted }) {
            Icon(
              if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = "Mute",
              tint = if (isMuted) StudioCoral else Color.White,
            )
          }
        }
      }
    }
  }

  // Cost Protection Confirmation Dialog before Gemini Request
  pendingAiConfirmation?.let { info ->
    val isAudioTranscribe = info.action == AiConfirmationAction.TRANSCRIBE_AUDIO
    AlertDialog(
      onDismissRequest = onDismissAiConfirmation,
      containerColor = StudioCard,
      shape = RoundedCornerShape(24.dp),
      title = {
        Text(
          if (isAudioTranscribe) "Confirm AI Transcription" else "Confirm AI Caption Analysis",
          style = MaterialTheme.typography.titleLarge,
          color = StudioTextPrimary,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Model: ${info.modelDisplayName}", color = StudioCoralLight, fontWeight = FontWeight.SemiBold)
          Text(
            text =
              if (isAudioTranscribe) {
                "Token budget: ${info.tokenBudget} max output tokens\nPayload: Mono audio clip for millisecond word transcription"
              } else {
                "Token budget: ${info.tokenBudget} max output tokens\nPayload: ${if (info.includesFrames) "Transcript + ${info.frameCount} sampled low-res frames" else "Transcript text & timings only"}"
              },
            style = MaterialTheme.typography.bodyMedium,
            color = StudioTextSecondary,
          )
          if (info.isPaidOrRestricted) {
            Text(
              "Note: This model may require a paid tier or strict quota.",
              color = StudioError,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = onConfirmAiGeneration,
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
          modifier = Modifier.testTag("confirm_ai_request_button"),
        ) {
          Text(if (isAudioTranscribe) "Run Transcribe" else "Continue", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissAiConfirmation) {
          Text("Cancel", color = StudioTextSecondary)
        }
      },
    )
  }

  // Add Custom Element Dialog
  if (showAddElementDialog && activeComp != null) {
    var textInput by remember { mutableStateOf("") }
    var roleInput by remember { mutableStateOf(FontRole.SUPPORTING) }
    AlertDialog(
      onDismissRequest = { showAddElementDialog = false },
      containerColor = StudioCard,
      shape = RoundedCornerShape(24.dp),
      title = { Text("Add Text Element", style = MaterialTheme.typography.titleLarge, color = StudioTextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Word or phrase") },
            singleLine = true,
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
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FontRole.entries.forEach { role ->
              FilterChip(
                selected = roleInput == role,
                onClick = { roleInput = role },
                shape = CircleShape,
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StudioCoralContainer,
                    selectedLabelColor = StudioCoralLight,
                  ),
                label = {
                  Text(
                    role.name.lowercase().replaceFirstChar { it.uppercase() },
                    maxLines = 1,
                    softWrap = false,
                  )
                },
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onAddCustomElement(activeComp.id, textInput, roleInput)
            showAddElementDialog = false
          },
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
        ) {
          Text("Add", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddElementDialog = false }) {
          Text("Cancel", color = StudioTextSecondary)
        }
      },
    )
  }

  // Adjust Word Timing Dialog
  editingWordIndex?.let { idx ->
    val wt = project.wordTimings.getOrNull(idx)
    if (wt != null) {
      var startInput by remember(idx) { mutableStateOf(wt.startMs.toString()) }
      var endInput by remember(idx) { mutableStateOf(wt.endMs.toString()) }
      AlertDialog(
        onDismissRequest = { editingWordIndex = null },
        containerColor = StudioCard,
        shape = RoundedCornerShape(24.dp),
        title = {
          Text(
            "Adjust Timing: \"${wt.word}\"",
            style = MaterialTheme.typography.titleMedium,
            color = StudioTextPrimary,
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = startInput,
              onValueChange = { startInput = it },
              label = { Text("Start (ms)") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = StudioCoral,
                  unfocusedBorderColor = StudioBorder,
                  focusedLabelColor = StudioCoral,
                  cursorColor = StudioCoral,
                ),
            )
            OutlinedTextField(
              value = endInput,
              onValueChange = { endInput = it },
              label = { Text("End (ms)") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = StudioCoral,
                  unfocusedBorderColor = StudioBorder,
                  focusedLabelColor = StudioCoral,
                  cursorColor = StudioCoral,
                ),
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val s = startInput.toLongOrNull() ?: wt.startMs
              val e = endInput.toLongOrNull() ?: wt.endMs
              onAdjustWordTiming(idx, s, e)
              editingWordIndex = null
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
          ) {
            Text("Save", fontWeight = FontWeight.SemiBold)
          }
        },
        dismissButton = {
          TextButton(onClick = { editingWordIndex = null }) {
            Text("Cancel", color = StudioTextSecondary)
          }
        },
      )
    }
  }
}

@Composable
private fun EditorMinimalTopBar(
  onBack: () -> Unit,
  onOpenExport: () -> Unit,
) {
  val exportInteraction = remember { MutableInteractionSource() }
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 18.dp, vertical = 10.dp),
  ) {
    StudioCircleIconButton(
      icon = Icons.AutoMirrored.Rounded.ArrowBack,
      contentDescription = "Back to Home",
      onClick = onBack,
      modifier = Modifier.align(Alignment.CenterStart).testTag("editor_back_button"),
    )

    Text(
      text = "Edit Captions",
      style = MaterialTheme.typography.titleMedium,
      fontSize = 17.sp,
      fontWeight = FontWeight.SemiBold,
      color = StudioTextPrimary,
      maxLines = 1,
      modifier = Modifier.align(Alignment.Center),
    )

    // Top-right warm coral Export pill matching reference mockup
    Box(
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .studioPressScale(interactionSource = exportInteraction)
          .clip(CircleShape)
          .background(
            Brush.horizontalGradient(listOf(StudioCoralLight, StudioCoral))
          )
          .clickable(
            interactionSource = exportInteraction,
            indication = ripple(bounded = true, color = Color.White),
            onClick = onOpenExport,
          )
          .padding(horizontal = 16.dp, vertical = 10.dp)
          .testTag("open_export_button"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.FileUpload,
          contentDescription = null,
          tint = StudioObsidian,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "Export",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = StudioObsidian,
          maxLines = 1,
          softWrap = false,
        )
      }
    }
  }
}

@Composable
private fun StageOverlayIconPill(
  icon: ImageVector,
  contentDescription: String,
  tint: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.Black.copy(alpha = 0.54f),
    shape = CircleShape,
    modifier =
      modifier
        .size(34.dp)
        .border(1.dp, StudioHairline, CircleShape)
        .clickable(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

@Composable
private fun EditorToolTile(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  glyphText: String? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val shape = RoundedCornerShape(18.dp)

  Box(
    modifier =
      modifier
        .height(74.dp)
        .studioPressScale(interactionSource = interactionSource)
        .clip(shape)
        .background(if (selected) Color(0xFF1B191D) else StudioCard)
        .border(
          width = if (selected) 1.2.dp else 1.dp,
          color = if (selected) StudioCoral.copy(alpha = 0.65f) else StudioHairline,
          shape = shape,
        )
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onClick,
        ),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      if (glyphText != null) {
        Text(
          text = glyphText,
          fontSize = 18.sp,
          fontWeight = FontWeight.Medium,
          color = if (selected) StudioTextPrimary else StudioTextPrimary.copy(alpha = 0.88f),
        )
      } else if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (selected) StudioTextPrimary else StudioTextPrimary.copy(alpha = 0.88f),
          modifier = Modifier.size(21.dp),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) StudioCoralLight else StudioTextSecondary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
  }
}

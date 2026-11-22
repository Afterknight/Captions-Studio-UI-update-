package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.ui.StudioViewModel
import com.example.captionstudio.ui.screens.AiSettingsScreen
import com.example.captionstudio.ui.screens.AnimationScreen
import com.example.captionstudio.ui.screens.EditorScreen
import com.example.captionstudio.ui.screens.ExportScreen
import com.example.captionstudio.ui.screens.HomeScreen
import com.example.captionstudio.ui.screens.PresetsScreen
import com.example.captionstudio.ui.screens.TypographyScreen
import com.example.ui.theme.CaptionStudioTheme
import kotlinx.serialization.Serializable

@Serializable object HomeRoute

@Serializable data class EditorRoute(val projectId: String)

@Serializable data class PresetsRoute(val projectId: String)

@Serializable data class TypographyRoute(val projectId: String)

@Serializable data class AnimationRoute(val projectId: String)

@Serializable object AiSettingsRoute

@Serializable data class ExportRoute(val projectId: String)

@Composable
fun CaptionStudioApp(studioViewModel: StudioViewModel = viewModel()) {
  val navController = rememberNavController()

  val projects by studioViewModel.projects.collectAsStateWithLifecycle()
  val activeProject by studioViewModel.activeProject.collectAsStateWithLifecycle()
  val selectedCompId by studioViewModel.selectedCompositionId.collectAsStateWithLifecycle()
  val selectedElId by studioViewModel.selectedElementId.collectAsStateWithLifecycle()
  val playheadMs by studioViewModel.playheadMs.collectAsStateWithLifecycle()
  val canUndo by studioViewModel.canUndo.collectAsStateWithLifecycle()
  val canRedo by studioViewModel.canRedo.collectAsStateWithLifecycle()
  val isBusy by studioViewModel.isBusy.collectAsStateWithLifecycle()
  val statusBanner by studioViewModel.statusBanner.collectAsStateWithLifecycle()
  val showSafeGuides by studioViewModel.showSafeGuides.collectAsStateWithLifecycle()
  val geminiSettings by studioViewModel.geminiSettings.collectAsStateWithLifecycle()
  val previewQuality by studioViewModel.previewQualityMode.collectAsStateWithLifecycle()
  val speechLanguage by studioViewModel.speechLanguageTag.collectAsStateWithLifecycle()
  val speechState by studioViewModel.speechState.collectAsStateWithLifecycle()
  val autoTranscribeState by studioViewModel.autoTranscribeState.collectAsStateWithLifecycle()
  val exportState by studioViewModel.exportState.collectAsStateWithLifecycle()
  val pendingAiConfirm by studioViewModel.pendingAiConfirmation.collectAsStateWithLifecycle()
  val hasSavedKey by studioViewModel.hasSavedApiKey.collectAsStateWithLifecycle()
  val voicePackStatuses by studioViewModel.voicePackStatuses.collectAsStateWithLifecycle()
  val transcribedSegments by studioViewModel.transcribedSegments.collectAsStateWithLifecycle()

  val navAnimDuration = 280

  NavHost(
    navController = navController,
    startDestination = HomeRoute,
    enterTransition = {
      slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(navAnimDuration, easing = FastOutSlowInEasing),
        initialOffset = { it / 4 },
      ) + fadeIn(tween(navAnimDuration, easing = FastOutSlowInEasing))
    },
    exitTransition = {
      slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(navAnimDuration, easing = FastOutSlowInEasing),
        targetOffset = { it / 6 },
      ) + fadeOut(tween(200))
    },
    popEnterTransition = {
      slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(navAnimDuration, easing = FastOutSlowInEasing),
        initialOffset = { it / 6 },
      ) + fadeIn(tween(navAnimDuration, easing = FastOutSlowInEasing))
    },
    popExitTransition = {
      slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(navAnimDuration, easing = FastOutSlowInEasing),
        targetOffset = { it / 4 },
      ) + fadeOut(tween(200))
    },
  ) {
    composable<HomeRoute> {
      HomeScreen(
        projects = projects,
        geminiSettings = geminiSettings,
        isBusy = isBusy,
        onOpenProject = { id ->
          studioViewModel.openProject(id)
          navController.navigate(EditorRoute(id))
        },
        onImportVideo = { uri, title -> studioViewModel.importVideoAndCreateProject(uri, title) },
        onCreateProject = { title, loadRef ->
          studioViewModel.createBlankProject(title, loadRef)
        },
        onRenameProject = studioViewModel::renameProject,
        onDeleteProject = studioViewModel::deleteProject,
        onOpenSettings = { navController.navigate(AiSettingsRoute) },
      )
    }

    composable<EditorRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<EditorRoute>()
      LaunchedEffect(route.projectId) {
        if (activeProject?.id != route.projectId) {
          studioViewModel.openProject(route.projectId)
        }
      }
      EditorScreen(
        project = activeProject,
        selectedCompositionId = selectedCompId,
        selectedElementId = selectedElId,
        playheadMs = playheadMs,
        canUndo = canUndo,
        canRedo = canRedo,
        isBusy = isBusy,
        statusBanner = statusBanner,
        showSafeGuides = showSafeGuides,
        geminiSettings = geminiSettings,
        previewQualityMode = previewQuality,
        speechState = speechState,
        autoTranscribeState = autoTranscribeState,
        pendingAiConfirmation = pendingAiConfirm,
        onClearBanner = studioViewModel::clearBanner,
        onToggleSafeGuides = studioViewModel::toggleSafeGuides,
        onAttachVideo = studioViewModel::attachVideoToActiveProject,
        onPlayheadUpdate = studioViewModel::updatePlayhead,
        onSelectComposition = studioViewModel::selectComposition,
        onSelectElement = studioViewModel::selectElement,
        onUndo = studioViewModel::undo,
        onRedo = studioViewModel::redo,
        onUpdateTranscript = studioViewModel::updateTranscriptText,
        onImportSubtitles = studioViewModel::importSubtitles,
        onAutoTranscribeVideo = studioViewModel::requestAutoTranscribeVideo,
        onStartDictation = studioViewModel::startSpeechDictation,
        onStopDictation = studioViewModel::stopSpeechDictation,
        onAdjustWordTiming = studioViewModel::adjustWordTiming,
        transcribedSegments = transcribedSegments,
        onUpdateSegmentText = studioViewModel::updateTranscribedSegmentText,
        onGenerateCompositions = studioViewModel::requestGenerateCompositions,
        onConfirmAiGeneration = studioViewModel::confirmAiGeneration,
        onDismissAiConfirmation = studioViewModel::dismissAiConfirmation,
        onUpdatePosition = studioViewModel::updateCompositionPosition,
        onUpdateTiming = studioViewModel::updateCompositionTiming,
        onSplitComposition = studioViewModel::splitComposition,
        onMergeWithNext = studioViewModel::mergeWithNextComposition,
        onDeleteComposition = studioViewModel::deleteComposition,
        onAddCustomElement = studioViewModel::addCustomElement,
        onUpdateElementText = studioViewModel::updateElementText,
        onQuickColorElement = { compId, elId, hex ->
          studioViewModel.applyElementStyle(
            compId = compId,
            elementId = elId,
            scope = ApplyScope.SINGLE_ELEMENT,
            colorHex = hex,
          )
        },
        onToggleDepthMask = studioViewModel::toggleCompositionDepthMask,
        onApplyPreset = studioViewModel::applyPreset,
        onApplyPairing = studioViewModel::applyCuratedPairing,
        speechLanguageTag = speechLanguage,
        voicePackStatuses = voicePackStatuses,
        onDownloadVoicePackForLanguage = studioViewModel::downloadOfflineVoicePackForLanguage,
        onOpenPresets = { navController.navigate(PresetsRoute(route.projectId)) },
        onOpenTypography = { navController.navigate(TypographyRoute(route.projectId)) },
        onOpenAnimation = { navController.navigate(AnimationRoute(route.projectId)) },
        onOpenAiSettings = { navController.navigate(AiSettingsRoute) },
        onOpenExport = {
          studioViewModel.resetExportState()
          navController.navigate(ExportRoute(route.projectId))
        },
        onBack = { navController.popBackStack() },
      )
    }

    composable<PresetsRoute> {
      PresetsScreen(
        activePresetId = activeProject?.presetId ?: "editorial",
        onApplyPreset = studioViewModel::applyPreset,
        onBack = { navController.popBackStack() },
      )
    }

    composable<TypographyRoute> {
      TypographyScreen(
        project = activeProject,
        selectedCompositionId = selectedCompId,
        selectedElementId = selectedElId,
        onApplyStyle = studioViewModel::applyElementStyle,
        onApplyPairing = studioViewModel::applyCuratedPairing,
        onDownloadFont = studioViewModel::downloadFontFamily,
        onBack = { navController.popBackStack() },
      )
    }

    composable<AnimationRoute> {
      AnimationScreen(
        project = activeProject,
        selectedCompositionId = selectedCompId,
        onApplyAnimation = studioViewModel::applyAnimationSettings,
        onBack = { navController.popBackStack() },
      )
    }

    composable<AiSettingsRoute> {
      AiSettingsScreen(
        settings = geminiSettings,
        hasSavedApiKey = hasSavedKey,
        previewQualityMode = previewQuality,
        speechLanguageTag = speechLanguage,
        onSaveApiKey = studioViewModel::saveApiKey,
        onRemoveApiKey = studioViewModel::removeApiKey,
        onTestConnection = studioViewModel::testGeminiConnection,
        onRefreshModels = studioViewModel::refreshAvailableModels,
        onSetGeminiEnabled = studioViewModel::setGeminiEnabled,
        onSetUsageMode = studioViewModel::setUsageMode,
        onSetSelectedModel = studioViewModel::setSelectedModel,
        onSetConfirmBeforeRequest = studioViewModel::setConfirmBeforeRequest,
        onSetTokenBudget = studioViewModel::setTokenBudget,
        onSetTranscriptOnly = studioViewModel::setTranscriptOnly,
        onSetAllowFrameSharing = studioViewModel::setAllowFrameSharing,
        onSetPreviewQuality = studioViewModel::setPreviewQualityMode,
        onSetSpeechLanguage = studioViewModel::setSpeechLanguageTag,
        voicePackStatuses = voicePackStatuses,
        onDownloadVoicePack = studioViewModel::downloadOfflineVoicePack,
        onDeleteVoicePack = studioViewModel::deleteOfflineVoicePack,
        onBack = { navController.popBackStack() },
      )
    }

    composable<ExportRoute> {
      ExportScreen(
        project = activeProject,
        exportState = exportState,
        onUpdatePreferences = studioViewModel::updateExportPreferences,
        onStartExport = studioViewModel::startExport,
        onCancelExport = studioViewModel::cancelExport,
        onBack = { navController.popBackStack() },
      )
    }
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setContent { CaptionStudioTheme { CaptionStudioApp() } }
  }
}

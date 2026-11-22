package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.captionstudio.ui.StudioViewModel
import com.example.captionstudio.ui.screens.AiSettingsScreen
import com.example.captionstudio.ui.screens.AnimationScreen
import com.example.captionstudio.ui.screens.EditorScreen
import com.example.captionstudio.ui.screens.ExportScreen
import com.example.captionstudio.ui.screens.PresetsScreen
import com.example.captionstudio.ui.screens.ProjectGalleryScreen
import com.example.captionstudio.ui.screens.StyleClonerScreen
import com.example.captionstudio.ui.screens.TypographyScreen
import com.example.ui.theme.CaptionStudioTheme
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

private val StudioEmphasizedEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Serializable object HomeRoute

@Serializable data class EditorRoute(val projectId: String)

@Serializable data class PresetsRoute(val projectId: String)

@Serializable data class TypographyRoute(val projectId: String)

@Serializable data class AnimationRoute(val projectId: String)

@Serializable object AiSettingsRoute

@Serializable data class ExportRoute(val projectId: String)

@Serializable object StyleClonerRoute

@Composable
fun CaptionStudioApp(
  pendingDeepLink: String? = null,
  onDeepLinkConsumed: () -> Unit = {},
  studioViewModel: StudioViewModel = viewModel(),
) {
  val context = LocalContext.current
  val navController = rememberNavController()

  val projects by studioViewModel.projects.collectAsStateWithLifecycle()
  val filteredGalleryProjects by studioViewModel.filteredGalleryProjects.collectAsStateWithLifecycle()
  val gallerySearchQuery by studioViewModel.gallerySearchQuery.collectAsStateWithLifecycle()
  val gallerySortOption by studioViewModel.gallerySortOption.collectAsStateWithLifecycle()
  val isRefreshingProjects by studioViewModel.isRefreshingProjects.collectAsStateWithLifecycle()
  val thumbnailRefreshNonce by studioViewModel.thumbnailRefreshNonce.collectAsStateWithLifecycle()
  val activeProject by studioViewModel.activeProject.collectAsStateWithLifecycle()
  val selectedCompId by studioViewModel.selectedCompositionId.collectAsStateWithLifecycle()
  val selectedElId by studioViewModel.selectedElementId.collectAsStateWithLifecycle()
  val playheadMs by studioViewModel.playheadMs.collectAsStateWithLifecycle()
  val canUndo by studioViewModel.canUndo.collectAsStateWithLifecycle()
  val canRedo by studioViewModel.canRedo.collectAsStateWithLifecycle()
  val isBusy by studioViewModel.isBusy.collectAsStateWithLifecycle()
  val statusBanner by studioViewModel.statusBanner.collectAsStateWithLifecycle()
  val showSafeGuides by studioViewModel.showSafeGuides.collectAsStateWithLifecycle()
  val safeZonePlatform by studioViewModel.safeZonePlatform.collectAsStateWithLifecycle()
  val customPresets by studioViewModel.customPresets.collectAsStateWithLifecycle()
  val isCreatorMode by studioViewModel.isCreatorMode.collectAsStateWithLifecycle()
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
  val fontsVersion by studioViewModel.fontsVersion.collectAsStateWithLifecycle()

  LaunchedEffect(pendingDeepLink) {
    if (!pendingDeepLink.isNullOrBlank()) {
      val imported = studioViewModel.importStyleFromDeepLink(pendingDeepLink)
      Toast.makeText(
        context,
        if (imported) "Caption style imported & ready!" else "Could not read style link.",
        Toast.LENGTH_SHORT,
      ).show()
      onDeepLinkConsumed()
    }
  }

  val navAnimDuration = 300

  NavHost(
    navController = navController,
    startDestination = HomeRoute,
    enterTransition = {
      slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(navAnimDuration, easing = StudioEmphasizedEasing),
        initialOffset = { it / 6 },
      ) + fadeIn(tween(navAnimDuration, easing = FastOutSlowInEasing))
    },
    exitTransition = {
      slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(navAnimDuration, easing = StudioEmphasizedEasing),
        targetOffset = { it / 8 },
      ) + fadeOut(tween(200))
    },
    popEnterTransition = {
      slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(navAnimDuration, easing = StudioEmphasizedEasing),
        initialOffset = { it / 8 },
      ) + fadeIn(tween(navAnimDuration, easing = FastOutSlowInEasing))
    },
    popExitTransition = {
      slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(navAnimDuration, easing = StudioEmphasizedEasing),
        targetOffset = { it / 6 },
      ) + fadeOut(tween(200))
    },
  ) {
    composable<HomeRoute>(
      exitTransition = {
        if (targetState.destination.hasRoute<EditorRoute>()) {
          scaleOut(
            targetScale = 0.94f,
            animationSpec = tween(320, easing = StudioEmphasizedEasing),
          ) + fadeOut(tween(220, easing = FastOutSlowInEasing))
        } else {
          slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(navAnimDuration, easing = StudioEmphasizedEasing),
            targetOffset = { it / 8 },
          ) + fadeOut(tween(200))
        }
      },
      popEnterTransition = {
        if (initialState.destination.hasRoute<EditorRoute>()) {
          scaleIn(
            initialScale = 0.94f,
            animationSpec = tween(340, easing = StudioEmphasizedEasing),
          ) + fadeIn(tween(280, easing = FastOutSlowInEasing))
        } else {
          slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(navAnimDuration, easing = StudioEmphasizedEasing),
            initialOffset = { it / 8 },
          ) + fadeIn(tween(navAnimDuration, easing = FastOutSlowInEasing))
        }
      },
    ) {
      ProjectGalleryScreen(
        projects = filteredGalleryProjects,
        totalProjectCount = projects.size,
        searchQuery = gallerySearchQuery,
        sortOption = gallerySortOption,
        isRefreshing = isRefreshingProjects,
        thumbnailRefreshKey = thumbnailRefreshNonce,
        geminiSettings = geminiSettings,
        isBusy = isBusy,
        onSearchQueryChange = studioViewModel::updateGallerySearchQuery,
        onSortOptionChange = studioViewModel::updateGallerySortOption,
        onRefreshProjects = studioViewModel::refreshProjects,
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
        onOpenStyleCloner = { navController.navigate(StyleClonerRoute) },
        onOpenSettings = { navController.navigate(AiSettingsRoute) },
      )
    }

    composable<EditorRoute>(
      enterTransition = {
        if (initialState.destination.hasRoute<HomeRoute>()) {
          scaleIn(
            initialScale = 1.035f,
            animationSpec = tween(360, easing = StudioEmphasizedEasing),
          ) +
            slideInVertically(
              initialOffsetY = { it / 28 },
              animationSpec = tween(360, easing = StudioEmphasizedEasing),
            ) +
            fadeIn(tween(260, easing = FastOutSlowInEasing))
        } else {
          slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(navAnimDuration, easing = StudioEmphasizedEasing),
            initialOffset = { it / 6 },
          ) + fadeIn(tween(navAnimDuration, easing = FastOutSlowInEasing))
        }
      },
      popExitTransition = {
        if (targetState.destination.hasRoute<HomeRoute>()) {
          scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(280, easing = FastOutSlowInEasing),
          ) +
            slideOutVertically(
              targetOffsetY = { it / 24 },
              animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) +
            fadeOut(tween(210))
        } else {
          slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(navAnimDuration, easing = StudioEmphasizedEasing),
            targetOffset = { it / 6 },
          ) + fadeOut(tween(200))
        }
      },
    ) { backStackEntry ->
      val route = backStackEntry.toRoute<EditorRoute>()
      LaunchedEffect(route.projectId) {
        if (activeProject?.id != route.projectId) {
          studioViewModel.openProject(route.projectId)
        }
      }
      val resolvedProject = activeProject?.takeIf { it.id == route.projectId }
      AnimatedContent(
        targetState = resolvedProject?.id,
        transitionSpec = {
          (fadeIn(tween(260, easing = StudioEmphasizedEasing)) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(260, easing = StudioEmphasizedEasing)))
            .togetherWith(fadeOut(tween(170)))
        },
        label = "editor_workspace_transition",
      ) { loadedProjectId ->
        if (loadedProjectId == null) {
          EditorWorkspaceLoadingPlaceholder()
        } else {
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
            onDeleteElement = studioViewModel::deleteElement,
            onPatchElementStyle = studioViewModel::updateElementStyle,
            onResetElementStyle = studioViewModel::resetElementStyle,
            fontsVersion = fontsVersion,
            onToggleDepthMask = studioViewModel::toggleCompositionDepthMask,
            onApplyPreset = studioViewModel::applyPreset,
            onApplyPairing = studioViewModel::applyCuratedPairing,
            speechLanguageTag = speechLanguage,
            voicePackStatuses = voicePackStatuses,
            onDownloadVoicePackForLanguage = studioViewModel::downloadOfflineVoicePackForLanguage,
            isCreatorMode = isCreatorMode,
            onSetCreatorMode = studioViewModel::setCreatorMode,
            customPresets = customPresets,
            safeZonePlatform = safeZonePlatform,
            onCycleSafeZone = studioViewModel::cycleSafeZonePlatform,
            onOpenStyleCloner = { navController.navigate(StyleClonerRoute) },
            onScaleAllText = studioViewModel::scaleAllCompositionsText,
            onSetAllHeight = studioViewModel::setAllCompositionsHeight,
            onSetAllEnergy = studioViewModel::setAllCompositionsEnergy,
            onQuickEditWord = studioViewModel::quickEditWord,
            onDeleteWord = studioViewModel::deleteWordAt,
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
      }
    }

    composable<PresetsRoute> {
      PresetsScreen(
        activePresetId = activeProject?.presetId ?: "editorial",
        customPresets = customPresets,
        onApplyPreset = studioViewModel::applyPreset,
        onOpenStyleCloner = { navController.navigate(StyleClonerRoute) },
        onImportStyleLink = studioViewModel::importStyleFromDeepLink,
        onDeleteCustomPreset = studioViewModel::deleteCustomPreset,
        onBack = { navController.popBackStack() },
      )
    }

    composable<StyleClonerRoute> {
      StyleClonerScreen(
        hasActiveProject = activeProject != null,
        onSaveAndApplyPreset = { preset, apply ->
          studioViewModel.saveCustomPreset(preset, applyToActiveProject = apply)
          navController.popBackStack()
        },
        onBack = { navController.popBackStack() },
      )
    }

    composable<TypographyRoute> {
      TypographyScreen(
        project = activeProject,
        selectedCompositionId = selectedCompId,
        selectedElementId = selectedElId,
        fontsVersion = fontsVersion,
        canUndo = canUndo,
        onUndo = studioViewModel::undo,
        onSelectElement = studioViewModel::selectElement,
        onUpdateElementText = studioViewModel::updateElementText,
        onDeleteElement = studioViewModel::deleteElement,
        onPatchStyle = studioViewModel::updateElementStyle,
        onResetElementStyle = studioViewModel::resetElementStyle,
        onToggleDepthMask = studioViewModel::toggleCompositionDepthMask,
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
        onSaveCurrentStyleAsPreset = {
          val presetName = activeProject?.title?.take(18)?.ifBlank { "MY LOOK" } ?: "MY LOOK"
          studioViewModel.buildCurrentProjectPreset(presetName)?.let { built ->
            studioViewModel.saveCustomPreset(built, applyToActiveProject = false)
          }
        },
        onBack = { navController.popBackStack() },
      )
    }
  }
}

@Composable
private fun EditorWorkspaceLoadingPlaceholder() {
  Box(
    modifier = Modifier.fillMaxSize().background(StudioObsidian),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CircularProgressIndicator(
        color = StudioCoral,
        strokeWidth = 2.5.dp,
        modifier = Modifier.size(32.dp),
      )
      Text(
        text = stringResource(R.string.editor_loading_workspace),
        style = MaterialTheme.typography.labelLarge,
        color = StudioTextSecondary,
      )
    }
  }
}

class MainActivity : ComponentActivity() {
  private val pendingDeepLinkFlow = MutableStateFlow<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    captureStyleDeepLink(intent)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setContent {
      val deepLink by pendingDeepLinkFlow.collectAsStateWithLifecycle()
      CaptionStudioTheme {
        CaptionStudioApp(
          pendingDeepLink = deepLink,
          onDeepLinkConsumed = { pendingDeepLinkFlow.value = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    captureStyleDeepLink(intent)
  }

  private fun captureStyleDeepLink(intent: Intent?) {
    val data = intent?.dataString ?: return
    if (data.startsWith("captionstudio://style")) {
      pendingDeepLinkFlow.value = data
    }
  }
}

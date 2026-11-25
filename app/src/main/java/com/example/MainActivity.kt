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
import com.example.captionstudio.ui.screens.EditorScreen
import com.example.captionstudio.ui.screens.ExportScreen
import com.example.captionstudio.ui.screens.ProjectGalleryScreen
import com.example.ui.theme.CaptionStudioTheme
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

private val StudioEmphasizedEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Serializable object HomeRoute

@Serializable data class EditorRoute(val projectId: String)

@Serializable data class ExportRoute(val projectId: String)

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
  val previewQuality by studioViewModel.previewQualityMode.collectAsStateWithLifecycle()
  val speechLanguage by studioViewModel.speechLanguageTag.collectAsStateWithLifecycle()
  val speechState by studioViewModel.speechState.collectAsStateWithLifecycle()
  val autoTranscribeState by studioViewModel.autoTranscribeState.collectAsStateWithLifecycle()
  val exportState by studioViewModel.exportState.collectAsStateWithLifecycle()
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
            previewQualityMode = previewQuality,
            speechState = speechState,
            autoTranscribeState = autoTranscribeState,
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
            onApplyAnimation = studioViewModel::applyAnimationSettings,
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
            onScaleAllText = studioViewModel::scaleAllCompositionsText,
            onSetAllHeight = studioViewModel::setAllCompositionsHeight,
            onSetAllEnergy = studioViewModel::setAllCompositionsEnergy,
            onPinchScaleComposition = studioViewModel::scaleCompositionElements,
            onApplyHookPacing = studioViewModel::applyHookPacingTemplate,
            onSetAllKaraokeMode = { mode -> studioViewModel.setAllKaraokeMode(mode) },
            onQuickEditWord = studioViewModel::quickEditWord,
            onDeleteWord = studioViewModel::deleteWordAt,
            onOpenExport = {
              studioViewModel.resetExportState()
              navController.navigate(ExportRoute(route.projectId))
            },
            onBack = { navController.popBackStack() },
          )
        }
      }
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

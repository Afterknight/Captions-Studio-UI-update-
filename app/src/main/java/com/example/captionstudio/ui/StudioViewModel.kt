package com.example.captionstudio.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.captionstudio.data.local.AppDatabase
import com.example.captionstudio.data.local.ProjectRepository
import com.example.captionstudio.data.preferences.AsrEnginePreference
import com.example.captionstudio.data.preferences.SettingsRepository
import com.example.captionstudio.domain.model.AnimationDefaults
import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.ElementStylePatch
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.ExportPreferences
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.FontRoleOverrides
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.HookPacingTemplate
import com.example.captionstudio.domain.model.KaraokeHighlightMode
import com.example.captionstudio.domain.model.KeepSegment
import com.example.captionstudio.domain.model.LayerMotionEffect
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.PreviewQualityMode
import com.example.captionstudio.domain.model.ProjectSortOption
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.SafeZonePlatform
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextAlignment
import com.example.captionstudio.domain.model.VideoMetadata
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.engine.CompositionValidator
import com.example.captionstudio.engine.LocalCompositionEngine
import com.example.captionstudio.media.AudioExtractionService
import com.example.captionstudio.media.AutoTranscribeResult
import com.example.captionstudio.media.AutoTranscribeUiState
import com.example.captionstudio.media.ExportUiState
import com.example.captionstudio.media.OfflineVoicePackInfo
import com.example.captionstudio.media.OfflineVoicePackStatus
import com.example.captionstudio.media.OfflineVoskModelManager
import com.example.captionstudio.media.SpeechRecognitionState
import com.example.captionstudio.media.SpeechTranscriptionManager
import com.example.captionstudio.media.VideoAnalysisService
import com.example.captionstudio.media.VideoAutoTranscriber
import com.example.captionstudio.media.VideoExportCoordinator
import com.example.captionstudio.media.VoskTextSegment
import com.example.captionstudio.media.VoskVideoTranscriptionService
import com.example.captionstudio.media.isUnder4GbRamDevice
import com.example.captionstudio.media.resolveOfflineAsrEngine
import com.example.captionstudio.presets.PresetCatalog
import com.example.captionstudio.presets.StyleDeepLinkCodec
import com.example.captionstudio.typography.CuratedFontPairing
import com.example.captionstudio.typography.FontRegistry
import com.example.captionstudio.ui.components.clearVideoThumbnailCache
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BackgroundTaskHudState(
  val isRunning: Boolean = false,
  val stepLabel: String = "",
  val progressFraction: Float? = null,
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

  private val db = AppDatabase.getInstance(application)
  private val projectRepo = ProjectRepository(db.projectDao())
  private val settingsRepo = SettingsRepository(application)
  private val speechManager = SpeechTranscriptionManager(application)
  private val autoTranscriber = VideoAutoTranscriber(application)
  private val persistMutex = Mutex()

  init {
    FontRegistry.loadCachedCustomFonts(application)
    viewModelScope.launch {
      settingsRepo.customPresets.collect { list ->
        PresetCatalog.registerCustomPresets(list)
      }
    }
  }

  private val _gallerySearchQuery = MutableStateFlow("")
  val gallerySearchQuery: StateFlow<String> = _gallerySearchQuery.asStateFlow()

  private val _gallerySortOption = MutableStateFlow(ProjectSortOption.DATE_CREATED)
  val gallerySortOption: StateFlow<ProjectSortOption> = _gallerySortOption.asStateFlow()

  private val _isRefreshingProjects = MutableStateFlow(false)
  val isRefreshingProjects: StateFlow<Boolean> = _isRefreshingProjects.asStateFlow()

  private val _thumbnailRefreshNonce = MutableStateFlow(0)
  val thumbnailRefreshNonce: StateFlow<Int> = _thumbnailRefreshNonce.asStateFlow()

  private val _refreshedSnapshotOverride = MutableStateFlow<List<CaptionProject>?>(null)

  val projects: StateFlow<List<CaptionProject>> =
    combine(projectRepo.allProjects, _refreshedSnapshotOverride) { live, snapshot ->
        if (live.isNotEmpty() || snapshot == null) live else snapshot
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val filteredGalleryProjects: StateFlow<List<CaptionProject>> =
    combine(projects, _gallerySearchQuery, _gallerySortOption) { all, query, sortOption ->
        val trimmed = query.trim()
        val matched =
          if (trimmed.isEmpty()) {
            all
          } else {
            all.filter { it.title.contains(trimmed, ignoreCase = true) }
          }
        when (sortOption) {
          ProjectSortOption.DATE_CREATED ->
            matched.sortedWith(
              compareByDescending<CaptionProject> { it.createdAtMs }
                .thenByDescending { it.updatedAtMs }
            )
          ProjectSortOption.NAME ->
            matched.sortedWith(
              compareBy<CaptionProject> { it.title.lowercase(Locale.getDefault()) }
                .thenByDescending { it.createdAtMs }
            )
          ProjectSortOption.DURATION ->
            matched.sortedWith(
              compareByDescending<CaptionProject> { it.metadata.durationMs }
                .thenByDescending { it.createdAtMs }
            )
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val previewQualityMode: StateFlow<PreviewQualityMode> =
    settingsRepo.previewQualityMode.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      PreviewQualityMode.HIGH_QUALITY,
    )

  val speechLanguageTag: StateFlow<String> =
    settingsRepo.speechLanguageTag.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      "en-US",
    )

  val isUnder4GbRam: Boolean = isUnder4GbRamDevice(application)

  val asrEnginePreference: StateFlow<AsrEnginePreference> =
    settingsRepo.asrEnginePreference.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      AsrEnginePreference.AUTO,
    )

  val speechState: StateFlow<SpeechRecognitionState> = speechManager.state
  val autoTranscribeState: StateFlow<AutoTranscribeUiState> = autoTranscriber.state
  val exportState: StateFlow<ExportUiState> = VideoExportCoordinator.state(application)

  val customPresets: StateFlow<List<PresetDefinition>> =
    settingsRepo.customPresets.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val isCreatorMode: StateFlow<Boolean> =
    settingsRepo.isCreatorMode.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      true,
    )

  val isProUnlocked: StateFlow<Boolean> =
    settingsRepo.isProUnlocked.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      false,
    )

  private val _activeProject = MutableStateFlow<CaptionProject?>(null)
  val activeProject: StateFlow<CaptionProject?> = _activeProject.asStateFlow()

  private val _selectedCompositionId = MutableStateFlow<String?>(null)
  val selectedCompositionId: StateFlow<String?> = _selectedCompositionId.asStateFlow()

  private val _selectedElementId = MutableStateFlow<String?>(null)
  val selectedElementId: StateFlow<String?> = _selectedElementId.asStateFlow()

  private val _playheadMs = MutableStateFlow(0L)
  val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _backgroundTaskState = MutableStateFlow(BackgroundTaskHudState())
  val backgroundTaskState: StateFlow<BackgroundTaskHudState> = _backgroundTaskState.asStateFlow()

  private var activeBackgroundJob: Job? = null
  private var activeTranscribeJob: Job? = null

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _showSafeGuides = MutableStateFlow(false)
  val showSafeGuides: StateFlow<Boolean> = _showSafeGuides.asStateFlow()

  private val _safeZonePlatform = MutableStateFlow(SafeZonePlatform.NONE)
  val safeZonePlatform: StateFlow<SafeZonePlatform> = _safeZonePlatform.asStateFlow()

  private val _voicePackStatuses = MutableStateFlow<List<OfflineVoicePackStatus>>(emptyList())
  val voicePackStatuses: StateFlow<List<OfflineVoicePackStatus>> = _voicePackStatuses.asStateFlow()

  private val _transcribedSegments = MutableStateFlow<List<VoskTextSegment>>(emptyList())
  val transcribedSegments: StateFlow<List<VoskTextSegment>> = _transcribedSegments.asStateFlow()

  private val _fontsVersion = MutableStateFlow(0)
  val fontsVersion: StateFlow<Int> = _fontsVersion.asStateFlow()

  private val _audioWaveformBins = MutableStateFlow<List<Float>>(emptyList())
  val audioWaveformBins: StateFlow<List<Float>> = _audioWaveformBins.asStateFlow()

  private var audioFeatureHydrateJob: Job? = null

  init {
    refreshVoicePackStatuses()
  }

  // Undo / Redo history stacks
  private val undoStack = ArrayDeque<CaptionProject>()
  private val redoStack = ArrayDeque<CaptionProject>()
  private var pendingPinchSnapshot: CaptionProject? = null
  private val _canUndo = MutableStateFlow(false)
  val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
  private val _canRedo = MutableStateFlow(false)
  val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun toggleSafeGuides() {
    cycleSafeZonePlatform()
  }

  fun cycleSafeZonePlatform() {
    val entries = SafeZonePlatform.entries
    val next = entries[(entries.indexOf(_safeZonePlatform.value) + 1) % entries.size]
    setSafeZonePlatform(next)
  }

  fun setSafeZonePlatform(platform: SafeZonePlatform) {
    _safeZonePlatform.value = platform
    _showSafeGuides.value = platform != SafeZonePlatform.NONE
  }

  fun setCreatorMode(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setCreatorMode(enabled) }
  }

  fun setProUnlocked(unlocked: Boolean) {
    viewModelScope.launch { settingsRepo.setProUnlocked(unlocked) }
    val current = _activeProject.value
    if (!unlocked && current != null && !current.exportPreferences.showWatermark) {
      commitMutation(
        current.copy(exportPreferences = current.exportPreferences.copy(showWatermark = true)),
        recordHistory = false,
      )
    }
  }

  fun updateGallerySearchQuery(query: String) {
    _gallerySearchQuery.value = query
  }

  fun updateGallerySortOption(option: ProjectSortOption) {
    _gallerySortOption.value = option
  }

  fun refreshProjects() {
    if (_isRefreshingProjects.value) return
    viewModelScope.launch {
      _isRefreshingProjects.value = true
      val startedAt = System.currentTimeMillis()
      try {
        val ctx = getApplication<Application>()
        val latestSnapshot = projectRepo.getAllProjectsSnapshot()
        latestSnapshot.forEach { proj ->
          if (proj.videoUriString.isNotBlank() && proj.metadata.durationMs <= 0L) {
            runCatching {
              val refreshedMeta =
                VideoAnalysisService.extractMetadata(ctx, Uri.parse(proj.videoUriString))
              if (refreshedMeta.durationMs > 0L) {
                projectRepo.saveProject(proj.copy(metadata = refreshedMeta))
              }
            }
          }
        }
        val synced = projectRepo.getAllProjectsSnapshot()
        _refreshedSnapshotOverride.value = synced
        clearVideoThumbnailCache()
        _thumbnailRefreshNonce.value += 1
        refreshVoicePackStatuses()
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < 380L) {
          delay(380L - elapsed)
        }
      } finally {
        _isRefreshingProjects.value = false
      }
    }
  }

  fun openProject(projectId: String) {
    val cached = projects.value.firstOrNull { it.id == projectId }
    if (cached != null) {
      val safeCached = ProjectRepository.sanitizeProject(cached)
      _activeProject.value = safeCached
      _transcribedSegments.value =
        if (safeCached.wordTimings.isNotEmpty()) {
          runCatching { VoskVideoTranscriptionService.segmentRecognizedWords(safeCached.wordTimings) }
            .getOrDefault(emptyList())
        } else {
          emptyList()
        }
      _playheadMs.value = 0L
      val firstComp = safeCached.compositions.firstOrNull()
      _selectedCompositionId.value = firstComp?.id
      _selectedElementId.value = firstComp?.elements?.firstOrNull()?.id
    }
    viewModelScope.launch {
      val loaded =
        try {
          projectRepo.getProject(projectId)?.let { ProjectRepository.sanitizeProject(it) }
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          cached?.let { ProjectRepository.sanitizeProject(it) }
        }
      undoStack.clear()
      redoStack.clear()
      refreshUndoFlags()
      _activeProject.value = loaded
      _transcribedSegments.value =
        if (loaded != null && loaded.wordTimings.isNotEmpty()) {
          runCatching { VoskVideoTranscriptionService.segmentRecognizedWords(loaded.wordTimings) }
            .getOrDefault(emptyList())
        } else {
          emptyList()
        }
      _playheadMs.value = 0L
      val firstComp = loaded?.compositions?.firstOrNull()
      _selectedCompositionId.value = firstComp?.id
      _selectedElementId.value = firstComp?.elements?.firstOrNull()?.id
      if (loaded != null) {
        refreshAudioWaveformAndBeats(loaded)
      } else {
        _audioWaveformBins.value = emptyList()
      }
    }
  }

  private fun refreshAudioWaveformAndBeats(project: CaptionProject) {
    val uriStr = project.videoUriString
    if (uriStr.isBlank() || !project.metadata.hasAudio) {
      _audioWaveformBins.value = emptyList()
      return
    }
    val keepSegments = project.analysisReport.keepSegments
    val cachedRaw = AudioExtractionService.getCachedRawEnvelope(uriStr)
    val cachedBins = AudioExtractionService.getCachedWaveformBins(uriStr)
    if (cachedRaw != null) {
      _audioWaveformBins.value =
        if (keepSegments.isNotEmpty()) {
          AudioExtractionService.resampleWaveformForKeepSegments(cachedRaw, keepSegments)
        } else {
          cachedBins ?: AudioExtractionService.resampleWaveformForKeepSegments(cachedRaw, emptyList())
        }
    } else if (cachedBins != null && keepSegments.isEmpty()) {
      _audioWaveformBins.value = cachedBins
    }

    val needsBeats = project.analysisReport.beatTimestampsMs.isEmpty()
    val needsEnvelope = cachedRaw == null
    if (!needsBeats && !needsEnvelope) return

    audioFeatureHydrateJob?.cancel()
    val targetProjectId = project.id
    audioFeatureHydrateJob =
      viewModelScope.launch {
        val track =
          withContext(Dispatchers.IO) {
            runCatching {
              AudioExtractionService.extractAudioTrack(getApplication(), Uri.parse(uriStr))
            }.getOrNull()
          } ?: return@launch
        val latest = _activeProject.value ?: return@launch
        if (latest.id != targetProjectId) return@launch
        val latestKeep = latest.analysisReport.keepSegments
        _audioWaveformBins.value =
          if (latestKeep.isNotEmpty()) {
            AudioExtractionService.resampleWaveformForKeepSegments(track.energyEnvelope10ms, latestKeep)
          } else {
            track.waveformBins
          }
        if (latest.analysisReport.beatTimestampsMs.isEmpty() && track.beatTimestampsMs.isNotEmpty()) {
          commitMutation(
            latest.copy(
              analysisReport = latest.analysisReport.copy(beatTimestampsMs = track.beatTimestampsMs)
            ),
            recordHistory = false,
          )
        }
      }
  }

  suspend fun importVideoAndCreateProject(uri: Uri, title: String): String {
    _isBusy.value = true
    _backgroundTaskState.value =
      BackgroundTaskHudState(
        isRunning = true,
        stepLabel = "Analyzing scene & framing...",
        progressFraction = 0.25f,
      )
    return try {
      val ctx = getApplication<Application>()
      val meta = VideoAnalysisService.extractMetadata(ctx, uri)
      _backgroundTaskState.value =
        BackgroundTaskHudState(
          isRunning = true,
          stepLabel = "Detecting subjects & safe zones...",
          progressFraction = 0.68f,
        )
      val analysis =
        try {
          VideoAnalysisService.analyzeLocally(ctx, uri, meta)
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          com.example.captionstudio.domain.model.VideoAnalysisReport(
            summary = "Scene framing fallback applied for low memory."
          )
        }
      val created =
        projectRepo.createProject(
          title = title,
          videoUriString = uri.toString(),
          metadata = meta,
          presetId = PresetCatalog.DEFAULT_PRESET_ID,
          analysisReport = analysis,
        )
      _activeProject.value = created
      undoStack.clear()
      redoStack.clear()
      refreshUndoFlags()
      _playheadMs.value = 0L
      _statusBanner.value = analysis.summary
      if (meta.hasAudio) {
        activeTranscribeJob?.cancel()
        activeTranscribeJob =
          viewModelScope.launch {
            runAutoTranscribeInternal(autoCompose = true)
          }
      }
      created.id
    } finally {
      _isBusy.value = false
      _backgroundTaskState.value = BackgroundTaskHudState()
    }
  }

  suspend fun createBlankProject(title: String, loadReferenceShowcase: Boolean): String {
    val preset = PresetCatalog.findById(PresetCatalog.DEFAULT_PRESET_ID)
    val meta = VideoMetadata(durationMs = 12000L, width = 1080, height = 1920, hasAudio = false)
    val transcript =
      if (loadReferenceShowcase) {
        "Why do many creators add. Especially text like this. No, it's not just because of it. But here's the big problem. Link in bio."
      } else {
        ""
      }
    val timings =
      if (transcript.isNotEmpty()) {
        LocalCompositionEngine.buildProvisionalWordTimings(transcript, meta.durationMs)
      } else {
        emptyList()
      }
    val comps =
      if (transcript.isNotEmpty()) {
        val raw =
          LocalCompositionEngine.generateCompositions(
            transcript = transcript,
            wordTimings = timings,
            totalDurationMs = meta.durationMs,
            preset = preset,
            analysisReport = com.example.captionstudio.domain.model.VideoAnalysisReport(),
          )
        CompositionValidator.validateAndRepair(getApplication(), raw, meta.width, meta.height)
      } else {
        emptyList()
      }

    val created =
      projectRepo.createProject(
        title = title,
        videoUriString = "",
        metadata = meta,
        presetId = preset.id,
        initialTranscript = transcript,
        initialWordTimings = timings,
        initialCompositions = comps,
      )
    _activeProject.value = created
    _transcribedSegments.value =
      if (timings.isNotEmpty()) VoskVideoTranscriptionService.segmentRecognizedWords(timings)
      else emptyList()
    undoStack.clear()
    redoStack.clear()
    refreshUndoFlags()
    _selectedCompositionId.value = comps.firstOrNull()?.id
    _selectedElementId.value = comps.firstOrNull()?.elements?.firstOrNull()?.id
    _playheadMs.value = comps.firstOrNull()?.startMs ?: 0L
    return created.id
  }

  fun attachVideoToActiveProject(uri: Uri) {
    val current = _activeProject.value ?: return
    activeBackgroundJob?.cancel()
    activeBackgroundJob =
      viewModelScope.launch {
        _isBusy.value = true
        _backgroundTaskState.value =
          BackgroundTaskHudState(
            isRunning = true,
            stepLabel = "Analyzing scene & framing...",
            progressFraction = 0.25f,
          )
        try {
          val ctx = getApplication<Application>()
          val meta = VideoAnalysisService.extractMetadata(ctx, uri)
          _backgroundTaskState.value =
            BackgroundTaskHudState(
              isRunning = true,
              stepLabel = "Detecting subjects & safe zones...",
              progressFraction = 0.65f,
            )
          val analysis =
            try {
              VideoAnalysisService.analyzeLocally(ctx, uri, meta)
            } catch (ce: CancellationException) {
              throw ce
            } catch (_: Throwable) {
              current.analysisReport
            }
          val updated =
            current.copy(
              videoUriString = uri.toString(),
              metadata = meta,
              analysisReport = analysis,
            )
          commitMutation(updated)
          _statusBanner.value = analysis.summary
          _backgroundTaskState.value = BackgroundTaskHudState()
          if (meta.hasAudio && current.transcriptText.isBlank()) {
            runAutoTranscribeInternal(autoCompose = true)
          } else {
            refreshAudioWaveformAndBeats(updated)
          }
        } catch (_: CancellationException) {
          _statusBanner.value = "Scene analysis cancelled."
        } finally {
          _isBusy.value = false
          _backgroundTaskState.value = BackgroundTaskHudState()
        }
      }
  }

  fun renameProject(projectId: String, newTitle: String) {
    viewModelScope.launch {
      projectRepo.renameProject(projectId, newTitle)
      val current = _activeProject.value
      if (current != null && current.id == projectId) {
        _activeProject.value = current.copy(title = newTitle.trim())
      }
    }
  }

  fun deleteProject(projectId: String) {
    viewModelScope.launch {
      projectRepo.deleteProject(projectId)
      if (_activeProject.value?.id == projectId) {
        _activeProject.value = null
      }
    }
  }

  fun updatePlayhead(positionMs: Long) {
    _playheadMs.value = positionMs.coerceAtLeast(0L)
  }

  fun selectComposition(compId: String) {
    _selectedCompositionId.value = compId
    val comp = _activeProject.value?.compositions?.firstOrNull { it.id == compId }
    if (comp != null) {
      _selectedElementId.value = comp.elements.firstOrNull()?.id
    }
  }

  fun selectElement(compId: String, elementId: String) {
    _selectedCompositionId.value = compId
    _selectedElementId.value = elementId
  }

  fun undo() {
    val current = _activeProject.value ?: return
    if (undoStack.isEmpty()) return
    redoStack.addLast(current)
    val restored = undoStack.removeLast()
    _activeProject.value = restored
    _transcribedSegments.value = VoskVideoTranscriptionService.segmentRecognizedWords(restored.wordTimings)
    refreshAudioWaveformAndBeats(restored)
    refreshUndoFlags()
    persistValidatedProjectAsync(restored)
  }

  fun redo() {
    val current = _activeProject.value ?: return
    if (redoStack.isEmpty()) return
    undoStack.addLast(current)
    val restored = redoStack.removeLast()
    _activeProject.value = restored
    _transcribedSegments.value = VoskVideoTranscriptionService.segmentRecognizedWords(restored.wordTimings)
    refreshAudioWaveformAndBeats(restored)
    refreshUndoFlags()
    persistValidatedProjectAsync(restored)
  }

  private fun commitMutation(updated: CaptionProject, recordHistory: Boolean = true) {
    val sanitized = ProjectRepository.sanitizeProject(updated)
    val current = _activeProject.value
    if (recordHistory && current != null && current != sanitized) {
      undoStack.addLast(current)
      while (undoStack.size > 30) undoStack.removeFirst()
      redoStack.clear()
      refreshUndoFlags()
    }
    _activeProject.value = sanitized
    persistValidatedProjectAsync(sanitized)
  }

  /**
   * Validates compositions cleanly through [CompositionValidator.validateAndRepair] before
   * persisting updated `compositionsJson` and `analysisReportJson` to Room. Uses a mutex so
   * concurrent edits never write out-of-order or partially validated states.
   */
  private fun persistValidatedProjectAsync(project: CaptionProject) {
    viewModelScope.launch(Dispatchers.IO) {
      persistMutex.withLock {
        val validatedProject = validateForPersistenceOrNull(project) ?: return@withLock
        projectRepo.saveProject(validatedProject)
      }
    }
  }

  private fun validateForPersistenceOrNull(project: CaptionProject): CaptionProject? {
    return try {
      val sanitized = ProjectRepository.sanitizeProject(project)
      val validatedComps =
        if (sanitized.compositions.isNotEmpty()) {
          CompositionValidator.validateAndRepair(
            context = getApplication(),
            compositions = sanitized.compositions,
            videoWidthPx = sanitized.metadata.effectiveWidth.coerceAtLeast(360),
            videoHeightPx = sanitized.metadata.effectiveHeight.coerceAtLeast(360),
          )
        } else {
          emptyList()
        }
      sanitized.copy(compositions = validatedComps)
    } catch (ce: CancellationException) {
      throw ce
    } catch (_: Throwable) {
      null
    }
  }

  private fun refreshUndoFlags() {
    _canUndo.value = undoStack.isNotEmpty()
    _canRedo.value = redoStack.isNotEmpty()
  }

  // --- Transcript & Speech ---

  fun updateTranscriptText(newTranscript: String) {
    val project = _activeProject.value ?: return
    val timings =
      LocalCompositionEngine.buildProvisionalWordTimings(
        newTranscript,
        max(project.metadata.durationMs, 4000L),
      )
    commitMutation(project.copy(transcriptText = newTranscript, wordTimings = timings))
    _transcribedSegments.value = VoskVideoTranscriptionService.segmentRecognizedWords(timings)
  }

  fun importSubtitles(rawSrtOrVtt: String) {
    val project = _activeProject.value ?: return
    val (parsedText, parsedTimings) = SpeechTranscriptionManager.parseSubtitles(rawSrtOrVtt)
    if (parsedText.isBlank()) {
      _statusBanner.value = "Could not find valid SRT or WebVTT cues in the selected file."
      return
    }
    commitMutation(project.copy(transcriptText = parsedText, wordTimings = parsedTimings))
    _transcribedSegments.value = VoskVideoTranscriptionService.segmentRecognizedWords(parsedTimings)
    _statusBanner.value = "Imported ${parsedTimings.size} timed words from subtitle file."
  }

  fun startSpeechDictation() {
    val project = _activeProject.value ?: return
    val baseOffset = project.wordTimings.lastOrNull()?.endMs ?: 0L
    speechManager.startListening(
      languageTag = speechLanguageTag.value,
      baseOffsetMs = baseOffset,
    ) { dictatedText, timings ->
      val latest = _activeProject.value ?: return@startListening
      val combinedTranscript =
        if (latest.transcriptText.isBlank()) dictatedText
        else "${latest.transcriptText.trim()} $dictatedText"
      val combinedTimings = latest.wordTimings + timings
      commitMutation(
        latest.copy(transcriptText = combinedTranscript, wordTimings = combinedTimings)
      )
      _transcribedSegments.value = VoskVideoTranscriptionService.segmentRecognizedWords(combinedTimings)
      _statusBanner.value = "Recognized ${timings.size} spoken words (provisional timings ready)."
    }
  }

  fun stopSpeechDictation() {
    speechManager.stopListening()
  }

  fun adjustWordTiming(index: Int, newStartMs: Long, newEndMs: Long) {
    val project = _activeProject.value ?: return
    if (index !in project.wordTimings.indices) return
    val updated =
      project.wordTimings.mapIndexed { idx, wt ->
        if (idx == index) {
          wt.copy(
            startMs = newStartMs.coerceAtLeast(0L),
            endMs = max(newStartMs + 100L, newEndMs),
            isEstimated = false,
          )
        } else wt
      }
    commitMutation(project.copy(wordTimings = updated))
    _transcribedSegments.value = VoskVideoTranscriptionService.segmentRecognizedWords(updated)
  }

  fun updateTranscribedSegmentText(segmentId: String, newText: String) {
    val project = _activeProject.value ?: return
    val currentSegments = _transcribedSegments.value
    val segIndex = currentSegments.indexOfFirst { it.id == segmentId }
    if (segIndex < 0) return

    val target = currentSegments[segIndex]
    val tokens = newText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val updatedSegments = currentSegments.toMutableList()
    if (tokens.isEmpty()) {
      updatedSegments.removeAt(segIndex)
    } else {
      val dur = max(180L, target.endMs - target.startMs)
      val slice = dur / tokens.size
      val newWords =
        tokens.mapIndexed { idx, w ->
          val ws = target.startMs + slice * idx
          val we = if (idx == tokens.lastIndex) target.endMs else max(ws + 80L, ws + slice)
          WordTiming(
            word = w,
            startMs = ws,
            endMs = we,
            isEstimated = false,
            emphasisScore = LocalCompositionEngine.scoreWordSalience(w, idx, tokens.size),
          )
        }
      updatedSegments[segIndex] =
        target.copy(
          text = tokens.joinToString(" "),
          words = newWords,
        )
    }

    val rebuiltTimings = updatedSegments.flatMap { it.words }
    val rebuiltTranscript = updatedSegments.joinToString(" ") { it.text }.trim()
    val preset = PresetCatalog.findById(project.presetId)
    val rawComps =
      LocalCompositionEngine.generateCompositionsFromPhrases(
        phrases = updatedSegments.map { it.words },
        preset = preset,
        analysisReport = project.analysisReport,
        fontOverrides = project.fontOverrides,
      )
    val validated =
      CompositionValidator.validateAndRepair(
        context = getApplication(),
        compositions = rawComps,
        videoWidthPx = project.metadata.effectiveWidth,
        videoHeightPx = project.metadata.effectiveHeight,
      )

    _transcribedSegments.value = updatedSegments.mapIndexed { idx, s -> s.copy(index = idx) }
    commitMutation(
      project.copy(
        transcriptText = rebuiltTranscript,
        wordTimings = rebuiltTimings,
        compositions = validated,
      )
    )
    _statusBanner.value = "Updated text segment & synced ${validated.size} editor captions."
  }

  fun cancelAutoTranscribe() {
    activeTranscribeJob?.cancel()
    activeTranscribeJob = null
    autoTranscriber.cancel()
    _backgroundTaskState.value = BackgroundTaskHudState()
    _isBusy.value = false
    _statusBanner.value = "Transcription cancelled."
  }

  fun cancelBackgroundTask() {
    activeBackgroundJob?.cancel()
    activeBackgroundJob = null
    activeTranscribeJob?.cancel()
    activeTranscribeJob = null
    autoTranscriber.cancel()
    _backgroundTaskState.value = BackgroundTaskHudState()
    _isBusy.value = false
    _statusBanner.value = "Task cancelled."
  }

  // --- Composition Generation ---

  fun requestGenerateCompositions() {
    val project = _activeProject.value ?: return
    if (project.transcriptText.isBlank()) {
      _statusBanner.value = "Enter, dictate, or import a transcript before generating captions."
      return
    }
    executeCompositionGeneration()
  }

  fun requestAutoTranscribeVideo() {
    val project = _activeProject.value ?: return
    if (project.videoUriString.isBlank()) {
      _statusBanner.value = "Attach a video before running automatic audio transcription."
      return
    }
    activeTranscribeJob?.cancel()
    activeTranscribeJob =
      viewModelScope.launch {
        try {
          runAutoTranscribeInternal(autoCompose = true)
        } catch (_: CancellationException) {
          autoTranscriber.cancel()
          _backgroundTaskState.value = BackgroundTaskHudState()
        }
      }
  }

  private suspend fun runAutoTranscribeInternal(autoCompose: Boolean) {
    val project = _activeProject.value ?: return
    if (project.videoUriString.isBlank()) return
    autoTranscriber.clearError()

    val result =
      autoTranscriber.transcribeVideo(
        videoUri = Uri.parse(project.videoUriString),
        languageTag = speechLanguageTag.value,
        useOnlineGemini = false,
        geminiApiKey = null,
        geminiModelName = "",
        asrEnginePreference = asrEnginePreference.value,
      )

    val latest = _activeProject.value ?: return
    when (result) {
      is AutoTranscribeResult.Success -> {
        val segments =
          result.segments.ifEmpty {
            VoskVideoTranscriptionService.segmentRecognizedWords(result.wordTimings)
          }
        _transcribedSegments.value = segments

        val targetSampleTimes =
          if (segments.isNotEmpty()) {
            segments.map { (it.startMs + it.endMs) / 2L }
          } else {
            result.wordTimings.chunked(4).map { chunk ->
              (chunk.first().startMs + chunk.last().endMs) / 2L
            }
          }

        val enrichedAnalysis =
          if (latest.videoUriString.isNotBlank() &&
              (latest.analysisReport.shotAnalyses.isEmpty() || targetSampleTimes.isNotEmpty())
          ) {
            _backgroundTaskState.value =
              BackgroundTaskHudState(
                isRunning = true,
                stepLabel = "Analyzing scene & aligning 10ms timings...",
                progressFraction = 0.86f,
              )
            try {
              VideoAnalysisService.analyzeLocally(
                context = getApplication(),
                videoUri = Uri.parse(latest.videoUriString),
                metadata = latest.metadata,
                targetTimestampsMs = targetSampleTimes,
              )
            } catch (ce: CancellationException) {
              throw ce
            } catch (_: Throwable) {
              latest.analysisReport
            } finally {
              _backgroundTaskState.value = BackgroundTaskHudState()
            }
          } else {
            latest.analysisReport
          }

        val cachedBeats =
          AudioExtractionService.getCachedBeatTimestamps(latest.videoUriString)
            ?.takeIf { it.isNotEmpty() }
            ?: latest.analysisReport.beatTimestampsMs
        val finalAnalysis =
          enrichedAnalysis.copy(
            beatTimestampsMs = cachedBeats,
            keepSegments = latest.analysisReport.keepSegments,
            originalCompositionsBackup = latest.analysisReport.originalCompositionsBackup,
            originalWordTimingsBackup = latest.analysisReport.originalWordTimingsBackup,
          )

        val preset = PresetCatalog.findById(latest.presetId)
        val comps =
          if (autoCompose) {
            val raw =
              if (segments.isNotEmpty()) {
                LocalCompositionEngine.generateCompositionsFromPhrases(
                  phrases = segments.map { it.words },
                  preset = preset,
                  analysisReport = finalAnalysis,
                  fontOverrides = latest.fontOverrides,
                )
              } else {
                LocalCompositionEngine.generateCompositions(
                  transcript = result.transcript,
                  wordTimings = result.wordTimings,
                  totalDurationMs =
                    max(latest.metadata.durationMs, result.wordTimings.lastOrNull()?.endMs ?: 4000L),
                  preset = preset,
                  analysisReport = finalAnalysis,
                  fontOverrides = latest.fontOverrides,
                )
              }
            CompositionValidator.validateAndRepair(
              context = getApplication(),
              compositions = raw,
              videoWidthPx = latest.metadata.effectiveWidth,
              videoHeightPx = latest.metadata.effectiveHeight,
            )
          } else {
            latest.compositions
          }

        val updatedProject =
          latest.copy(
            transcriptText = result.transcript,
            wordTimings = result.wordTimings,
            analysisReport = finalAnalysis,
            compositions = comps,
          )
        commitMutation(updatedProject)
        refreshAudioWaveformAndBeats(updatedProject)
        _selectedCompositionId.value = comps.firstOrNull()?.id
        _selectedElementId.value = comps.firstOrNull()?.elements?.firstOrNull()?.id
        _playheadMs.value = comps.firstOrNull()?.startMs ?: 0L
        _statusBanner.value =
          "${result.engineLabel}: ${segments.size} segments & ${comps.size} frame-aware captions ready."
      }
      is AutoTranscribeResult.NoSpeechDetected -> {
        _statusBanner.value = result.message
      }
      is AutoTranscribeResult.Unavailable -> {
        _statusBanner.value = result.reason
      }
    }
  }

  private fun executeCompositionGeneration() {
    val project = _activeProject.value ?: return
    val preset = PresetCatalog.findById(project.presetId)

    activeBackgroundJob?.cancel()
    activeBackgroundJob =
      viewModelScope.launch {
        _isBusy.value = true
        _backgroundTaskState.value =
          BackgroundTaskHudState(
            isRunning = true,
            stepLabel = "Analyzing scene & safe zones...",
            progressFraction = 0.30f,
          )
        try {
          val targetTimes =
            if (project.wordTimings.isNotEmpty()) {
              project.wordTimings.chunked(4).map { chunk ->
                (chunk.first().startMs + chunk.last().endMs) / 2L
              }
            } else {
              emptyList()
            }
          val effectiveAnalysis =
            if (project.videoUriString.isNotBlank() && project.analysisReport.shotAnalyses.isEmpty()) {
              try {
                VideoAnalysisService.analyzeLocally(
                  context = getApplication(),
                  videoUri = Uri.parse(project.videoUriString),
                  metadata = project.metadata,
                  targetTimestampsMs = targetTimes,
                )
              } catch (ce: CancellationException) {
                throw ce
              } catch (_: Throwable) {
                project.analysisReport
              }
            } else {
              project.analysisReport
            }

          _backgroundTaskState.value =
            BackgroundTaskHudState(
              isRunning = true,
              stepLabel = "Aligning 10ms timings...",
              progressFraction = 0.72f,
            )

          val localComps =
            LocalCompositionEngine.generateCompositions(
              transcript = project.transcriptText,
              wordTimings = project.wordTimings,
              totalDurationMs = project.metadata.durationMs,
              preset = preset,
              analysisReport = effectiveAnalysis,
              fontOverrides = project.fontOverrides,
            )

          val bannerMsg =
            if (effectiveAnalysis.shotAnalyses.isNotEmpty()) {
              "Generated ${localComps.size} frame-aware captions across ${effectiveAnalysis.shotAnalyses.size} shot(s) offline."
            } else {
              "Generated ${localComps.size} kinetic captions offline."
            }

          val validated =
            CompositionValidator.validateAndRepair(
              context = getApplication(),
              compositions = localComps,
              videoWidthPx = project.metadata.effectiveWidth,
              videoHeightPx = project.metadata.effectiveHeight,
            )

          commitMutation(
            project.copy(
              analysisReport = effectiveAnalysis,
              compositions = validated,
            )
          )
          _selectedCompositionId.value = validated.firstOrNull()?.id
          _selectedElementId.value = validated.firstOrNull()?.elements?.firstOrNull()?.id
          _playheadMs.value = validated.firstOrNull()?.startMs ?: 0L
          _statusBanner.value = bannerMsg
        } catch (_: CancellationException) {
          _statusBanner.value = "Caption generation cancelled."
        } finally {
          _isBusy.value = false
          _backgroundTaskState.value = BackgroundTaskHudState()
        }
      }
  }

  // --- Manual Editing ---

  fun updateCompositionTiming(compId: String, newStartMs: Long, newEndMs: Long) {
    val project = _activeProject.value ?: return
    val updated =
      project.compositions.map { comp ->
        if (comp.id == compId) {
          val safeStart = newStartMs.coerceAtLeast(0L)
          comp.copy(startMs = safeStart, endMs = max(safeStart + 350L, newEndMs))
        } else comp
      }
    commitMutation(project.copy(compositions = updated))
  }

  fun updateCompositionPosition(
    compId: String,
    anchorX: Float,
    anchorY: Float,
    region: SafeRegion = SafeRegion.CUSTOM,
    recordHistory: Boolean = true,
  ) {
    val project = _activeProject.value ?: return
    val updated =
      project.compositions.map { comp ->
        if (comp.id == compId) {
          comp.copy(
            region = region,
            anchorXFraction = anchorX.coerceIn(0.16f, 0.84f),
            anchorYFraction = anchorY.coerceIn(0.10f, 0.90f),
          )
        } else comp
      }
    commitMutation(project.copy(compositions = updated), recordHistory = recordHistory)
  }

  fun toggleCompositionDepthMask(compId: String) {
    val project = _activeProject.value ?: return
    if (project.compositions.isEmpty()) {
      _statusBanner.value = "Transcribe or add captions first to enable behind-subject depth."
      return
    }
    var enabledResult = false
    val updated =
      project.compositions.map { comp ->
        if (comp.id != compId) {
          comp
        } else {
          val nextEnable = !comp.hasBehindSubjectElements()
          enabledResult = nextEnable
          val nextLayer = if (nextEnable) DepthLayer.BEHIND_SUBJECT else DepthLayer.IN_FRONT
          val hasDominant = comp.elements.any { it.hierarchyLevel == HierarchyLevel.DOMINANT }
          val updatedEls =
            comp.elements.mapIndexed { idx, el ->
              if (el.hierarchyLevel == HierarchyLevel.DOMINANT || (!hasDominant && idx == 0)) {
                el.copy(depthLayer = nextLayer)
              } else {
                el
              }
            }
          comp.copy(
            depthLayer = nextLayer,
            backgroundMaskEnabled = nextEnable,
            elements = updatedEls,
          )
        }
      }
    commitMutation(project.copy(compositions = updated))
    _statusBanner.value =
      if (enabledResult) "Behind-subject depth ON for active caption."
      else "Behind-subject depth OFF."
  }

  fun splitComposition(compId: String) {
    val project = _activeProject.value ?: return
    val idx = project.compositions.indexOfFirst { it.id == compId }
    if (idx < 0) return
    val comp = project.compositions[idx]
    val words = comp.originalText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size < 2) {
      _statusBanner.value = "Need at least 2 words to split this composition."
      return
    }
    val midWord = words.size / 2
    val firstText = words.subList(0, midWord).joinToString(" ")
    val secondText = words.subList(midWord, words.size).joinToString(" ")
    val midTime = (comp.startMs + comp.endMs) / 2L

    val preset = PresetCatalog.findById(project.presetId)
    val firstPart =
      LocalCompositionEngine.generateCompositions(
          firstText,
          LocalCompositionEngine.buildProvisionalWordTimings(firstText, max(400L, midTime - comp.startMs))
            .map { it.copy(startMs = it.startMs + comp.startMs, endMs = it.endMs + comp.startMs) },
          max(400L, midTime - comp.startMs),
          preset,
          project.analysisReport,
          project.fontOverrides,
        )
        .first()
        .copy(startMs = comp.startMs, endMs = midTime)
    val secondPart =
      LocalCompositionEngine.generateCompositions(
          secondText,
          LocalCompositionEngine.buildProvisionalWordTimings(secondText, max(400L, comp.endMs - midTime))
            .map { it.copy(startMs = it.startMs + midTime, endMs = it.endMs + midTime) },
          max(400L, comp.endMs - midTime),
          preset,
          project.analysisReport,
          project.fontOverrides,
        )
        .first()
        .copy(startMs = midTime, endMs = comp.endMs)

    val mutable = project.compositions.toMutableList()
    mutable.removeAt(idx)
    mutable.addAll(idx, listOf(firstPart, secondPart))
    commitMutation(project.copy(compositions = mutable))
    _selectedCompositionId.value = firstPart.id
  }

  fun mergeWithNextComposition(compId: String) {
    val project = _activeProject.value ?: return
    val idx = project.compositions.indexOfFirst { it.id == compId }
    if (idx < 0 || idx >= project.compositions.lastIndex) {
      _statusBanner.value = "No following composition to merge with."
      return
    }
    val a = project.compositions[idx]
    val b = project.compositions[idx + 1]
    val merged =
      a.copy(
        endMs = b.endMs,
        originalText = "${a.originalText} ${b.originalText}".trim(),
        elements = a.elements + b.elements,
        wordMappings = a.wordMappings + b.wordMappings,
      )
    val mutable = project.compositions.toMutableList()
    mutable[idx] = merged
    mutable.removeAt(idx + 1)
    commitMutation(project.copy(compositions = mutable))
    _selectedCompositionId.value = merged.id
  }

  fun deleteComposition(compId: String) {
    val project = _activeProject.value ?: return
    val filtered = project.compositions.filterNot { it.id == compId }
    commitMutation(project.copy(compositions = filtered))
    _selectedCompositionId.value = filtered.firstOrNull()?.id
    _selectedElementId.value = filtered.firstOrNull()?.elements?.firstOrNull()?.id
  }

  fun addCustomElement(compId: String, text: String, role: FontRole = FontRole.SUPPORTING) {
    val project = _activeProject.value ?: return
    val clean = text.trim()
    if (clean.isEmpty()) return
    val preset = PresetCatalog.findById(project.presetId)
    val newEl =
      CompositionElement(
        id = UUID.randomUUID().toString(),
        text = clean,
        role = role,
        hierarchyLevel =
          when (role) {
            FontRole.HEAVY -> HierarchyLevel.DOMINANT
            FontRole.EXPRESSIVE -> HierarchyLevel.EXPRESSIVE
            FontRole.SUPPORTING -> HierarchyLevel.SUPPORTING
          },
        fontFamilyId =
          when (role) {
            FontRole.HEAVY -> project.fontOverrides.heavyFontId ?: preset.heavyFontId
            FontRole.EXPRESSIVE ->
              project.fontOverrides.expressiveFontId ?: preset.expressiveFontId
            FontRole.SUPPORTING ->
              project.fontOverrides.supportingFontId ?: preset.supportingFontId
          },
        fontWeight = if (role == FontRole.HEAVY) 900 else 600,
        isItalic = role == FontRole.EXPRESSIVE,
        fontSizeSp =
          when (role) {
            FontRole.HEAVY -> 42f
            FontRole.EXPRESSIVE -> 28f
            FontRole.SUPPORTING -> 20f
          },
        colorHex =
          when (role) {
            FontRole.HEAVY -> StudioPalette.YELLOW
            FontRole.EXPRESSIVE -> StudioPalette.ORANGE
            FontRole.SUPPORTING -> StudioPalette.WHITE
          },
        uppercase = role == FontRole.HEAVY,
      )
    val updated =
      project.compositions.map { comp ->
        if (comp.id == compId) comp.copy(elements = comp.elements + newEl) else comp
      }
    commitMutation(project.copy(compositions = updated))
    _selectedElementId.value = newEl.id
  }

  fun updateElementText(compId: String, elementId: String, newText: String) {
    val project = _activeProject.value ?: return
    val updated =
      project.compositions.map { comp ->
        if (comp.id != compId) comp
        else {
          val newElements =
            comp.elements.map { el -> if (el.id == elementId) el.copy(text = newText) else el }
          comp.copy(
            elements = newElements,
            originalText = newElements.joinToString(" ") { it.text },
          )
        }
      }
    commitMutation(project.copy(compositions = updated))
  }

  fun deleteElement(compId: String, elementId: String) {
    val project = _activeProject.value ?: return
    var nextSelectedEl: String? = null
    val updated =
      project.compositions.map { comp ->
        if (comp.id != compId || comp.elements.size <= 1) {
          comp
        } else {
          val remaining = comp.elements.filterNot { it.id == elementId }
          nextSelectedEl = remaining.firstOrNull()?.id
          comp.copy(
            elements = remaining,
            originalText = remaining.joinToString(" ") { it.text },
          )
        }
      }
    commitMutation(project.copy(compositions = updated))
    if (nextSelectedEl != null) {
      _selectedElementId.value = nextSelectedEl
    }
  }

  fun updateElementStyle(
    compId: String,
    elementId: String,
    scope: ApplyScope,
    patch: ElementStylePatch,
    recordHistory: Boolean = true,
  ) {
    val project = _activeProject.value ?: return
    val updatedComps =
      project.compositions.map { comp ->
        val matchesComp = scope == ApplyScope.ALL_COMPOSITIONS || comp.id == compId
        if (!matchesComp) {
          comp
        } else {
          val newElements =
            comp.elements.map { el ->
              val matchesEl = scope != ApplyScope.SINGLE_ELEMENT || el.id == elementId
              if (!matchesEl) el
              else {
                el.copy(
                  fontFamilyId = patch.fontFamilyId ?: el.fontFamilyId,
                  fontWeight = (patch.fontWeight ?: el.fontWeight).coerceIn(300, 900),
                  isItalic = patch.isItalic ?: el.isItalic,
                  isUnderlined = patch.isUnderlined ?: el.isUnderlined,
                  uppercase = patch.uppercase ?: el.uppercase,
                  fontSizeSp = (patch.fontSizeSp ?: el.fontSizeSp).coerceIn(12f, 68f),
                  colorHex = patch.colorHex ?: el.colorHex,
                  alignment = patch.alignment ?: el.alignment,
                  letterSpacingEm = (patch.letterSpacingEm ?: el.letterSpacingEm).coerceIn(-0.10f, 0.40f),
                  lineHeightMultiplier =
                    (patch.lineHeightMultiplier ?: el.lineHeightMultiplier).coerceIn(0.80f, 1.80f),
                  opacity = (patch.opacity ?: el.opacity).coerceIn(0.10f, 1.0f),
                  rotationDeg = (patch.rotationDeg ?: el.rotationDeg).coerceIn(-25f, 25f),
                  offsetXEm = (patch.offsetXEm ?: el.offsetXEm).coerceIn(-0.6f, 0.6f),
                  offsetYEm = (patch.offsetYEm ?: el.offsetYEm).coerceIn(-0.6f, 0.6f),
                  strokeWidthFactor = (patch.strokeWidthFactor ?: el.strokeWidthFactor).coerceIn(0f, 10f),
                  strokeColorHex = patch.strokeColorHex ?: el.strokeColorHex,
                  glowColorHex =
                    when {
                      patch.clearGlow -> null
                      patch.glowColorHex != null -> patch.glowColorHex
                      else -> el.glowColorHex
                    },
                  glowIntensity = (patch.glowIntensity ?: el.glowIntensity).coerceIn(0.25f, 2.2f),
                  shadowEnabled = patch.shadowEnabled ?: el.shadowEnabled,
                  shadowIntensity = (patch.shadowIntensity ?: el.shadowIntensity).coerceIn(0f, 2.2f),
                  shadowColorHex = patch.shadowColorHex ?: el.shadowColorHex,
                  backgroundColorHex =
                    when {
                      patch.clearBackground -> null
                      patch.backgroundColorHex != null -> patch.backgroundColorHex
                      else -> el.backgroundColorHex
                    },
                  backgroundAlpha = (patch.backgroundAlpha ?: el.backgroundAlpha).coerceIn(0.1f, 1.0f),
                  backgroundCornerRadiusDp =
                    (patch.backgroundCornerRadiusDp ?: el.backgroundCornerRadiusDp).coerceIn(0f, 32f),
                  depthLayer = patch.depthLayer ?: el.depthLayer,
                  motionEffect = patch.motionEffect ?: el.motionEffect,
                  motionSpeed = (patch.motionSpeed ?: el.motionSpeed).coerceIn(0.4f, 2.2f),
                  motionIntensity = (patch.motionIntensity ?: el.motionIntensity).coerceIn(0.25f, 2.0f),
                  karaokeMode = patch.karaokeMode ?: el.karaokeMode,
                  karaokeHighlightColorHex = patch.karaokeHighlightColorHex ?: el.karaokeHighlightColorHex,
                  karaokePillColorHex =
                    when {
                      patch.clearKaraokePill -> null
                      patch.karaokePillColorHex != null -> patch.karaokePillColorHex
                      else -> el.karaokePillColorHex
                    },
                  letterAnimation = patch.letterAnimation ?: el.letterAnimation,
                  surfaceStyle = patch.surfaceStyle ?: el.surfaceStyle,
                  tiltDegrees = (patch.tiltDegrees ?: el.tiltDegrees).coerceIn(-18f, 18f),
                  designerFrame = patch.designerFrame ?: el.designerFrame,
                  flourish = patch.flourish ?: el.flourish,
                )
              }
            }
          val anyBehind = newElements.any { it.depthLayer == DepthLayer.BEHIND_SUBJECT }
          val nextBgMask =
            if (patch.depthLayer == DepthLayer.BEHIND_SUBJECT) true
            else if (patch.depthLayer == DepthLayer.IN_FRONT && !anyBehind && comp.depthLayer != DepthLayer.BEHIND_SUBJECT) {
              false
            } else {
              comp.backgroundMaskEnabled
            }
          val nextCompDepth =
            if (patch.depthLayer == DepthLayer.IN_FRONT && !anyBehind) DepthLayer.IN_FRONT
            else comp.depthLayer
          val nextSpatialMode = patch.spatialMode ?: comp.spatialMode

          if (patch.safeRegion != null) {
            comp.copy(
              region = patch.safeRegion,
              anchorXFraction = patch.safeRegion.defaultAnchorX,
              anchorYFraction = patch.safeRegion.defaultAnchorY,
              backgroundMaskEnabled = nextBgMask,
              depthLayer = nextCompDepth,
              spatialMode = nextSpatialMode,
              elements = newElements,
            )
          } else {
            comp.copy(
              backgroundMaskEnabled = nextBgMask,
              depthLayer = nextCompDepth,
              spatialMode = nextSpatialMode,
              elements = newElements,
            )
          }
        }
      }
    commitMutation(project.copy(compositions = updatedComps), recordHistory = recordHistory)
  }

  fun resetElementStyle(compId: String, elementId: String) {
    val project = _activeProject.value ?: return
    val preset = PresetCatalog.findById(project.presetId)
    val updatedComps =
      project.compositions.map { comp ->
        if (comp.id != compId) comp
        else {
          comp.copy(
            elements =
              comp.elements.map { el ->
                if (el.id != elementId) el
                else {
                  val roleFont =
                    when (el.role) {
                      FontRole.HEAVY -> project.fontOverrides.heavyFontId ?: preset.heavyFontId
                      FontRole.EXPRESSIVE ->
                        project.fontOverrides.expressiveFontId ?: preset.expressiveFontId
                      FontRole.SUPPORTING ->
                        project.fontOverrides.supportingFontId ?: preset.supportingFontId
                    }
                  val roleColor =
                    when (el.hierarchyLevel) {
                      HierarchyLevel.DOMINANT -> preset.dominantColorHex
                      HierarchyLevel.EXPRESSIVE -> preset.expressiveColorHex
                      else -> preset.primaryColorHex
                    }
                  val baseSize =
                    when (el.hierarchyLevel) {
                      HierarchyLevel.DOMINANT -> 52f * preset.dominantScaleMultiplier
                      HierarchyLevel.EXPRESSIVE -> 32f * preset.expressiveScaleMultiplier
                      HierarchyLevel.SUPPORTING -> 20f
                      HierarchyLevel.SECONDARY -> 16f
                    }
                  el.copy(
                    fontFamilyId = roleFont,
                    fontWeight = if (el.role == FontRole.HEAVY) 900 else 600,
                    isItalic = el.role == FontRole.EXPRESSIVE,
                    isUnderlined = false,
                    uppercase = el.role == FontRole.HEAVY,
                    fontSizeSp = baseSize.coerceIn(15f, 68f),
                    colorHex = roleColor,
                    alignment = TextAlignment.CENTER,
                    letterSpacingEm = 0f,
                    lineHeightMultiplier = 1.05f,
                    opacity = 1.0f,
                    rotationDeg = 0f,
                    offsetXEm = 0f,
                    offsetYEm = 0f,
                    strokeWidthFactor = 0f,
                    strokeColorHex = StudioPalette.SHADOW,
                    glowColorHex = null,
                    glowIntensity = 1.0f,
                    shadowEnabled = true,
                    shadowIntensity = 1.0f,
                    shadowColorHex = StudioPalette.SHADOW,
                    backgroundColorHex = null,
                    backgroundAlpha = 0.72f,
                    backgroundCornerRadiusDp = 10f,
                    depthLayer = DepthLayer.IN_FRONT,
                    motionEffect = LayerMotionEffect.NONE,
                    motionSpeed = 1.0f,
                    motionIntensity = 1.0f,
                    karaokeMode = preset.defaultKaraokeMode,
                    karaokeHighlightColorHex = preset.dominantColorHex,
                    karaokePillColorHex = preset.backgroundPillColorHex,
                  )
                }
              }
          )
        }
      }
    commitMutation(project.copy(compositions = updatedComps))
    _statusBanner.value = "Reset layer style to preset defaults."
  }

  fun applyElementStyle(
    compId: String,
    elementId: String,
    scope: ApplyScope,
    fontFamilyId: String? = null,
    fontWeight: Int? = null,
    isItalic: Boolean? = null,
    fontSizeSp: Float? = null,
    colorHex: String? = null,
    alignment: TextAlignment? = null,
    uppercase: Boolean? = null,
    safeRegion: SafeRegion? = null,
  ) {
    updateElementStyle(
      compId = compId,
      elementId = elementId,
      scope = scope,
      patch =
        ElementStylePatch(
          fontFamilyId = fontFamilyId,
          fontWeight = fontWeight,
          isItalic = isItalic,
          fontSizeSp = fontSizeSp,
          colorHex = colorHex,
          alignment = alignment,
          uppercase = uppercase,
          safeRegion = safeRegion,
        ),
    )
  }

  fun applyAnimationSettings(
    compId: String,
    scope: ApplyScope,
    entry: EntryAnimationType,
    exit: ExitAnimationType,
    easing: AnimationEasingType,
    entryDurationMs: Int,
    exitDurationMs: Int,
  ) {
    val project = _activeProject.value ?: return
    val updated =
      project.compositions.map { comp ->
        if (scope == ApplyScope.ALL_COMPOSITIONS || comp.id == compId) {
          comp.copy(
            entryAnimation = entry,
            exitAnimation = exit,
            easing = easing,
            entryDurationMs = entryDurationMs.coerceIn(80, 350),
            exitDurationMs = exitDurationMs.coerceIn(80, 300),
          )
        } else comp
      }
    val defaults =
      if (scope == ApplyScope.ALL_COMPOSITIONS) {
        AnimationDefaults(entry, exit, easing, entryDurationMs, exitDurationMs)
      } else {
        project.animationDefaults
      }
    commitMutation(project.copy(compositions = updated, animationDefaults = defaults))
  }

  fun applyPreset(presetId: String) {
    val project = _activeProject.value ?: return
    val preset = PresetCatalog.findById(presetId)
    val restyled =
      PresetCatalog.applyPresetToCompositions(
        preset = preset,
        compositions = project.compositions,
        analysisReport = project.analysisReport,
        fontOverrides = project.fontOverrides,
      )
    val validated =
      CompositionValidator.validateAndRepair(
        context = getApplication(),
        compositions = restyled,
        videoWidthPx = project.metadata.effectiveWidth,
        videoHeightPx = project.metadata.effectiveHeight,
      )
    commitMutation(project.copy(presetId = preset.id, compositions = validated))
    _statusBanner.value = "Applied ${preset.name} preset without altering transcript or timings."
  }

  fun applyCuratedPairing(pairing: CuratedFontPairing) {
    val project = _activeProject.value ?: return
    val overrides =
      FontRoleOverrides(
        heavyFontId = pairing.heavyFontId,
        supportingFontId = pairing.supportingFontId,
        expressiveFontId = pairing.expressiveFontId,
      )
    val updatedComps =
      project.compositions.map { comp ->
        comp.copy(
          elements =
            comp.elements.map { el ->
              val fId =
                when (el.role) {
                  FontRole.HEAVY -> pairing.heavyFontId
                  FontRole.SUPPORTING -> pairing.supportingFontId
                  FontRole.EXPRESSIVE -> pairing.expressiveFontId
                }
              el.copy(fontFamilyId = fId)
            }
        )
      }
    commitMutation(project.copy(fontOverrides = overrides, compositions = updatedComps))
    _statusBanner.value = "Applied font pairing: ${pairing.name}"
  }

  fun downloadFontFamily(familyName: String, role: FontRole) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val result = FontRegistry.downloadAndCacheFont(getApplication(), familyName, role)
        _statusBanner.value =
          result.fold(
            onSuccess = {
              _fontsVersion.value += 1
              "Cached '${it.displayName}' locally for offline use."
            },
            onFailure = { it.localizedMessage ?: "Font download failed." },
          )
      } finally {
        _isBusy.value = false
      }
    }
  }

  // --- Preview & Speech Settings ---

  fun setPreviewQualityMode(mode: PreviewQualityMode) {
    viewModelScope.launch { settingsRepo.setPreviewQualityMode(mode) }
  }

  fun setSpeechLanguageTag(tag: String) {
    viewModelScope.launch { settingsRepo.setSpeechLanguageTag(tag) }
  }

  fun setAsrEnginePreference(preference: AsrEnginePreference) {
    viewModelScope.launch { settingsRepo.setAsrEnginePreference(preference) }
    val resolved = resolveOfflineAsrEngine(preference, isUnder4GbRam)
    _statusBanner.value =
      when (preference) {
        AsrEnginePreference.AUTO -> "ASR Engine: Auto (${resolved.displayName})"
        AsrEnginePreference.WHISPER_SMALL -> "ASR Engine: Whisper Small (high accuracy)"
        AsrEnginePreference.VOSK_FAST -> "ASR Engine: Vosk Fast (low-RAM)"
      }
  }

  fun refreshVoicePackStatuses(
    activeDownloadPackId: String? = null,
    activeProgress: Float = 0f,
    errorPackId: String? = null,
    errorMessage: String? = null,
  ) {
    val ctx = getApplication<Application>()
    _voicePackStatuses.value =
      OfflineVoskModelManager.supportedPacks.map { pack ->
        OfflineVoicePackStatus(
          pack = pack,
          isInstalled = OfflineVoskModelManager.isPackInstalled(ctx, pack.id),
          isDownloading = pack.id == activeDownloadPackId,
          downloadProgress = if (pack.id == activeDownloadPackId) activeProgress else 0f,
          errorMessage = if (pack.id == errorPackId) errorMessage else null,
        )
      }
  }

  fun downloadOfflineVoicePack(pack: OfflineVoicePackInfo) {
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      refreshVoicePackStatuses(activeDownloadPackId = pack.id, activeProgress = 0.02f)
      val res =
        OfflineVoskModelManager.ensureOrDownloadPack(ctx, pack) { p ->
          refreshVoicePackStatuses(activeDownloadPackId = pack.id, activeProgress = p)
        }
      res.fold(
        onSuccess = {
          refreshVoicePackStatuses()
          _statusBanner.value = "${pack.displayName} offline voice pack installed (works offline on Android 7+)."
        },
        onFailure = { err ->
          refreshVoicePackStatuses(
            errorPackId = pack.id,
            errorMessage = err.localizedMessage ?: "Download failed",
          )
          _statusBanner.value = "Voice pack download failed: ${err.localizedMessage}"
        },
      )
    }
  }

  fun downloadOfflineVoicePackForLanguage(languageTag: String) {
    downloadOfflineVoicePack(OfflineVoskModelManager.packForLanguage(languageTag))
  }

  fun deleteOfflineVoicePack(packId: String) {
    OfflineVoskModelManager.deletePack(getApplication(), packId)
    refreshVoicePackStatuses()
    _statusBanner.value = "Removed cached offline voice pack."
  }

  // --- Custom Cloned Presets, Shareable Deep Links, Creator Macros & Descript Word Editing ---

  fun saveCustomPreset(preset: PresetDefinition, applyToActiveProject: Boolean) {
    viewModelScope.launch {
      settingsRepo.saveCustomPreset(preset)
      val updated = (listOf(preset) + customPresets.value.filterNot { it.id == preset.id }).take(30)
      PresetCatalog.registerCustomPresets(updated)
      if (applyToActiveProject && _activeProject.value != null) {
        applyPreset(preset.id)
      }
      _statusBanner.value =
        if (applyToActiveProject) "Saved & applied \"${preset.name}\" style!"
        else "Saved \"${preset.name}\" to My Presets!"
    }
  }

  fun deleteCustomPreset(presetId: String) {
    viewModelScope.launch {
      settingsRepo.deleteCustomPreset(presetId)
      PresetCatalog.registerCustomPresets(customPresets.value.filterNot { it.id == presetId })
      _statusBanner.value = "Removed custom style."
    }
  }

  fun importStyleFromDeepLink(rawLink: String): Boolean {
    val decoded = StyleDeepLinkCodec.decodeFromDeepLink(rawLink) ?: return false
    saveCustomPreset(decoded, applyToActiveProject = _activeProject.value != null)
    _statusBanner.value = "Imported style \"${decoded.name}\" from link!"
    return true
  }

  fun buildCurrentProjectPreset(customName: String): PresetDefinition? {
    val project = _activeProject.value ?: return null
    val base = PresetCatalog.findById(project.presetId)
    val firstComp = project.compositions.firstOrNull()
    val dom = firstComp?.elements?.firstOrNull { it.hierarchyLevel == HierarchyLevel.DOMINANT }
    val sup = firstComp?.elements?.firstOrNull { it.hierarchyLevel == HierarchyLevel.SUPPORTING }
    val exp = firstComp?.elements?.firstOrNull { it.hierarchyLevel == HierarchyLevel.EXPRESSIVE }
    return base.copy(
      id = "saved_${UUID.randomUUID().toString().take(8)}",
      name = customName.ifBlank { "${project.title} Look" }.trim().uppercase(),
      tagline = "Saved Creator Preset • ${(dom?.fontFamilyId ?: base.heavyFontId).replace('_', ' ')}",
      heavyFontId = dom?.fontFamilyId ?: base.heavyFontId,
      supportingFontId = sup?.fontFamilyId ?: base.supportingFontId,
      expressiveFontId = exp?.fontFamilyId ?: base.expressiveFontId,
      primaryColorHex = sup?.colorHex ?: base.primaryColorHex,
      dominantColorHex = dom?.colorHex ?: base.dominantColorHex,
      expressiveColorHex = exp?.colorHex ?: base.expressiveColorHex,
      defaultEntryAnim = firstComp?.entryAnimation ?: base.defaultEntryAnim,
      defaultExitAnim = firstComp?.exitAnimation ?: base.defaultExitAnim,
      defaultEasing = firstComp?.easing ?: base.defaultEasing,
      entryDurationMs = firstComp?.entryDurationMs ?: base.entryDurationMs,
      exitDurationMs = firstComp?.exitDurationMs ?: base.exitDurationMs,
      strokeWidthFactor = dom?.strokeWidthFactor ?: base.strokeWidthFactor,
      strokeColorHex = dom?.strokeColorHex ?: base.strokeColorHex,
      backgroundPillColorHex = sup?.backgroundColorHex ?: dom?.backgroundColorHex,
      preferBehindSubject = firstComp?.hasBehindSubjectElements() ?: base.preferBehindSubject,
      preferUppercase = dom?.uppercase ?: base.preferUppercase,
      isCustomCloned = true,
    )
  }

  fun scaleAllCompositionsText(scaleMultiplier: Float) {
    val project = _activeProject.value ?: return
    val clamped = scaleMultiplier.coerceIn(0.70f, 1.40f)
    val updated =
      project.compositions.map { comp ->
        comp.copy(
          elements =
            comp.elements.map { el ->
              val baseSp =
                when (el.hierarchyLevel) {
                  HierarchyLevel.DOMINANT -> 54f
                  HierarchyLevel.EXPRESSIVE -> 34f
                  HierarchyLevel.SUPPORTING -> 20f
                  HierarchyLevel.SECONDARY -> 16f
                }
              el.copy(fontSizeSp = (baseSp * clamped).coerceIn(13f, 76f))
            }
        )
      }
    commitMutation(project.copy(compositions = updated), recordHistory = false)
  }

  fun setAllCompositionsHeight(anchorY: Float) {
    val project = _activeProject.value ?: return
    val y = anchorY.coerceIn(0.12f, 0.88f)
    val updated =
      project.compositions.map { comp ->
        comp.copy(anchorYFraction = y, region = SafeRegion.CUSTOM)
      }
    commitMutation(project.copy(compositions = updated), recordHistory = false)
  }

  fun setAllCompositionsEnergy(energy01: Float) {
    val project = _activeProject.value ?: return
    val e = energy01.coerceIn(0f, 1f)
    val entryMs = (260f - e * 165f).toInt().coerceIn(95, 260)
    val easing =
      when {
        e > 0.68f -> AnimationEasingType.SPRING_OVERSHOOT
        e > 0.35f -> AnimationEasingType.DECELERATE
        else -> AnimationEasingType.SMOOTH_IN_OUT
      }
    val updated =
      project.compositions.map { comp ->
        comp.copy(
          entryDurationMs = entryMs,
          exitDurationMs = (entryMs * 0.78f).toInt().coerceIn(80, 200),
          easing = easing,
        )
      }
    commitMutation(project.copy(compositions = updated), recordHistory = false)
  }

  /**
   * Two-finger pinch-to-resize on the live video stage.
   * Captures a single pre-pinch snapshot so Undo cleanly reverts the entire pinch gesture.
   */
  fun scaleCompositionElements(
    compId: String,
    scaleDelta: Float,
    recordHistory: Boolean = false,
  ) {
    val project = _activeProject.value ?: return
    if (!recordHistory) {
      if (pendingPinchSnapshot == null) {
        pendingPinchSnapshot = project
      }
      val safeFactor = scaleDelta.coerceIn(0.82f, 1.22f)
      val updated =
        project.compositions.map { comp ->
          if (comp.id != compId) comp
          else {
            comp.copy(
              elements =
                comp.elements.map { el ->
                  el.copy(fontSizeSp = (el.fontSizeSp * safeFactor).coerceIn(12f, 74f))
                }
            )
          }
        }
      commitMutation(project.copy(compositions = updated), recordHistory = false)
    } else {
      val snapshot = pendingPinchSnapshot
      pendingPinchSnapshot = null
      if (snapshot != null && snapshot != project) {
        undoStack.addLast(snapshot)
        while (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
      }
      val updated =
        if (scaleDelta == 1f) {
          project.compositions
        } else {
          val safeFactor = scaleDelta.coerceIn(0.82f, 1.22f)
          project.compositions.map { comp ->
            if (comp.id != compId) comp
            else {
              comp.copy(
                elements =
                  comp.elements.map { el ->
                    el.copy(fontSizeSp = (el.fontSizeSp * safeFactor).coerceIn(12f, 74f))
                  }
              )
            }
          }
        }
      commitMutation(project.copy(compositions = updated), recordHistory = false)
    }
  }

  /**
   * Batch Hook & B-Roll Pacing Template:
   * Automatically styles opening 0–3s hook captions with bold kinetic punch and body captions
   * with clean retention-friendly pacing, preserving exact transcript and word timings.
   */
  fun applyHookPacingTemplate(template: HookPacingTemplate) {
    val project = _activeProject.value ?: return
    if (project.compositions.isEmpty()) {
      _statusBanner.value = "Generate or transcribe captions first to apply Hook Pacing."
      return
    }
    val preset = PresetCatalog.findById(project.presetId)
    val result =
      LocalCompositionEngine.applyHookPacingTemplate(
        compositions = project.compositions,
        template = template,
        activePreset = preset,
        analysisReport = project.analysisReport,
        fontOverrides = project.fontOverrides,
      )
    val validated =
      CompositionValidator.validateAndRepair(
        context = getApplication(),
        compositions = result.compositions,
        videoWidthPx = project.metadata.effectiveWidth,
        videoHeightPx = project.metadata.effectiveHeight,
      )
    commitMutation(project.copy(compositions = validated), recordHistory = true)
    _statusBanner.value =
      if (template == HookPacingTemplate.UNIFORM_RESET) {
        "Reset all ${validated.size} captions evenly to ${preset.name}."
      } else {
        "Applied ${template.title}: ${result.hookCount} hook + ${result.bodyCount} body captions."
      }
  }

  /**
   * One-tap Active-Word Karaoke mode toggle across all captions in the active project.
   */
  fun setAllKaraokeMode(mode: KaraokeHighlightMode, highlightHex: String? = null) {
    val project = _activeProject.value ?: return
    if (project.compositions.isEmpty()) return
    val preset = PresetCatalog.findById(project.presetId)
    val chosenHighlight =
      highlightHex
        ?: preset.dominantColorHex.takeIf { it != StudioPalette.WHITE }
        ?: StudioPalette.YELLOW
    val chosenPill =
      when (mode) {
        KaraokeHighlightMode.PILL_SWEEP ->
          preset.expressiveColorHex.takeIf { it != StudioPalette.WHITE } ?: "#FF5A36"
        KaraokeHighlightMode.UNDERLINE_SWEEP -> chosenHighlight
        else -> null
      }
    val updated =
      project.compositions.map { comp ->
        comp.copy(
          elements =
            comp.elements.map { el ->
              el.copy(
                karaokeMode = mode,
                karaokeHighlightColorHex = chosenHighlight,
                karaokePillColorHex = chosenPill ?: el.karaokePillColorHex,
              )
            }
        )
      }
    commitMutation(project.copy(compositions = updated), recordHistory = true)
    _statusBanner.value =
      if (mode == KaraokeHighlightMode.OFF) "Active-word karaoke disabled."
      else "Active-word karaoke: ${mode.label}"
  }

  private fun resolveEffectiveWordTimings(project: CaptionProject): List<WordTiming> {
    if (project.wordTimings.isNotEmpty()) return project.wordTimings
    val fromComps =
      project.compositions.flatMap { comp ->
        if (comp.wordMappings.isNotEmpty()) {
          comp.wordMappings
        } else {
          val tokens =
            comp.originalText.ifBlank { comp.elements.joinToString(" ") { it.text } }
              .trim()
              .split(Regex("\\s+"))
              .filter { it.isNotBlank() }
          if (tokens.isEmpty()) {
            emptyList()
          } else {
            val dur = max(200L, comp.endMs - comp.startMs)
            val slice = dur / tokens.size
            tokens.mapIndexed { idx, token ->
              val ws = comp.startMs + slice * idx
              val we = if (idx == tokens.lastIndex) comp.endMs else max(ws + 80L, ws + slice)
              WordTiming(
                word = token,
                startMs = ws,
                endMs = we,
                isEstimated = true,
                emphasisScore = LocalCompositionEngine.scoreWordSalience(token, idx, tokens.size),
              )
            }
          }
        }
      }
    return fromComps
  }

  fun quickEditWord(
    wordIndex: Int,
    newText: String,
    emphasis: HierarchyLevel,
    behindSubject: Boolean,
  ) {
    val project = _activeProject.value ?: return
    val baseTimings = resolveEffectiveWordTimings(project)
    if (wordIndex !in baseTimings.indices) return
    val clean = newText.trim()
    if (clean.isEmpty()) return

    val oldWord = baseTimings[wordIndex]
    val newEmphasisScore =
      when (emphasis) {
        HierarchyLevel.DOMINANT -> 0.95f
        HierarchyLevel.EXPRESSIVE -> 0.82f
        else -> 0.15f
      }
    val updatedTimings =
      baseTimings.toMutableList().apply {
        this[wordIndex] = oldWord.copy(word = clean, emphasisScore = newEmphasisScore)
      }
    val updatedTranscript = updatedTimings.joinToString(" ") { it.word }
    val preset = PresetCatalog.findById(project.presetId)

    val updatedComps =
      project.compositions.map { comp ->
        val matchesTiming = oldWord.startMs in (comp.startMs - 80L)..(comp.endMs + 80L)
        if (!matchesTiming) {
          comp
        } else {
          val replacedWordMappings =
            comp.wordMappings.map { wm ->
              if (wm.startMs == oldWord.startMs && wm.word.equals(oldWord.word, ignoreCase = true)) {
                wm.copy(word = clean, emphasisScore = newEmphasisScore)
              } else wm
            }
          val replacedElements =
            comp.elements.map { el ->
              if (el.text.contains(oldWord.word, ignoreCase = true)) {
                val updatedElText =
                  el.text.replaceFirst(oldWord.word, clean, ignoreCase = true)
                val targetRole =
                  when (emphasis) {
                    HierarchyLevel.DOMINANT -> FontRole.HEAVY
                    HierarchyLevel.EXPRESSIVE -> FontRole.EXPRESSIVE
                    else -> FontRole.SUPPORTING
                  }
                val targetFont =
                  when (targetRole) {
                    FontRole.HEAVY -> project.fontOverrides.heavyFontId ?: preset.heavyFontId
                    FontRole.EXPRESSIVE -> project.fontOverrides.expressiveFontId ?: preset.expressiveFontId
                    FontRole.SUPPORTING -> project.fontOverrides.supportingFontId ?: preset.supportingFontId
                  }
                val targetColor =
                  when (emphasis) {
                    HierarchyLevel.DOMINANT -> preset.dominantColorHex
                    HierarchyLevel.EXPRESSIVE -> preset.expressiveColorHex
                    else -> preset.primaryColorHex
                  }
                val targetSize =
                  when (emphasis) {
                    HierarchyLevel.DOMINANT -> 54f * preset.dominantScaleMultiplier
                    HierarchyLevel.EXPRESSIVE -> 34f * preset.expressiveScaleMultiplier
                    else -> 20f
                  }
                el.copy(
                  text = updatedElText,
                  role = targetRole,
                  hierarchyLevel = emphasis,
                  fontFamilyId = targetFont,
                  colorHex = targetColor,
                  fontSizeSp = targetSize.coerceIn(15f, 74f),
                  depthLayer = if (behindSubject) DepthLayer.BEHIND_SUBJECT else DepthLayer.IN_FRONT,
                )
              } else el
            }
          comp.copy(
            originalText = replacedWordMappings.joinToString(" ") { it.word }.ifBlank {
              replacedElements.joinToString(" ") { it.text }
            },
            wordMappings = replacedWordMappings,
            elements = replacedElements,
            depthLayer = if (behindSubject) DepthLayer.BEHIND_SUBJECT else comp.depthLayer,
            backgroundMaskEnabled = if (behindSubject) true else comp.backgroundMaskEnabled,
          )
        }
      }

    commitMutation(
      project.copy(
        transcriptText = updatedTranscript,
        wordTimings = updatedTimings,
        compositions = updatedComps,
      ),
      recordHistory = true,
    )
    _statusBanner.value = "Updated \"$clean\""
  }

  fun deleteWordAt(wordIndex: Int) {
    val project = _activeProject.value ?: return
    val baseTimings = resolveEffectiveWordTimings(project)
    if (wordIndex !in baseTimings.indices) return
    val removed = baseTimings[wordIndex]
    val updatedTimings = baseTimings.toMutableList().apply { removeAt(wordIndex) }
    val updatedTranscript = updatedTimings.joinToString(" ") { it.word }
    val preset = PresetCatalog.findById(project.presetId)
    val regenerated =
      if (updatedTimings.isEmpty()) {
        emptyList()
      } else {
        LocalCompositionEngine.generateCompositions(
          transcript = updatedTranscript,
          wordTimings = updatedTimings,
          totalDurationMs = max(project.metadata.durationMs, updatedTimings.last().endMs),
          preset = preset,
          analysisReport = project.analysisReport,
          fontOverrides = project.fontOverrides,
        )
      }
    commitMutation(
      project.copy(
        transcriptText = updatedTranscript,
        wordTimings = updatedTimings,
        compositions = regenerated,
      ),
      recordHistory = true,
    )
    _statusBanner.value = "Removed \"${removed.word}\""
  }

  // --- Beat Sync & Non-Destructive Auto-Jump-Cut Silence Trimmer ---

  fun mapEditedToSourceTimeMs(editedTimeMs: Long): Long {
    val keepSegments = _activeProject.value?.analysisReport?.keepSegments.orEmpty()
    return AudioExtractionService.mapEditedToSourceTimeMs(editedTimeMs, keepSegments)
  }

  /**
   * 1-tap Beat Sync: snaps every caption block's entrance (`startMs`) to the nearest audio
   * transient within ±110ms while preserving minimum duration and strictly non-overlapping order.
   */
  fun beatSyncCompositions() {
    val project = _activeProject.value ?: return
    if (project.compositions.isEmpty()) {
      _statusBanner.value = "Generate or transcribe captions before running Beat Sync."
      return
    }
    val existingBeats =
      project.analysisReport.beatTimestampsMs.ifEmpty {
        AudioExtractionService.getCachedBeatTimestamps(project.videoUriString).orEmpty()
      }
    if (existingBeats.isNotEmpty()) {
      applyBeatSyncWithSourceBeats(project, existingBeats)
      return
    }
    if (project.videoUriString.isBlank() || !project.metadata.hasAudio) {
      _statusBanner.value = "Attach a video with audio to detect beat transients."
      return
    }

    activeBackgroundJob?.cancel()
    activeBackgroundJob =
      viewModelScope.launch {
        _isBusy.value = true
        _backgroundTaskState.value =
          BackgroundTaskHudState(
            isRunning = true,
            stepLabel = "Detecting audio beat transients...",
            progressFraction = 0.45f,
          )
        try {
          val track =
            withContext(Dispatchers.IO) {
              AudioExtractionService.extractAudioTrack(
                getApplication(),
                Uri.parse(project.videoUriString),
              )
            }
          if (track == null) {
            _statusBanner.value = "Could not decode audio track for Beat Sync."
            return@launch
          }
          val latest = _activeProject.value ?: return@launch
          val keepSegments = latest.analysisReport.keepSegments
          _audioWaveformBins.value =
            if (keepSegments.isNotEmpty()) {
              AudioExtractionService.resampleWaveformForKeepSegments(track.energyEnvelope10ms, keepSegments)
            } else {
              track.waveformBins
            }
          if (track.beatTimestampsMs.isEmpty()) {
            _statusBanner.value = "No distinct beat transients found in this audio track."
          } else {
            applyBeatSyncWithSourceBeats(latest, track.beatTimestampsMs)
          }
        } catch (_: CancellationException) {
          _statusBanner.value = "Beat Sync cancelled."
        } catch (_: Exception) {
          _statusBanner.value = "Could not analyze audio track for Beat Sync."
        } finally {
          _isBusy.value = false
          _backgroundTaskState.value = BackgroundTaskHudState()
        }
      }
  }

  private fun applyBeatSyncWithSourceBeats(
    project: CaptionProject,
    sourceBeatsMs: List<Long>,
  ) {
    val keepSegments = project.analysisReport.keepSegments
    val editedBeats = AudioExtractionService.remapBeatsToEditedTimeline(sourceBeatsMs, keepSegments)
    val editedDuration =
      max(
        AudioExtractionService.computeEditedDurationMs(keepSegments, project.metadata.durationMs),
        project.compositions.maxOfOrNull { it.endMs } ?: 4000L,
      )
    val (snappedComps, snappedCount) =
      snapCompositionsToBeats(
        compositions = project.compositions,
        beatTimestampsMs = editedBeats,
        totalDurationMs = editedDuration,
        maxSnapDeltaMs = 110L,
        minDurationMs = 300L,
      )
    val updatedReport =
      if (project.analysisReport.beatTimestampsMs == sourceBeatsMs) {
        project.analysisReport
      } else {
        project.analysisReport.copy(beatTimestampsMs = sourceBeatsMs)
      }
    commitMutation(
      project.copy(
        analysisReport = updatedReport,
        compositions = snappedComps,
      ),
      recordHistory = snappedCount > 0,
    )
    _statusBanner.value =
      if (snappedCount > 0) {
        "🧲 Beat Sync snapped $snappedCount caption${if (snappedCount == 1) "" else "s"} to audio transients."
      } else {
        "Captions are already tight with nearby beats."
      }
  }

  /**
   * Non-destructive Auto-Jump-Cut silence trimmer:
   * Detects dead-air silence gaps (>420ms) with 90ms natural breath padding, creates a kept
   * segment edit list (`KeepSegment`) for both ExoPlayer preview and Media3 Transformer export,
   * and remaps captions/word timings while backing up the uncut timeline for 1-tap restore.
   */
  fun trimSilencesAutoJumpCut() {
    val project = _activeProject.value ?: return
    if (project.videoUriString.isBlank() || !project.metadata.hasAudio) {
      _statusBanner.value = "Attach a video with spoken audio to auto-trim silences."
      return
    }
    if (project.analysisReport.keepSegments.isNotEmpty()) {
      _statusBanner.value = "Silences already trimmed. Use Restore Original Cut to revert."
      return
    }

    activeBackgroundJob?.cancel()
    activeBackgroundJob =
      viewModelScope.launch {
        _isBusy.value = true
        _backgroundTaskState.value =
          BackgroundTaskHudState(
            isRunning = true,
            stepLabel = "Detecting dead-air silences (>420ms)...",
            progressFraction = 0.35f,
          )
        try {
          var rawEnvelope = AudioExtractionService.getCachedRawEnvelope(project.videoUriString)
          var extractedBeats = project.analysisReport.beatTimestampsMs
          if (rawEnvelope == null) {
            val track =
              withContext(Dispatchers.IO) {
                AudioExtractionService.extractAudioTrack(
                  getApplication(),
                  Uri.parse(project.videoUriString),
                )
              }
            if (track == null) {
              _statusBanner.value = "Could not decode audio track for silence trimming."
              return@launch
            }
            rawEnvelope = track.energyEnvelope10ms
            if (extractedBeats.isEmpty()) extractedBeats = track.beatTimestampsMs
          }

          val latest = _activeProject.value ?: return@launch
          val sourceDurationMs = max(latest.metadata.durationMs, rawEnvelope.size * 10L)
          _backgroundTaskState.value =
            BackgroundTaskHudState(
              isRunning = true,
              stepLabel = "Building jump-cut timeline & remapping captions...",
              progressFraction = 0.78f,
            )

          val keepSegments =
            AudioExtractionService.detectKeepSegments(
              envelope10ms = rawEnvelope,
              totalDurationMs = sourceDurationMs,
              minSilenceGapMs = 420L,
              breathPadMs = 90L,
            )
          val editedDurationMs =
            AudioExtractionService.computeEditedDurationMs(keepSegments, sourceDurationMs)
          val savedMs = (sourceDurationMs - editedDurationMs).coerceAtLeast(0L)

          if (keepSegments.isEmpty() || savedMs < 250L) {
            _statusBanner.value = "No dead-air gaps over 0.42s detected — your pacing is already tight."
            return@launch
          }

          val remappedWords =
            AudioExtractionService.remapWordTimingsToEditedTimeline(
              wordTimings = latest.wordTimings,
              keepSegments = keepSegments,
            )
          val remappedComps =
            AudioExtractionService.remapCompositionsToEditedTimeline(
              compositions = latest.compositions,
              keepSegments = keepSegments,
            )

          val updatedReport =
            latest.analysisReport.copy(
              beatTimestampsMs = extractedBeats,
              keepSegments = keepSegments,
              originalCompositionsBackup = latest.compositions,
              originalWordTimingsBackup = latest.wordTimings,
            )
          val updatedProject =
            latest.copy(
              analysisReport = updatedReport,
              wordTimings = remappedWords,
              compositions = remappedComps,
            )

          commitMutation(updatedProject, recordHistory = true)
          _transcribedSegments.value =
            if (remappedWords.isNotEmpty()) {
              VoskVideoTranscriptionService.segmentRecognizedWords(remappedWords)
            } else {
              emptyList()
            }
          _audioWaveformBins.value =
            AudioExtractionService.resampleWaveformForKeepSegments(rawEnvelope, keepSegments)
          _playheadMs.value = 0L
          _selectedCompositionId.value = remappedComps.firstOrNull()?.id
          _selectedElementId.value = remappedComps.firstOrNull()?.elements?.firstOrNull()?.id

          val savedSec = String.format(Locale.US, "%.1f", savedMs / 1000f)
          _statusBanner.value =
            "✂️ Trimmed ${savedSec}s of silence (${keepSegments.size} tight jump cuts)."
        } catch (_: CancellationException) {
          _statusBanner.value = "Silence trimming cancelled."
        } catch (_: Exception) {
          _statusBanner.value = "Could not analyze audio track for silence trimming."
        } finally {
          _isBusy.value = false
          _backgroundTaskState.value = BackgroundTaskHudState()
        }
      }
  }

  /**
   * Non-destructively restores the original uncut video timeline, captions, and word timings.
   */
  fun restoreOriginalCut() {
    val project = _activeProject.value ?: return
    if (project.analysisReport.keepSegments.isEmpty()) return

    val restoredComps =
      project.analysisReport.originalCompositionsBackup.ifEmpty { project.compositions }
    val restoredWords =
      project.analysisReport.originalWordTimingsBackup.ifEmpty { project.wordTimings }
    val restoredReport =
      project.analysisReport.copy(
        keepSegments = emptyList(),
        originalCompositionsBackup = emptyList(),
        originalWordTimingsBackup = emptyList(),
      )
    val restoredProject =
      project.copy(
        analysisReport = restoredReport,
        compositions = restoredComps,
        wordTimings = restoredWords,
      )
    commitMutation(restoredProject, recordHistory = true)
    _transcribedSegments.value =
      if (restoredWords.isNotEmpty()) {
        VoskVideoTranscriptionService.segmentRecognizedWords(restoredWords)
      } else {
        emptyList()
      }
    val cachedBins = AudioExtractionService.getCachedWaveformBins(project.videoUriString)
    if (cachedBins != null) {
      _audioWaveformBins.value = cachedBins
    }
    _playheadMs.value = 0L
    _selectedCompositionId.value = restoredComps.firstOrNull()?.id
    _selectedElementId.value = restoredComps.firstOrNull()?.elements?.firstOrNull()?.id
    _statusBanner.value = "Restored original uncut timeline."
  }

  // --- Export (Background ForegroundService) ---

  fun updateExportPreferences(prefs: ExportPreferences) {
    val project = _activeProject.value ?: return
    commitMutation(project.copy(exportPreferences = prefs), recordHistory = false)
  }

  fun startExport() {
    val project = _activeProject.value ?: return
    val uri =
      if (project.videoUriString.isBlank()) Uri.EMPTY else Uri.parse(project.videoUriString)
    val effectivePrefs =
      if (isProUnlocked.value) project.exportPreferences
      else project.exportPreferences.copy(showWatermark = true)
    VideoExportCoordinator.startForegroundExport(
      context = getApplication(),
      videoUri = uri,
      compositions = project.compositions,
      presetId = project.presetId,
      exportPrefs = effectivePrefs,
      outputTitle = project.title,
      faceAnchorSamples = project.analysisReport.faceAnchorSamples,
      audioEnergyEnvelope10ms =
        AudioExtractionService.getCachedNormalizedEnvelope(project.videoUriString),
      keepSegments = project.analysisReport.keepSegments,
    )
  }

  fun cancelExport() {
    VideoExportCoordinator.cancelExport(getApplication())
  }

  fun resetExportState() {
    VideoExportCoordinator.resetState(getApplication())
  }

  override fun onCleared() {
    super.onCleared()
    speechManager.stopListening()
    autoTranscriber.release()
    // Do NOT cancel active VideoExportForegroundService here so background exports complete reliably!
  }

  companion object {
    /**
     * Pure deterministic Beat Sync algorithm:
     * Snaps each caption block's entrance (`startMs`) to the nearest audio transient within
     * ±[maxSnapDeltaMs] while strictly preserving minimum block duration ([minDurationMs]) and
     * non-overlapping ascending order.
     */
    fun snapCompositionsToBeats(
      compositions: List<CaptionComposition>,
      beatTimestampsMs: List<Long>,
      totalDurationMs: Long,
      maxSnapDeltaMs: Long = 110L,
      minDurationMs: Long = 300L,
    ): Pair<List<CaptionComposition>, Int> {
      if (compositions.isEmpty() || beatTimestampsMs.isEmpty()) {
        return compositions to 0
      }
      val sorted = compositions.sortedBy { it.startMs }
      val safeTotal =
        max(totalDurationMs, (sorted.maxOfOrNull { it.endMs } ?: 0L).coerceAtLeast(minDurationMs))
      val result = ArrayList<CaptionComposition>(sorted.size)
      var snappedCount = 0

      for (idx in sorted.indices) {
        val comp = sorted[idx]
        val prevEnd = if (idx > 0) result[idx - 1].endMs else 0L
        val nextStart = sorted.getOrNull(idx + 1)?.startMs ?: safeTotal
        val maxAllowedEnd = max(prevEnd + minDurationMs, min(safeTotal, nextStart))
        val maxAllowedStart = (maxAllowedEnd - minDurationMs).coerceAtLeast(prevEnd)

        var bestBeat: Long? = null
        var bestDiff = Long.MAX_VALUE
        for (beat in beatTimestampsMs) {
          val diff = abs(beat - comp.startMs)
          if (diff <= maxSnapDeltaMs && diff < bestDiff) {
            bestDiff = diff
            bestBeat = beat
          }
        }

        val currentStart = comp.startMs.coerceIn(prevEnd, maxAllowedStart)
        val currentEnd = comp.endMs.coerceIn(currentStart + minDurationMs, maxAllowedEnd)

        if (bestBeat != null) {
          val candidateStart = bestBeat.coerceIn(prevEnd, maxAllowedStart)
          if (candidateStart != comp.startMs && abs(candidateStart - bestBeat) <= 10L) {
            val newEnd = max(currentEnd, candidateStart + minDurationMs).coerceAtMost(maxAllowedEnd)
            val shift = candidateStart - comp.startMs
            val shiftedWords =
              comp.wordMappings.map { wt ->
                val ws = (wt.startMs + shift).coerceIn(candidateStart, newEnd - 40L)
                val we = (wt.endMs + shift).coerceIn(ws + 40L, newEnd)
                wt.copy(startMs = ws, endMs = we)
              }
            result.add(
              comp.copy(
                startMs = candidateStart,
                endMs = newEnd,
                wordMappings = shiftedWords,
              )
            )
            snappedCount++
            continue
          }
        }

        result.add(comp.copy(startMs = currentStart, endMs = currentEnd))
      }

      return result to snappedCount
    }
  }
}

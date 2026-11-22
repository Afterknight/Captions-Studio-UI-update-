package com.example.captionstudio.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.captionstudio.ai.GeminiApiClient
import com.example.captionstudio.ai.GeminiApiResult
import com.example.captionstudio.data.local.AppDatabase
import com.example.captionstudio.data.local.ProjectRepository
import com.example.captionstudio.data.preferences.SettingsRepository
import com.example.captionstudio.data.security.SecureApiKeyStore
import com.example.captionstudio.domain.model.AnimationDefaults
import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.ConnectionStatus
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.ElementStylePatch
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.ExportPreferences
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.FontRoleOverrides
import com.example.captionstudio.domain.model.GeminiSettings
import com.example.captionstudio.domain.model.GeminiUsageMode
import com.example.captionstudio.domain.model.HierarchyLevel
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
import com.example.captionstudio.presets.PresetCatalog
import com.example.captionstudio.presets.StyleDeepLinkCodec
import com.example.captionstudio.typography.CuratedFontPairing
import com.example.captionstudio.typography.FontRegistry
import com.example.captionstudio.ui.components.clearVideoThumbnailCache
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AiConfirmationAction {
  COMPOSE_CAPTIONS,
  TRANSCRIBE_AUDIO,
}

data class PendingAiConfirmation(
  val modelDisplayName: String,
  val isPaidOrRestricted: Boolean,
  val includesFrames: Boolean,
  val tokenBudget: Int,
  val frameCount: Int,
  val action: AiConfirmationAction = AiConfirmationAction.COMPOSE_CAPTIONS,
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

  private val db = AppDatabase.getInstance(application)
  private val projectRepo = ProjectRepository(db.projectDao())
  private val settingsRepo = SettingsRepository(application)
  private val keyStore = SecureApiKeyStore(application)
  private val speechManager = SpeechTranscriptionManager(application)
  private val autoTranscriber = VideoAutoTranscriber(application)

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

  val geminiSettings: StateFlow<GeminiSettings> =
    settingsRepo.geminiSettings.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      GeminiSettings(),
    )

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

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _pendingAiConfirmation = MutableStateFlow<PendingAiConfirmation?>(null)
  val pendingAiConfirmation: StateFlow<PendingAiConfirmation?> = _pendingAiConfirmation.asStateFlow()

  private val _hasSavedApiKey = MutableStateFlow(keyStore.hasApiKey())
  val hasSavedApiKey: StateFlow<Boolean> = _hasSavedApiKey.asStateFlow()

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

  init {
    refreshVoicePackStatuses()
  }

  // Undo / Redo history stacks
  private val undoStack = ArrayDeque<CaptionProject>()
  private val redoStack = ArrayDeque<CaptionProject>()
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
      _activeProject.value = cached
      _transcribedSegments.value =
        if (cached.wordTimings.isNotEmpty()) {
          VoskVideoTranscriptionService.segmentRecognizedWords(cached.wordTimings)
        } else {
          emptyList()
        }
      _playheadMs.value = 0L
      val firstComp = cached.compositions.firstOrNull()
      _selectedCompositionId.value = firstComp?.id
      _selectedElementId.value = firstComp?.elements?.firstOrNull()?.id
    }
    viewModelScope.launch {
      val loaded = projectRepo.getProject(projectId)
      undoStack.clear()
      redoStack.clear()
      refreshUndoFlags()
      _activeProject.value = loaded
      _transcribedSegments.value =
        if (loaded != null && loaded.wordTimings.isNotEmpty()) {
          VoskVideoTranscriptionService.segmentRecognizedWords(loaded.wordTimings)
        } else {
          emptyList()
        }
      _playheadMs.value = 0L
      val firstComp = loaded?.compositions?.firstOrNull()
      _selectedCompositionId.value = firstComp?.id
      _selectedElementId.value = firstComp?.elements?.firstOrNull()?.id
    }
  }

  suspend fun importVideoAndCreateProject(uri: Uri, title: String): String {
    _isBusy.value = true
    return try {
      val ctx = getApplication<Application>()
      val meta = VideoAnalysisService.extractMetadata(ctx, uri)
      val analysis = VideoAnalysisService.analyzeLocally(ctx, uri, meta)
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
        val autoUseOnline =
          geminiSettings.value.enabled &&
            geminiSettings.value.usageMode == GeminiUsageMode.GEMINI_ASSISTED &&
            !geminiSettings.value.confirmBeforeRequest &&
            keyStore.hasApiKey() &&
            geminiSettings.value.selectedModelName.isNotBlank()
        viewModelScope.launch {
          runAutoTranscribeInternal(useOnline = autoUseOnline, autoCompose = true)
        }
      }
      created.id
    } finally {
      _isBusy.value = false
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
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val ctx = getApplication<Application>()
        val meta = VideoAnalysisService.extractMetadata(ctx, uri)
        val analysis = VideoAnalysisService.analyzeLocally(ctx, uri, meta)
        commitMutation(
          current.copy(
            videoUriString = uri.toString(),
            metadata = meta,
            analysisReport = analysis,
          )
        )
        _statusBanner.value = analysis.summary
        if (meta.hasAudio && current.transcriptText.isBlank()) {
          runAutoTranscribeInternal(useOnline = false, autoCompose = true)
        }
      } finally {
        _isBusy.value = false
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
    refreshUndoFlags()
    viewModelScope.launch { projectRepo.saveProject(restored) }
  }

  fun redo() {
    val current = _activeProject.value ?: return
    if (redoStack.isEmpty()) return
    undoStack.addLast(current)
    val restored = redoStack.removeLast()
    _activeProject.value = restored
    _transcribedSegments.value = VoskVideoTranscriptionService.segmentRecognizedWords(restored.wordTimings)
    refreshUndoFlags()
    viewModelScope.launch { projectRepo.saveProject(restored) }
  }

  private fun commitMutation(updated: CaptionProject, recordHistory: Boolean = true) {
    val current = _activeProject.value
    if (recordHistory && current != null && current != updated) {
      undoStack.addLast(current)
      while (undoStack.size > 30) undoStack.removeFirst()
      redoStack.clear()
      refreshUndoFlags()
    }
    _activeProject.value = updated
    viewModelScope.launch { projectRepo.saveProject(updated) }
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
    autoTranscriber.cancel()
  }

  // --- Composition Generation ---

  fun requestGenerateCompositions(forceLocalOnly: Boolean = false) {
    val project = _activeProject.value ?: return
    if (project.transcriptText.isBlank()) {
      _statusBanner.value = "Enter, dictate, or import a transcript before generating captions."
      return
    }

    val settings = geminiSettings.value
    val canUseGemini =
      !forceLocalOnly &&
        settings.enabled &&
        settings.usageMode != GeminiUsageMode.OFFLINE &&
        keyStore.hasApiKey() &&
        settings.selectedModelName.isNotBlank()

    if (canUseGemini && settings.confirmBeforeRequest) {
      val modelInfo =
        settings.cachedModels.firstOrNull { it.name == settings.selectedModelName }
      _pendingAiConfirmation.value =
        PendingAiConfirmation(
          modelDisplayName = modelInfo?.displayName ?: settings.selectedModelName.removePrefix("models/"),
          isPaidOrRestricted = modelInfo?.isLikelyPaidOrRestricted == true,
          includesFrames =
            settings.allowFrameSharing &&
              !settings.transcriptOnly &&
              project.videoUriString.isNotBlank() &&
              (modelInfo?.supportsImageInput != false),
          tokenBudget = settings.tokenBudget,
          frameCount = if (settings.allowFrameSharing && !settings.transcriptOnly) 8 else 0,
        )
      return
    }

    executeCompositionGeneration(useGemini = canUseGemini)
  }

  fun requestAutoTranscribeVideo(preferOnline: Boolean) {
    val project = _activeProject.value ?: return
    if (project.videoUriString.isBlank()) {
      _statusBanner.value = "Attach a video before running automatic audio transcription."
      return
    }
    if (preferOnline) {
      val settings = geminiSettings.value
      val key = keyStore.getApiKey()
      if (!settings.enabled || key.isNullOrBlank() || settings.selectedModelName.isBlank()) {
        _statusBanner.value = "Configure & enable Gemini in AI Settings to run online AI transcription."
        return
      }
      if (settings.confirmBeforeRequest) {
        val modelInfo =
          settings.cachedModels.firstOrNull { it.name == settings.selectedModelName }
        _pendingAiConfirmation.value =
          PendingAiConfirmation(
            modelDisplayName = modelInfo?.displayName ?: settings.selectedModelName.removePrefix("models/"),
            isPaidOrRestricted = modelInfo?.isLikelyPaidOrRestricted == true,
            includesFrames = false,
            tokenBudget = 4096,
            frameCount = 0,
            action = AiConfirmationAction.TRANSCRIBE_AUDIO,
          )
        return
      }
    }
    viewModelScope.launch {
      runAutoTranscribeInternal(useOnline = preferOnline, autoCompose = true)
    }
  }

  private suspend fun runAutoTranscribeInternal(useOnline: Boolean, autoCompose: Boolean) {
    val project = _activeProject.value ?: return
    if (project.videoUriString.isBlank()) return
    autoTranscriber.clearError()

    val settings = geminiSettings.value
    val apiKey = if (useOnline) keyStore.getApiKey() else null
    val result =
      autoTranscriber.transcribeVideo(
        videoUri = Uri.parse(project.videoUriString),
        languageTag = speechLanguageTag.value,
        useOnlineGemini = useOnline,
        geminiApiKey = apiKey,
        geminiModelName = settings.selectedModelName,
      )

    val latest = _activeProject.value ?: return
    when (result) {
      is AutoTranscribeResult.Success -> {
        val segments =
          result.segments.ifEmpty {
            VoskVideoTranscriptionService.segmentRecognizedWords(result.wordTimings)
          }
        _transcribedSegments.value = segments

        val preset = PresetCatalog.findById(latest.presetId)
        val comps =
          if (autoCompose) {
            val raw =
              if (segments.isNotEmpty()) {
                LocalCompositionEngine.generateCompositionsFromPhrases(
                  phrases = segments.map { it.words },
                  preset = preset,
                  analysisReport = latest.analysisReport,
                  fontOverrides = latest.fontOverrides,
                )
              } else {
                LocalCompositionEngine.generateCompositions(
                  transcript = result.transcript,
                  wordTimings = result.wordTimings,
                  totalDurationMs =
                    max(latest.metadata.durationMs, result.wordTimings.lastOrNull()?.endMs ?: 4000L),
                  preset = preset,
                  analysisReport = latest.analysisReport,
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

        commitMutation(
          latest.copy(
            transcriptText = result.transcript,
            wordTimings = result.wordTimings,
            compositions = comps,
          )
        )
        _selectedCompositionId.value = comps.firstOrNull()?.id
        _selectedElementId.value = comps.firstOrNull()?.elements?.firstOrNull()?.id
        _playheadMs.value = comps.firstOrNull()?.startMs ?: 0L
        _statusBanner.value =
          "${result.engineLabel}: ${segments.size} text segments & ${comps.size} kinetic captions ready."
      }
      is AutoTranscribeResult.NoSpeechDetected -> {
        _statusBanner.value = result.message
      }
      is AutoTranscribeResult.Unavailable -> {
        _statusBanner.value = result.reason
      }
    }
  }

  fun dismissAiConfirmation() {
    _pendingAiConfirmation.value = null
  }

  fun confirmAiGeneration() {
    val pending = _pendingAiConfirmation.value
    _pendingAiConfirmation.value = null
    if (pending?.action == AiConfirmationAction.TRANSCRIBE_AUDIO) {
      viewModelScope.launch { runAutoTranscribeInternal(useOnline = true, autoCompose = true) }
    } else {
      executeCompositionGeneration(useGemini = true)
    }
  }

  private fun executeCompositionGeneration(useGemini: Boolean) {
    val project = _activeProject.value ?: return
    val preset = PresetCatalog.findById(project.presetId)

    viewModelScope.launch {
      _isBusy.value = true
      try {
        val localComps =
          LocalCompositionEngine.generateCompositions(
            transcript = project.transcriptText,
            wordTimings = project.wordTimings,
            totalDurationMs = project.metadata.durationMs,
            preset = preset,
            analysisReport = project.analysisReport,
            fontOverrides = project.fontOverrides,
          )

        var finalComps = localComps
        var bannerMsg = "Generated ${localComps.size} kinetic compositions offline."

        if (useGemini) {
          val apiKey = keyStore.getApiKey()
          val settings = geminiSettings.value
          if (apiKey.isNullOrBlank()) {
            bannerMsg = "API key unavailable; fell back to local kinetic composition engine."
          } else {
            val frames =
              if (settings.allowFrameSharing &&
                  !settings.transcriptOnly &&
                  project.videoUriString.isNotBlank()
              ) {
                VideoAnalysisService.sampleTimestampedFramesAsJpegBase64(
                  context = getApplication(),
                  videoUri = Uri.parse(project.videoUriString),
                  durationMs = project.metadata.durationMs,
                  maxFrames = 8,
                )
              } else {
                emptyList()
              }

            val result =
              GeminiApiClient.generateKineticCompositions(
                apiKey = apiKey,
                modelName = settings.selectedModelName,
                transcript = project.transcriptText,
                wordTimings = project.wordTimings,
                preset = preset,
                analysisReport = project.analysisReport,
                tokenBudget = settings.tokenBudget,
                timestampedFrames = frames,
              )

            when (result) {
              is GeminiApiResult.Success -> {
                finalComps = result.data
                bannerMsg =
                  "Generated ${result.data.size} vision-directed captions via ${settings.selectedModelName.removePrefix("models/")}."
              }
              is GeminiApiResult.Error -> {
                bannerMsg =
                  if (settings.usageMode == GeminiUsageMode.HYBRID) {
                    "Gemini skipped (${result.message}). Using local hybrid compositions."
                  } else {
                    "${result.message} Using local fallback compositions."
                  }
              }
            }
          }
        }

        val validated =
          CompositionValidator.validateAndRepair(
            context = getApplication(),
            compositions = finalComps,
            videoWidthPx = project.metadata.effectiveWidth,
            videoHeightPx = project.metadata.effectiveHeight,
          )

        commitMutation(project.copy(compositions = validated))
        _selectedCompositionId.value = validated.firstOrNull()?.id
        _selectedElementId.value = validated.firstOrNull()?.elements?.firstOrNull()?.id
        _playheadMs.value = validated.firstOrNull()?.startMs ?: 0L
        _statusBanner.value = bannerMsg
      } finally {
        _isBusy.value = false
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
    val updated =
      project.compositions.map { comp ->
        if (comp.id != compId) {
          comp
        } else {
          val nextEnable = !comp.hasBehindSubjectElements()
          val nextLayer = if (nextEnable) DepthLayer.BEHIND_SUBJECT else DepthLayer.IN_FRONT
          val updatedEls =
            comp.elements.map { el ->
              if (el.hierarchyLevel == HierarchyLevel.DOMINANT) {
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

          if (patch.safeRegion != null) {
            comp.copy(
              region = patch.safeRegion,
              anchorXFraction = patch.safeRegion.defaultAnchorX,
              anchorYFraction = patch.safeRegion.defaultAnchorY,
              backgroundMaskEnabled = nextBgMask,
              depthLayer = nextCompDepth,
              elements = newElements,
            )
          } else {
            comp.copy(
              backgroundMaskEnabled = nextBgMask,
              depthLayer = nextCompDepth,
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

  // --- Gemini Settings & Keystore ---

  fun saveApiKey(rawKey: String) {
    val ok = keyStore.saveApiKey(rawKey)
    _hasSavedApiKey.value = keyStore.hasApiKey()
    viewModelScope.launch {
      if (ok) {
        settingsRepo.setConnectionStatus(
          ConnectionStatus.CONFIGURED_UNVERIFIED,
          "API key encrypted in Android Keystore. Tap Refresh Models or Test Connection.",
        )
        refreshAvailableModels()
      } else {
        settingsRepo.setConnectionStatus(ConnectionStatus.ERROR, "Could not encrypt empty key.")
      }
    }
  }

  fun removeApiKey() {
    keyStore.removeApiKey()
    _hasSavedApiKey.value = false
    viewModelScope.launch {
      settingsRepo.updateCachedModels(emptyList())
      settingsRepo.setSelectedModel("")
      settingsRepo.setConnectionStatus(ConnectionStatus.NOT_CONFIGURED, "API key removed.")
    }
  }

  fun refreshAvailableModels() {
    val key = keyStore.getApiKey()
    if (key.isNullOrBlank()) {
      viewModelScope.launch {
        settingsRepo.setConnectionStatus(
          ConnectionStatus.NOT_CONFIGURED,
          "Save a Gemini API key first to discover models.",
        )
      }
      return
    }
    viewModelScope.launch {
      settingsRepo.setConnectionStatus(ConnectionStatus.TESTING, "Fetching models.list...")
      when (val res = GeminiApiClient.fetchAvailableModels(key)) {
        is GeminiApiResult.Success -> {
          settingsRepo.updateCachedModels(res.data)
          settingsRepo.setConnectionStatus(
            ConnectionStatus.CONFIGURED_UNVERIFIED,
            "Discovered ${res.data.size} generateContent models.",
          )
        }
        is GeminiApiResult.Error -> {
          settingsRepo.setConnectionStatus(ConnectionStatus.ERROR, res.message)
        }
      }
    }
  }

  fun testGeminiConnection() {
    val key = keyStore.getApiKey()
    val model = geminiSettings.value.selectedModelName
    if (key.isNullOrBlank()) {
      viewModelScope.launch {
        settingsRepo.setConnectionStatus(ConnectionStatus.NOT_CONFIGURED, "No API key stored.")
      }
      return
    }
    viewModelScope.launch {
      settingsRepo.setConnectionStatus(ConnectionStatus.TESTING, "Testing $model...")
      when (val res = GeminiApiClient.testConnection(key, model)) {
        is GeminiApiResult.Success -> {
          settingsRepo.setConnectionStatus(ConnectionStatus.CONNECTED, res.data)
        }
        is GeminiApiResult.Error -> {
          settingsRepo.setConnectionStatus(ConnectionStatus.ERROR, res.message)
        }
      }
    }
  }

  fun setGeminiEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setGeminiEnabled(enabled) }
  }

  fun setUsageMode(mode: GeminiUsageMode) {
    viewModelScope.launch { settingsRepo.setUsageMode(mode) }
  }

  fun setSelectedModel(modelName: String) {
    viewModelScope.launch { settingsRepo.setSelectedModel(modelName) }
  }

  fun setConfirmBeforeRequest(confirm: Boolean) {
    viewModelScope.launch { settingsRepo.setConfirmBeforeRequest(confirm) }
  }

  fun setTokenBudget(budget: Int) {
    viewModelScope.launch { settingsRepo.setTokenBudget(budget) }
  }

  fun setTranscriptOnly(transcriptOnly: Boolean) {
    viewModelScope.launch {
      settingsRepo.setTranscriptOnly(transcriptOnly)
      if (transcriptOnly && geminiSettings.value.allowFrameSharing) {
        settingsRepo.setAllowFrameSharing(false)
      }
    }
  }

  fun setAllowFrameSharing(allowed: Boolean) {
    viewModelScope.launch {
      settingsRepo.setAllowFrameSharing(allowed)
      if (allowed && geminiSettings.value.transcriptOnly) {
        settingsRepo.setTranscriptOnly(false)
      }
    }
  }

  fun setPreviewQualityMode(mode: PreviewQualityMode) {
    viewModelScope.launch { settingsRepo.setPreviewQualityMode(mode) }
  }

  fun setSpeechLanguageTag(tag: String) {
    viewModelScope.launch { settingsRepo.setSpeechLanguageTag(tag) }
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

  fun quickEditWord(
    wordIndex: Int,
    newText: String,
    emphasis: HierarchyLevel,
    behindSubject: Boolean,
  ) {
    val project = _activeProject.value ?: return
    if (wordIndex !in project.wordTimings.indices) return
    val clean = newText.trim()
    if (clean.isEmpty()) return

    val oldWord = project.wordTimings[wordIndex]
    val newEmphasisScore =
      when (emphasis) {
        HierarchyLevel.DOMINANT -> 0.95f
        HierarchyLevel.EXPRESSIVE -> 0.82f
        else -> 0.15f
      }
    val updatedTimings =
      project.wordTimings.toMutableList().apply {
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
            originalText = replacedWordMappings.joinToString(" ") { it.word }.ifBlank { comp.originalText },
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
    if (wordIndex !in project.wordTimings.indices) return
    val removed = project.wordTimings[wordIndex]
    val updatedTimings = project.wordTimings.toMutableList().apply { removeAt(wordIndex) }
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

  // --- Export (Background ForegroundService) ---

  fun updateExportPreferences(prefs: ExportPreferences) {
    val project = _activeProject.value ?: return
    commitMutation(project.copy(exportPreferences = prefs), recordHistory = false)
  }

  fun startExport() {
    val project = _activeProject.value ?: return
    val uri =
      if (project.videoUriString.isBlank()) Uri.EMPTY else Uri.parse(project.videoUriString)
    VideoExportCoordinator.startForegroundExport(
      context = getApplication(),
      videoUri = uri,
      compositions = project.compositions,
      presetId = project.presetId,
      exportPrefs = project.exportPreferences,
      outputTitle = project.title,
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
    autoTranscriber.cancel()
    // Do NOT cancel active VideoExportForegroundService here so background exports complete reliably!
  }
}

package com.example.captionstudio.data.local

import com.example.captionstudio.domain.model.AnimationDefaults
import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.AspectRatioOption
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.CompositionStyleVariant
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.DesignerFrameStyle
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.ExportFrameRate
import com.example.captionstudio.domain.model.ExportPreferences
import com.example.captionstudio.domain.model.ExportQualityMode
import com.example.captionstudio.domain.model.ExportResolution
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.FontRoleOverrides
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.KaraokeHighlightMode
import com.example.captionstudio.domain.model.LayerMotionEffect
import com.example.captionstudio.domain.model.LetterAnimationStyle
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.ShotVisualAnalysis
import com.example.captionstudio.domain.model.SpatialLayoutMode
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextAlignment
import com.example.captionstudio.domain.model.TextSurfaceStyle
import com.example.captionstudio.domain.model.VectorFlourish
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.domain.model.VideoMetadata
import com.example.captionstudio.domain.model.WordTiming
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class ProjectRepository(private val projectDao: ProjectDao) {

  val allProjects: Flow<List<CaptionProject>> =
    projectDao.observeAllProjects().map { entities -> entities.map { decodeProjectSafely(it) } }

  suspend fun getAllProjectsSnapshot(): List<CaptionProject> =
    projectDao.getAllProjectsSnapshot().map { decodeProjectSafely(it) }

  fun observeProject(projectId: String): Flow<CaptionProject?> =
    projectDao.observeProjectById(projectId).map { entity -> entity?.let { decodeProjectSafely(it) } }

  suspend fun getProject(projectId: String): CaptionProject? =
    projectDao.getProjectById(projectId)?.let { decodeProjectSafely(it) }

  /**
   * Atomically serializes all validated JSON payloads in memory before writing to Room.
   * If encoding fails for any reason (including OOM), the existing Room row is left untouched.
   */
  suspend fun saveProject(project: CaptionProject): Boolean {
    val sanitized = sanitizeProject(project.copy(updatedAtMs = System.currentTimeMillis()))
    val entity =
      try {
        encodeProjectToEntity(sanitized)
      } catch (_: Throwable) {
        return false
      }
    projectDao.upsertProject(entity)
    return true
  }

  suspend fun createProject(
    title: String,
    videoUriString: String,
    metadata: VideoMetadata,
    presetId: String = "editorial",
    initialTranscript: String = "",
    initialWordTimings: List<WordTiming> = emptyList(),
    initialCompositions: List<CaptionComposition> = emptyList(),
    analysisReport: VideoAnalysisReport = VideoAnalysisReport(),
  ): CaptionProject {
    val now = System.currentTimeMillis()
    val project =
      sanitizeProject(
        CaptionProject(
          id = UUID.randomUUID().toString(),
          title = title.ifBlank { "Untitled Studio Project" },
          videoUriString = videoUriString,
          metadata = metadata,
          presetId = presetId,
          transcriptText = initialTranscript,
          wordTimings = initialWordTimings,
          compositions = initialCompositions,
          analysisReport = analysisReport,
          fontOverrides = FontRoleOverrides(),
          animationDefaults = AnimationDefaults(),
          exportPreferences = ExportPreferences(),
          createdAtMs = now,
          updatedAtMs = now,
        )
      )
    projectDao.upsertProject(encodeProjectToEntity(project))
    return project
  }

  suspend fun renameProject(projectId: String, newTitle: String) {
    val trimmed = newTitle.trim()
    if (trimmed.isNotEmpty()) {
      projectDao.renameProject(projectId, trimmed, System.currentTimeMillis())
    }
  }

  suspend fun deleteProject(projectId: String) {
    projectDao.deleteProjectById(projectId)
  }

  companion object {
    @OptIn(ExperimentalSerializationApi::class)
    internal val json: Json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      coerceInputValues = true
      isLenient = true
      explicitNulls = false
    }

    private val defaultSafeRegions: List<SafeRegion> =
      listOf(
        SafeRegion.UPPER_CENTER,
        SafeRegion.LOWER_CENTER,
        SafeRegion.UPPER_LEFT,
        SafeRegion.UPPER_RIGHT,
      )

    internal fun encodeProjectToEntity(project: CaptionProject): ProjectEntity {
      val clean = sanitizeProject(project)
      return ProjectEntity(
        id = clean.id,
        title = clean.title,
        videoUriString = clean.videoUriString,
        metadataJson = json.encodeToString(clean.metadata),
        presetId = clean.presetId,
        transcriptText = clean.transcriptText,
        wordTimingsJson = json.encodeToString(clean.wordTimings),
        compositionsJson = json.encodeToString(clean.compositions),
        analysisReportJson = json.encodeToString(clean.analysisReport),
        fontOverridesJson = json.encodeToString(clean.fontOverrides),
        animationDefaultsJson = json.encodeToString(clean.animationDefaults),
        exportPreferencesJson = json.encodeToString(clean.exportPreferences),
        createdAtMs = clean.createdAtMs,
        updatedAtMs = clean.updatedAtMs,
      )
    }

    /**
     * Null-safe, R8-resilient project loader. Wraps every JSON decode in `try/catch (Throwable)`,
     * strips explicit `null` properties from older/interrupted payloads before decoding, recovers
     * individual compositions if one entry is malformed, and sanitizes all List & enum fields back
     * to non-null safe defaults.
     */
    internal fun decodeProjectSafely(entity: ProjectEntity): CaptionProject {
      val metadata = decodeObjectSafely(entity.metadataJson) { VideoMetadata() }
      val wordTimings = decodeListSafely<WordTiming>(entity.wordTimingsJson)
      val compositions = decodeListSafely<CaptionComposition>(entity.compositionsJson)
      val analysisReport = decodeObjectSafely(entity.analysisReportJson) { VideoAnalysisReport() }
      val fontOverrides = decodeObjectSafely(entity.fontOverridesJson) { FontRoleOverrides() }
      val animationDefaults = decodeObjectSafely(entity.animationDefaultsJson) { AnimationDefaults() }
      val exportPreferences = decodeObjectSafely(entity.exportPreferencesJson) { ExportPreferences() }

      val rawProject =
        CaptionProject(
          id = safeNonNull(entity.id, UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
          title = safeNonNull(entity.title, "Untitled Studio Project").ifBlank { "Untitled Studio Project" },
          videoUriString = safeNonNull(entity.videoUriString, ""),
          metadata = metadata,
          presetId = safeNonNull(entity.presetId, "editorial").ifBlank { "editorial" },
          transcriptText = safeNonNull(entity.transcriptText, ""),
          wordTimings = wordTimings,
          compositions = compositions,
          analysisReport = analysisReport,
          fontOverrides = fontOverrides,
          animationDefaults = animationDefaults,
          exportPreferences = exportPreferences,
          createdAtMs = entity.createdAtMs,
          updatedAtMs = entity.updatedAtMs,
        )
      return sanitizeProject(rawProject)
    }

    private inline fun <reified T> decodeObjectSafely(
      rawJson: String?,
      defaultProvider: () -> T,
    ): T {
      if (rawJson.isNullOrBlank()) return defaultProvider()
      return try {
        val parsed = json.parseToJsonElement(rawJson)
        val cleaned = stripJsonNulls(parsed)
        json.decodeFromJsonElement<T>(cleaned)
      } catch (_: Throwable) {
        try {
          json.decodeFromString<T>(rawJson)
        } catch (_: Throwable) {
          defaultProvider()
        }
      }
    }

    private inline fun <reified T> decodeListSafely(rawJson: String?): List<T> {
      if (rawJson.isNullOrBlank()) return emptyList()
      return try {
        val parsed = json.parseToJsonElement(rawJson)
        val cleaned = stripJsonNulls(parsed)
        try {
          json.decodeFromJsonElement<List<T>>(cleaned)
        } catch (_: Throwable) {
          if (cleaned is JsonArray) {
            cleaned.mapNotNull { item ->
              try {
                json.decodeFromJsonElement<T>(item)
              } catch (_: Throwable) {
                null
              }
            }
          } else {
            emptyList()
          }
        }
      } catch (_: Throwable) {
        emptyList()
      }
    }

    /**
     * Strips explicit `null` properties from JSON objects and `null` elements from JSON arrays so
     * Kotlinx Serialization cleanly applies non-null default values for legacy/corrupted projects.
     */
    internal fun stripJsonNulls(element: JsonElement): JsonElement =
      when (element) {
        is JsonObject -> {
          val filtered = LinkedHashMap<String, JsonElement>(element.size)
          for ((key, value) in element) {
            if (value !is JsonNull) {
              filtered[key] = stripJsonNulls(value)
            }
          }
          JsonObject(filtered)
        }
        is JsonArray -> {
          JsonArray(element.filter { it !is JsonNull }.map { stripJsonNulls(it) })
        }
        else -> element
      }

    internal fun sanitizeProject(project: CaptionProject): CaptionProject {
      return project.copy(
        id = safeNonNull(project.id, UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
        title = safeNonNull(project.title, "Untitled Studio Project").ifBlank { "Untitled Studio Project" },
        videoUriString = safeNonNull(project.videoUriString, ""),
        metadata = safeNonNull(project.metadata, VideoMetadata()),
        presetId = safeNonNull(project.presetId, "editorial").ifBlank { "editorial" },
        transcriptText = safeNonNull(project.transcriptText, ""),
        wordTimings = safeList(project.wordTimings).map { sanitizeWordTiming(it) },
        compositions = safeList(project.compositions).map { sanitizeComposition(it) },
        analysisReport = sanitizeAnalysisReport(safeNonNull(project.analysisReport, VideoAnalysisReport())),
        fontOverrides = safeNonNull(project.fontOverrides, FontRoleOverrides()),
        animationDefaults = sanitizeAnimationDefaults(safeNonNull(project.animationDefaults, AnimationDefaults())),
        exportPreferences = sanitizeExportPreferences(safeNonNull(project.exportPreferences, ExportPreferences())),
      )
    }

    internal fun sanitizeWordTiming(wt: WordTiming): WordTiming {
      val safeStart = wt.startMs.coerceAtLeast(0L)
      val safeEnd = wt.endMs.coerceAtLeast(safeStart)
      return wt.copy(
        word = safeNonNull(wt.word, ""),
        startMs = safeStart,
        endMs = safeEnd,
        emphasisScore = if (wt.emphasisScore.isFinite()) wt.emphasisScore else 0f,
      )
    }

    internal fun sanitizeComposition(comp: CaptionComposition): CaptionComposition {
      val safeStart = comp.startMs.coerceAtLeast(0L)
      val safeEnd = comp.endMs.coerceAtLeast(safeStart + 100L)
      return comp.copy(
        id = safeNonNull(comp.id, UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
        startMs = safeStart,
        endMs = safeEnd,
        originalText = safeNonNull(comp.originalText, ""),
        region = safeNonNull(comp.region, SafeRegion.LOWER_CENTER),
        anchorXFraction = if (comp.anchorXFraction.isFinite()) comp.anchorXFraction.coerceIn(0f, 1f) else 0.50f,
        anchorYFraction = if (comp.anchorYFraction.isFinite()) comp.anchorYFraction.coerceIn(0f, 1f) else 0.74f,
        entryAnimation = safeNonNull(comp.entryAnimation, EntryAnimationType.SCALE_RISE),
        exitAnimation = safeNonNull(comp.exitAnimation, ExitAnimationType.FADE_DROP),
        easing = safeNonNull(comp.easing, AnimationEasingType.DECELERATE),
        elements = safeList(comp.elements).map { sanitizeElement(it) },
        wordMappings = safeList(comp.wordMappings).map { sanitizeWordTiming(it) },
        depthLayer = safeNonNull(comp.depthLayer, DepthLayer.IN_FRONT),
        styleVariant = safeNonNull(comp.styleVariant, CompositionStyleVariant.HERO_STACK),
        spatialMode = safeNonNull(comp.spatialMode, SpatialLayoutMode.STANDARD),
      )
    }

    internal fun sanitizeElement(el: CompositionElement): CompositionElement {
      return el.copy(
        id = safeNonNull(el.id, UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
        text = safeNonNull(el.text, ""),
        role = safeNonNull(el.role, FontRole.SUPPORTING),
        hierarchyLevel = safeNonNull(el.hierarchyLevel, HierarchyLevel.SUPPORTING),
        fontFamilyId = safeNonNull(el.fontFamilyId, "inter").ifBlank { "inter" },
        colorHex = safeNonNull(el.colorHex, StudioPalette.WHITE).ifBlank { StudioPalette.WHITE },
        alignment = safeNonNull(el.alignment, TextAlignment.CENTER),
        wordIndices = safeList(el.wordIndices),
        depthLayer = safeNonNull(el.depthLayer, DepthLayer.IN_FRONT),
        strokeColorHex = safeNonNull(el.strokeColorHex, StudioPalette.SHADOW).ifBlank { StudioPalette.SHADOW },
        shadowColorHex = safeNonNull(el.shadowColorHex, StudioPalette.SHADOW).ifBlank { StudioPalette.SHADOW },
        motionEffect = safeNonNull(el.motionEffect, LayerMotionEffect.NONE),
        karaokeMode = safeNonNull(el.karaokeMode, KaraokeHighlightMode.OFF),
        karaokeHighlightColorHex =
          safeNonNull(el.karaokeHighlightColorHex, StudioPalette.YELLOW).ifBlank { StudioPalette.YELLOW },
        letterAnimation = safeNonNull(el.letterAnimation, LetterAnimationStyle.NONE),
        surfaceStyle = safeNonNull(el.surfaceStyle, TextSurfaceStyle.STANDARD),
        designerFrame = safeNonNull(el.designerFrame, DesignerFrameStyle.NONE),
        flourish = safeNonNull(el.flourish, VectorFlourish.NONE),
      )
    }

    internal fun sanitizeAnalysisReport(report: VideoAnalysisReport): VideoAnalysisReport {
      val safeShots =
        safeList(report.shotAnalyses).map { shot ->
          sanitizeShotAnalysis(shot)
        }
      val safeRegions = safeList(report.safeRegionsRanked).ifEmpty { defaultSafeRegions }
      return report.copy(
        sceneChangeTimestampsMs = safeList(report.sceneChangeTimestampsMs),
        faceRegions = safeList(report.faceRegions),
        safeRegionsRanked = safeRegions,
        suggestedAnchorX = if (report.suggestedAnchorX.isFinite()) report.suggestedAnchorX.coerceIn(0f, 1f) else 0.5f,
        suggestedAnchorY = if (report.suggestedAnchorY.isFinite()) report.suggestedAnchorY.coerceIn(0f, 1f) else 0.26f,
        summary = safeNonNull(report.summary, "Local heuristic layout ready"),
        subjectCoverageFraction =
          if (report.subjectCoverageFraction.isFinite()) report.subjectCoverageFraction.coerceIn(0f, 1f) else 0f,
        shotAnalyses = safeShots,
        faceAnchorSamples = safeList(report.faceAnchorSamples),
        beatTimestampsMs = safeList(report.beatTimestampsMs),
        keepSegments = safeList(report.keepSegments),
        originalCompositionsBackup = safeList(report.originalCompositionsBackup).map { sanitizeComposition(it) },
        originalWordTimingsBackup = safeList(report.originalWordTimingsBackup).map { sanitizeWordTiming(it) },
      )
    }

    private fun sanitizeShotAnalysis(shot: ShotVisualAnalysis): ShotVisualAnalysis {
      val safeMetrics =
        @Suppress("USELESS_ELVIS")
        (shot.zoneMetrics ?: emptyMap())
      return shot.copy(
        zoneMetrics = safeMetrics,
        preferredZone = safeNonNull(shot.preferredZone, SafeRegion.LOWER_CENTER),
      )
    }

    private fun sanitizeAnimationDefaults(defaults: AnimationDefaults): AnimationDefaults {
      return defaults.copy(
        entryAnimation = safeNonNull(defaults.entryAnimation, EntryAnimationType.SCALE_RISE),
        exitAnimation = safeNonNull(defaults.exitAnimation, ExitAnimationType.FADE_DROP),
        easing = safeNonNull(defaults.easing, AnimationEasingType.DECELERATE),
      )
    }

    private fun sanitizeExportPreferences(prefs: ExportPreferences): ExportPreferences {
      return prefs.copy(
        aspectRatio = safeNonNull(prefs.aspectRatio, AspectRatioOption.PORTRAIT_9_16),
        resolution = safeNonNull(prefs.resolution, ExportResolution.RES_1080P),
        frameRate = safeNonNull(prefs.frameRate, ExportFrameRate.FPS_30),
        qualityMode = safeNonNull(prefs.qualityMode, ExportQualityMode.BALANCED),
      )
    }

    @Suppress("USELESS_ELVIS", "UNCHECKED_CAST")
    private fun <T> safeNonNull(value: T?, fallback: T): T = value ?: fallback

    @Suppress("USELESS_ELVIS")
    private fun <T> safeList(list: List<T>?): List<T> =
      (list ?: emptyList()).filterNotNull()
  }
}

package com.example.captionstudio.data.local

import com.example.captionstudio.domain.model.AnimationDefaults
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.ExportPreferences
import com.example.captionstudio.domain.model.FontRoleOverrides
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.domain.model.VideoMetadata
import com.example.captionstudio.domain.model.WordTiming
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProjectRepository(private val projectDao: ProjectDao) {

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  val allProjects: Flow<List<CaptionProject>> =
    projectDao.observeAllProjects().map { entities -> entities.map { it.toDomain() } }

  suspend fun getAllProjectsSnapshot(): List<CaptionProject> =
    projectDao.getAllProjectsSnapshot().map { it.toDomain() }

  fun observeProject(projectId: String): Flow<CaptionProject?> =
    projectDao.observeProjectById(projectId).map { it?.toDomain() }

  suspend fun getProject(projectId: String): CaptionProject? =
    projectDao.getProjectById(projectId)?.toDomain()

  suspend fun saveProject(project: CaptionProject) {
    val updated = project.copy(updatedAtMs = System.currentTimeMillis())
    projectDao.upsertProject(updated.toEntity())
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
    projectDao.upsertProject(project.toEntity())
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

  private fun ProjectEntity.toDomain(): CaptionProject {
    return CaptionProject(
      id = id,
      title = title,
      videoUriString = videoUriString,
      metadata =
        runCatching { json.decodeFromString<VideoMetadata>(metadataJson) }
          .getOrDefault(VideoMetadata()),
      presetId = presetId,
      transcriptText = transcriptText,
      wordTimings =
        runCatching { json.decodeFromString<List<WordTiming>>(wordTimingsJson) }
          .getOrDefault(emptyList()),
      compositions =
        runCatching { json.decodeFromString<List<CaptionComposition>>(compositionsJson) }
          .getOrDefault(emptyList()),
      analysisReport =
        runCatching { json.decodeFromString<VideoAnalysisReport>(analysisReportJson) }
          .getOrDefault(VideoAnalysisReport()),
      fontOverrides =
        runCatching { json.decodeFromString<FontRoleOverrides>(fontOverridesJson) }
          .getOrDefault(FontRoleOverrides()),
      animationDefaults =
        runCatching { json.decodeFromString<AnimationDefaults>(animationDefaultsJson) }
          .getOrDefault(AnimationDefaults()),
      exportPreferences =
        runCatching { json.decodeFromString<ExportPreferences>(exportPreferencesJson) }
          .getOrDefault(ExportPreferences()),
      createdAtMs = createdAtMs,
      updatedAtMs = updatedAtMs,
    )
  }

  private fun CaptionProject.toEntity(): ProjectEntity {
    return ProjectEntity(
      id = id,
      title = title,
      videoUriString = videoUriString,
      metadataJson = json.encodeToString(metadata),
      presetId = presetId,
      transcriptText = transcriptText,
      wordTimingsJson = json.encodeToString(wordTimings),
      compositionsJson = json.encodeToString(compositions),
      analysisReportJson = json.encodeToString(analysisReport),
      fontOverridesJson = json.encodeToString(fontOverrides),
      animationDefaultsJson = json.encodeToString(animationDefaults),
      exportPreferencesJson = json.encodeToString(exportPreferences),
      createdAtMs = createdAtMs,
      updatedAtMs = updatedAtMs,
    )
  }
}

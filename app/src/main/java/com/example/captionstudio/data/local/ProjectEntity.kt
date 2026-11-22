package com.example.captionstudio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey val id: String,
  val title: String,
  val videoUriString: String,
  val metadataJson: String,
  val presetId: String,
  val transcriptText: String,
  val wordTimingsJson: String,
  val compositionsJson: String,
  val analysisReportJson: String,
  val fontOverridesJson: String,
  val animationDefaultsJson: String,
  val exportPreferencesJson: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

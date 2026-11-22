package com.example.captionstudio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
  @Query("SELECT * FROM projects ORDER BY updatedAtMs DESC")
  fun observeAllProjects(): Flow<List<ProjectEntity>>

  @Query("SELECT * FROM projects WHERE id = :projectId")
  fun observeProjectById(projectId: String): Flow<ProjectEntity?>

  @Query("SELECT * FROM projects WHERE id = :projectId")
  suspend fun getProjectById(projectId: String): ProjectEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProject(project: ProjectEntity)

  @Query("UPDATE projects SET title = :title, updatedAtMs = :updatedAtMs WHERE id = :projectId")
  suspend fun renameProject(projectId: String, title: String, updatedAtMs: Long)

  @Query("DELETE FROM projects WHERE id = :projectId")
  suspend fun deleteProjectById(projectId: String)
}

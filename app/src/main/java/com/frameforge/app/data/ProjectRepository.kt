package com.frameforge.app.data
import kotlinx.coroutines.flow.Flow
import java.util.UUID
class ProjectRepository(private val dao: ProjectDao, private val editHistoryDao: EditOperationDao? = null) {
    val projects: Flow<List<ProjectEntity>> = dao.observeProjects()
    suspend fun create(name: String, type: String, ratio: String) {
        val cleanName = name.trim().ifBlank { "Untitled ${type.lowercase()} project" }
        val now = System.currentTimeMillis()
        dao.upsert(ProjectEntity(UUID.randomUUID().toString(), cleanName, type, ratio, now, now))
    }
    suspend fun delete(id: String) {
        editHistoryDao?.deleteForProject(id)
        dao.delete(id)
    }
    suspend fun duplicate(project: ProjectEntity) {
        val now = System.currentTimeMillis()
        dao.upsert(project.copy(id = UUID.randomUUID().toString(), name = "${project.name} copy", createdAt = now, updatedAt = now))
    }
    suspend fun rename(id: String, name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotEmpty()) dao.rename(id, cleanName, System.currentTimeMillis())
    }
    suspend fun updateAspectRatio(id: String, aspectRatio: String) {
        if (aspectRatio.isNotBlank()) dao.updateAspectRatio(id, aspectRatio, System.currentTimeMillis())
    }
}

package com.frameforge.app.data
import kotlinx.coroutines.flow.Flow
import java.util.UUID
class ProjectRepository(private val dao: ProjectDao) {
    val projects: Flow<List<ProjectEntity>> = dao.observeProjects()
    suspend fun create(name: String, type: String, ratio: String) {
        val cleanName = name.trim().ifBlank { "Untitled ${type.lowercase()} project" }
        val now = System.currentTimeMillis()
        dao.upsert(ProjectEntity(UUID.randomUUID().toString(), cleanName, type, ratio, now, now))
    }
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun rename(id: String, name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotEmpty()) dao.rename(id, cleanName, System.currentTimeMillis())
    }
}

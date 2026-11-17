package com.frameforge.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Stores edit instructions as an operation history. This is the persistence
 * layer for the shared non-destructive photo/video editing engine.
 */
class EditHistoryRepository(private val dao: EditOperationDao) {
    fun operations(projectId: String): Flow<List<EditOperationEntity>> =
        dao.observeOperations(projectId)

    suspend fun record(projectId: String, type: String, payload: String) {
        require(projectId.isNotBlank()) { "A project is required" }
        require(type.isNotBlank()) { "An operation type is required" }
        val sequence = dao.nextSequence(projectId)
        dao.discardRedoBranch(projectId)
        dao.insert(
            EditOperationEntity(
                operationId = UUID.randomUUID().toString(),
                projectId = projectId,
                sequence = sequence,
                operationType = type,
                payload = payload,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun undo(projectId: String): Boolean = dao.undoLatest(projectId) > 0
    suspend fun redo(projectId: String): Boolean = dao.redoNext(projectId) > 0
    suspend fun appliedOperationCount(projectId: String): Int = dao.appliedCount(projectId)
}

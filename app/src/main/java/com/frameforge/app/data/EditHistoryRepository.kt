package com.frameforge.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Persistence for general edit instructions and state-restoring photo snapshots. */
class EditHistoryRepository(private val dao: EditOperationDao) {
    fun operations(projectId: String): Flow<List<EditOperationEntity>> = dao.observeOperations(projectId)

    fun photoHistory(projectId: String): Flow<List<EditOperationEntity>> = dao.observePhotoHistory(projectId)

    suspend fun record(projectId: String, type: String, payload: String) {
        require(projectId.isNotBlank()) { "A project is required" }
        require(type.isNotBlank()) { "An operation type is required" }
        dao.appendOperation(projectId, type, payload, UUID.randomUUID().toString(), System.currentTimeMillis())
    }

    suspend fun clearPhotoHistory(projectId: String) {
        require(projectId.isNotBlank()) { "A project is required" }
        dao.deletePhotoSnapshots(projectId)
    }

    internal suspend fun recordPhotoSnapshot(projectId: String, snapshot: PhotoEditSnapshot) {
        require(projectId.isNotBlank()) { "A project is required" }
        dao.appendPhotoSnapshot(
            projectId = projectId,
            payload = PhotoEditSnapshotCodec.encode(snapshot),
            operationId = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis()
        )
    }

    internal suspend fun loadPhotoSnapshot(projectId: String, sourceUri: String): PhotoEditSnapshot? {
        require(projectId.isNotBlank()) { "A project is required" }
        val latest = dao.latestAppliedPhotoSnapshot(projectId) ?: return null
        val snapshot = decodePhotoSnapshotForSource(latest.payload, sourceUri)
        if (snapshot == null) dao.deletePhotoSnapshots(projectId)
        return snapshot
    }

    internal suspend fun undoPhoto(projectId: String): PhotoEditSnapshot? =
        dao.undoPhotoSnapshot(projectId)?.payload?.let(PhotoEditSnapshotCodec::decode)

    internal suspend fun redoPhoto(projectId: String): PhotoEditSnapshot? =
        dao.redoPhotoSnapshot(projectId)?.payload?.let(PhotoEditSnapshotCodec::decode)

    suspend fun undo(projectId: String): Boolean = dao.undoLatest(projectId) > 0
    suspend fun redo(projectId: String): Boolean = dao.redoNext(projectId) > 0
    suspend fun appliedOperationCount(projectId: String): Int = dao.appliedCount(projectId)
}

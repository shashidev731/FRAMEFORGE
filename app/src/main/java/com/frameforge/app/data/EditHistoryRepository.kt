package com.frameforge.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Persistence for general edit instructions and state-restoring photo snapshots. */
class EditHistoryRepository(private val dao: EditOperationDao) {
    fun operations(projectId: String): Flow<List<EditOperationEntity>> = dao.observeOperations(projectId)

    fun photoHistory(projectId: String): Flow<List<EditOperationEntity>> = dao.observePhotoHistory(projectId)

    fun photoHistory(projectId: String, sourceUri: String): Flow<List<EditOperationEntity>> =
        dao.observePhotoHistoryForSource(projectId, PhotoEditSnapshotCodec.sourceUriToken(sourceUri))

    suspend fun record(projectId: String, type: String, payload: String) {
        require(projectId.isNotBlank()) { "A project is required" }
        require(type.isNotBlank()) { "An operation type is required" }
        dao.appendOperation(projectId, type, payload, UUID.randomUUID().toString(), System.currentTimeMillis())
    }

    suspend fun clearPhotoHistory(projectId: String) {
        require(projectId.isNotBlank()) { "A project is required" }
        dao.deletePhotoSnapshots(projectId)
    }

    suspend fun clearPhotoHistory(projectId: String, sourceUri: String) {
        require(projectId.isNotBlank()) { "A project is required" }
        require(sourceUri.isNotBlank()) { "A source URI is required" }
        dao.deletePhotoSnapshotsForSource(projectId, PhotoEditSnapshotCodec.sourceUriToken(sourceUri))
    }

    internal suspend fun recordPhotoSnapshot(projectId: String, snapshot: PhotoEditSnapshot) {
        require(projectId.isNotBlank()) { "A project is required" }
        val sourceUriToken = PhotoEditSnapshotCodec.sourceUriToken(snapshot.sourceUri)
        dao.appendPhotoSnapshotForSource(
            projectId = projectId,
            sourceUriToken = sourceUriToken,
            payload = PhotoEditSnapshotCodec.encode(snapshot),
            operationId = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis()
        )
    }

    internal suspend fun loadPhotoSnapshot(projectId: String, sourceUri: String): PhotoEditSnapshot? {
        require(projectId.isNotBlank()) { "A project is required" }
        require(sourceUri.isNotBlank()) { "A source URI is required" }
        val sourceUriToken = PhotoEditSnapshotCodec.sourceUriToken(sourceUri)
        val latest = dao.latestAppliedPhotoSnapshotForSource(projectId, sourceUriToken) ?: return null
        val snapshot = decodePhotoSnapshotForSource(latest.payload, sourceUri)
        if (snapshot == null) dao.deletePhotoSnapshotsForSource(projectId, sourceUriToken)
        return snapshot
    }

    internal suspend fun undoPhoto(projectId: String): PhotoEditSnapshot? =
        dao.undoPhotoSnapshot(projectId)?.payload?.let(PhotoEditSnapshotCodec::decode)

    internal suspend fun redoPhoto(projectId: String): PhotoEditSnapshot? =
        dao.redoPhotoSnapshot(projectId)?.payload?.let(PhotoEditSnapshotCodec::decode)

    internal suspend fun undoPhoto(projectId: String, sourceUri: String): PhotoEditSnapshot? {
        require(projectId.isNotBlank()) { "A project is required" }
        require(sourceUri.isNotBlank()) { "A source URI is required" }
        return dao.undoPhotoSnapshotForSource(
            projectId,
            PhotoEditSnapshotCodec.sourceUriToken(sourceUri)
        )?.payload?.let(PhotoEditSnapshotCodec::decode)
    }

    internal suspend fun redoPhoto(projectId: String, sourceUri: String): PhotoEditSnapshot? {
        require(projectId.isNotBlank()) { "A project is required" }
        require(sourceUri.isNotBlank()) { "A source URI is required" }
        return dao.redoPhotoSnapshotForSource(
            projectId,
            PhotoEditSnapshotCodec.sourceUriToken(sourceUri)
        )?.payload?.let(PhotoEditSnapshotCodec::decode)
    }

    suspend fun undo(projectId: String): Boolean = dao.undoLatest(projectId) > 0
    suspend fun redo(projectId: String): Boolean = dao.redoNext(projectId) > 0
    suspend fun appliedOperationCount(projectId: String): Int = dao.appliedCount(projectId)
}

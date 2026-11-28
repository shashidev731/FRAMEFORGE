package com.frameforge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

internal const val GENERAL_REDO_BRANCH_DELETE_SQL =
    "DELETE FROM edit_operations WHERE projectId = :projectId AND operationType NOT IN ('timeline_snapshot', 'photo_snapshot') AND isApplied = 0"

internal const val PHOTO_REDO_BRANCH_DELETE_SQL =
    "DELETE FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' AND isApplied = 0"

internal const val PHOTO_HISTORY_DELETE_SQL =
    "DELETE FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot'"

internal const val PHOTO_SOURCE_MATCH_SQL =
    "substr(payload, 1, length('PHS1|' || :sourceUriToken || '|')) = 'PHS1|' || :sourceUriToken || '|'"

internal const val PHOTO_SOURCE_REDO_BRANCH_DELETE_SQL =
    "DELETE FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' AND $PHOTO_SOURCE_MATCH_SQL AND isApplied = 0"

internal const val PHOTO_SOURCE_HISTORY_DELETE_SQL =
    "DELETE FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' AND $PHOTO_SOURCE_MATCH_SQL"

internal const val TIMELINE_REDO_BRANCH_DELETE_SQL =
    "DELETE FROM edit_operations WHERE projectId = :projectId AND operationType = 'timeline_snapshot' AND isApplied = 0"

@Dao
interface EditOperationDao {
    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType NOT IN ('timeline_snapshot', 'photo_snapshot') ORDER BY sequence ASC")
    fun observeOperations(projectId: String): Flow<List<EditOperationEntity>>

    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' ORDER BY sequence ASC")
    fun observePhotoHistory(projectId: String): Flow<List<EditOperationEntity>>

    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' AND $PHOTO_SOURCE_MATCH_SQL ORDER BY sequence ASC")
    fun observePhotoHistoryForSource(projectId: String, sourceUriToken: String): Flow<List<EditOperationEntity>>

    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' AND $PHOTO_SOURCE_MATCH_SQL AND isApplied = 1 ORDER BY sequence DESC LIMIT 1")
    suspend fun latestAppliedPhotoSnapshotForSource(projectId: String, sourceUriToken: String): EditOperationEntity?

    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' AND $PHOTO_SOURCE_MATCH_SQL AND isApplied = 0 ORDER BY sequence ASC LIMIT 1")
    suspend fun nextPhotoSnapshotRedoForSource(projectId: String, sourceUriToken: String): EditOperationEntity?

    @Query(PHOTO_SOURCE_REDO_BRANCH_DELETE_SQL)
    suspend fun discardPhotoRedoBranchForSource(projectId: String, sourceUriToken: String)

    @Query(PHOTO_SOURCE_HISTORY_DELETE_SQL)
    suspend fun deletePhotoSnapshotsForSource(projectId: String, sourceUriToken: String)

    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' AND isApplied = 1 ORDER BY sequence DESC LIMIT 1")
    suspend fun latestAppliedPhotoSnapshot(projectId: String): EditOperationEntity?

    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'photo_snapshot' AND isApplied = 0 ORDER BY sequence ASC LIMIT 1")
    suspend fun nextPhotoSnapshotRedo(projectId: String): EditOperationEntity?

    @Query(PHOTO_REDO_BRANCH_DELETE_SQL)
    suspend fun discardPhotoRedoBranch(projectId: String)

    @Query(PHOTO_HISTORY_DELETE_SQL)
    suspend fun deletePhotoSnapshots(projectId: String)

    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'timeline_snapshot' ORDER BY sequence ASC")
    fun observeTimelineHistory(projectId: String): Flow<List<EditOperationEntity>>

    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'timeline_snapshot' AND isApplied = 1 ORDER BY sequence DESC LIMIT 1")
    suspend fun latestAppliedTimeline(projectId: String): EditOperationEntity?


    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType = 'timeline_snapshot' AND isApplied = 0 ORDER BY sequence ASC LIMIT 1")
    suspend fun nextTimelineRedo(projectId: String): EditOperationEntity?


    @Query("UPDATE edit_operations SET isApplied = :applied WHERE operationId = :operationId")
    suspend fun setApplied(operationId: String, applied: Boolean)


    @Query(TIMELINE_REDO_BRANCH_DELETE_SQL)
    suspend fun discardTimelineRedo(projectId: String)



    suspend fun undoTimeline(projectId: String): EditOperationEntity? {
        val operation = latestAppliedTimeline(projectId) ?: return null
        setApplied(operation.operationId, false)
        return operation
    }


    suspend fun redoTimeline(projectId: String): EditOperationEntity? {
        val operation = nextTimelineRedo(projectId) ?: return null
        setApplied(operation.operationId, true)
        return operation
    }


    suspend fun appendTimelineSnapshot(projectId: String, payload: String, operationId: String, createdAt: Long) {
        discardTimelineRedo(projectId)
        val sequence = nextSequence(projectId)
        insert(EditOperationEntity(operationId, projectId, sequence, "timeline_snapshot", payload, createdAt))
    }

    @Transaction
    suspend fun appendPhotoSnapshot(projectId: String, payload: String, operationId: String, createdAt: Long) {
        if (latestAppliedPhotoSnapshot(projectId)?.payload == payload) return
        discardPhotoRedoBranch(projectId)
        insert(EditOperationEntity(operationId, projectId, nextSequence(projectId), "photo_snapshot", payload, createdAt))
    }

    @Transaction
    suspend fun appendPhotoSnapshotForSource(
        projectId: String,
        sourceUriToken: String,
        payload: String,
        operationId: String,
        createdAt: Long
    ) {
        if (latestAppliedPhotoSnapshotForSource(projectId, sourceUriToken)?.payload == payload) return
        discardPhotoRedoBranchForSource(projectId, sourceUriToken)
        insert(EditOperationEntity(operationId, projectId, nextSequence(projectId), "photo_snapshot", payload, createdAt))
    }

    @Transaction
    suspend fun undoPhotoSnapshotForSource(projectId: String, sourceUriToken: String): EditOperationEntity? {
        val current = latestAppliedPhotoSnapshotForSource(projectId, sourceUriToken) ?: return null
        setApplied(current.operationId, false)
        return latestAppliedPhotoSnapshotForSource(projectId, sourceUriToken)
    }

    @Transaction
    suspend fun redoPhotoSnapshotForSource(projectId: String, sourceUriToken: String): EditOperationEntity? {
        val next = nextPhotoSnapshotRedoForSource(projectId, sourceUriToken) ?: return null
        setApplied(next.operationId, true)
        return next
    }

    @Transaction
    suspend fun undoPhotoSnapshot(projectId: String): EditOperationEntity? {
        val current = latestAppliedPhotoSnapshot(projectId) ?: return null
        setApplied(current.operationId, false)
        return latestAppliedPhotoSnapshot(projectId)
    }

    @Transaction
    suspend fun redoPhotoSnapshot(projectId: String): EditOperationEntity? {
        val next = nextPhotoSnapshotRedo(projectId) ?: return null
        setApplied(next.operationId, true)
        return next
    }

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM edit_operations WHERE projectId = :projectId")
    suspend fun nextSequence(projectId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: EditOperationEntity)

    @Query("UPDATE edit_operations SET isApplied = 0 WHERE operationId = (SELECT operationId FROM edit_operations WHERE projectId = :projectId AND operationType NOT IN ('timeline_snapshot', 'photo_snapshot') AND isApplied = 1 ORDER BY sequence DESC LIMIT 1)")
    suspend fun undoLatest(projectId: String): Int

    @Query("UPDATE edit_operations SET isApplied = 1 WHERE operationId = (SELECT operationId FROM edit_operations WHERE projectId = :projectId AND operationType NOT IN ('timeline_snapshot', 'photo_snapshot') AND isApplied = 0 ORDER BY sequence ASC LIMIT 1)")
    suspend fun redoNext(projectId: String): Int

    @Query(GENERAL_REDO_BRANCH_DELETE_SQL)
    suspend fun discardRedoBranch(projectId: String)

    @Query("SELECT COUNT(*) FROM edit_operations WHERE projectId = :projectId AND operationType NOT IN ('timeline_snapshot', 'photo_snapshot') AND isApplied = 1")
    suspend fun appliedCount(projectId: String): Int

    @Query("DELETE FROM edit_operations WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)

    @Transaction
    suspend fun appendOperation(projectId: String, type: String, payload: String, operationId: String, createdAt: Long) {
        discardRedoBranch(projectId)
        val sequence = nextSequence(projectId)
        insert(EditOperationEntity(operationId, projectId, sequence, type, payload, createdAt))
    }
}

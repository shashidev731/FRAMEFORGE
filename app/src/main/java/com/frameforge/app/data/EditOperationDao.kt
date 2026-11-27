package com.frameforge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

internal const val GENERAL_REDO_BRANCH_DELETE_SQL =
    "DELETE FROM edit_operations WHERE projectId = :projectId AND operationType != 'timeline_snapshot' AND isApplied = 0"

internal const val TIMELINE_REDO_BRANCH_DELETE_SQL =
    "DELETE FROM edit_operations WHERE projectId = :projectId AND operationType = 'timeline_snapshot' AND isApplied = 0"

@Dao
interface EditOperationDao {
    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId AND operationType != 'timeline_snapshot' ORDER BY sequence ASC")
    fun observeOperations(projectId: String): Flow<List<EditOperationEntity>>

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

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM edit_operations WHERE projectId = :projectId")
    suspend fun nextSequence(projectId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: EditOperationEntity)

    @Query("UPDATE edit_operations SET isApplied = 0 WHERE operationId = (SELECT operationId FROM edit_operations WHERE projectId = :projectId AND operationType != 'timeline_snapshot' AND isApplied = 1 ORDER BY sequence DESC LIMIT 1)")
    suspend fun undoLatest(projectId: String): Int

    @Query("UPDATE edit_operations SET isApplied = 1 WHERE operationId = (SELECT operationId FROM edit_operations WHERE projectId = :projectId AND operationType != 'timeline_snapshot' AND isApplied = 0 ORDER BY sequence ASC LIMIT 1)")
    suspend fun redoNext(projectId: String): Int

    @Query(GENERAL_REDO_BRANCH_DELETE_SQL)
    suspend fun discardRedoBranch(projectId: String)

    @Query("SELECT COUNT(*) FROM edit_operations WHERE projectId = :projectId AND operationType != 'timeline_snapshot' AND isApplied = 1")
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

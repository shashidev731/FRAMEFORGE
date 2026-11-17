package com.frameforge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EditOperationDao {
    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId ORDER BY sequence ASC")
    fun observeOperations(projectId: String): Flow<List<EditOperationEntity>>

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM edit_operations WHERE projectId = :projectId")
    suspend fun nextSequence(projectId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: EditOperationEntity)

    @Query("UPDATE edit_operations SET isApplied = 0 WHERE operationId = (SELECT operationId FROM edit_operations WHERE projectId = :projectId AND isApplied = 1 ORDER BY sequence DESC LIMIT 1)")
    suspend fun undoLatest(projectId: String): Int

    @Query("UPDATE edit_operations SET isApplied = 1 WHERE operationId = (SELECT operationId FROM edit_operations WHERE projectId = :projectId AND isApplied = 0 ORDER BY sequence ASC LIMIT 1)")
    suspend fun redoNext(projectId: String): Int

    @Query("DELETE FROM edit_operations WHERE projectId = :projectId AND isApplied = 0")
    suspend fun discardRedoBranch(projectId: String)

    @Query("SELECT COUNT(*) FROM edit_operations WHERE projectId = :projectId AND isApplied = 1")
    suspend fun appliedCount(projectId: String): Int
}

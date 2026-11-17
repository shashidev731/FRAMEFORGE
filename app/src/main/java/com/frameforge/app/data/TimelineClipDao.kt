package com.frameforge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineClipDao {
    @Query("SELECT * FROM timeline_clips WHERE projectId = :projectId ORDER BY trackIndex ASC, position ASC")
    fun observeClips(projectId: String): Flow<List<TimelineClipEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM timeline_clips WHERE projectId = :projectId AND trackIndex = :trackIndex")
    suspend fun nextPosition(projectId: String, trackIndex: Int): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(clip: TimelineClipEntity)

    @Query("SELECT * FROM timeline_clips WHERE clipId = :clipId LIMIT 1")
    suspend fun find(clipId: String): TimelineClipEntity?

    @Query("DELETE FROM timeline_clips WHERE clipId = :clipId")
    suspend fun delete(clipId: String)

    @Query("UPDATE timeline_clips SET position = -1 WHERE clipId = :clipId")
    suspend fun moveToTemporary(clipId: String)

    @Query("UPDATE timeline_clips SET position = :position WHERE clipId = :clipId")
    suspend fun setPosition(clipId: String, position: Int)

    @Transaction
    suspend fun move(clipId: String, direction: Int) {
        require(direction == -1 || direction == 1)
        val current = find(clipId) ?: return
        val targetPosition = current.position + direction
        val neighbor = findAt(current.projectId, current.trackIndex, targetPosition) ?: return
        moveToTemporary(current.clipId)
        setPosition(neighbor.clipId, current.position)
        setPosition(current.clipId, neighbor.position)
    }

    @Query("SELECT * FROM timeline_clips WHERE projectId = :projectId AND trackIndex = :trackIndex AND position = :position LIMIT 1")
    suspend fun findAt(projectId: String, trackIndex: Int, position: Int): TimelineClipEntity?

    @Query("DELETE FROM timeline_clips WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

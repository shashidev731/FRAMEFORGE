package com.frameforge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

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

    @Query("SELECT * FROM timeline_clips WHERE projectId = :projectId AND trackIndex = :trackIndex ORDER BY position ASC")
    suspend fun clipsInTrack(projectId: String, trackIndex: Int): List<TimelineClipEntity>

    @Query("UPDATE timeline_clips SET timelineStartMs = :startMs WHERE clipId = :clipId")
    suspend fun setTimelineStart(clipId: String, startMs: Long)

    @Query("UPDATE timeline_clips SET gapBeforeMs = :gapMs WHERE clipId = :clipId")
    suspend fun setGapBefore(clipId: String, gapMs: Long)

    @Transaction
    suspend fun reflowTrack(projectId: String, trackIndex: Int) {
        withSequentialTimelineOffsets(clipsInTrack(projectId, trackIndex)).forEach { clip ->
            setTimelineStart(clip.clipId, clip.timelineStartMs)
        }
    }

    @Query("UPDATE timeline_clips SET position = -position - 1 WHERE projectId = :projectId AND trackIndex = :trackIndex")
    suspend fun moveTrackToTemporaryPositions(projectId: String, trackIndex: Int)

    @Transaction
    suspend fun removeAndCompact(clipId: String) {
        val clip = find(clipId) ?: return
        delete(clipId)
        val remaining = clipsInTrack(clip.projectId, clip.trackIndex)
        moveTrackToTemporaryPositions(clip.projectId, clip.trackIndex)
        remaining.forEachIndexed { index, item -> setPosition(item.clipId, index) }
        reflowTrack(clip.projectId, clip.trackIndex)
    }

    @Query("UPDATE timeline_clips SET position = -1 WHERE clipId = :clipId")
    suspend fun moveToTemporary(clipId: String)

    @Query("UPDATE timeline_clips SET position = :position WHERE clipId = :clipId")
    suspend fun setPosition(clipId: String, position: Int)

    @Query("UPDATE timeline_clips SET trimEndMs = :endMs WHERE clipId = :clipId")
    suspend fun setTrimEnd(clipId: String, endMs: Long)

    @Query("UPDATE timeline_clips SET trimStartMs = :startMs, trimEndMs = :endMs WHERE clipId = :clipId")
    suspend fun updateTrimRange(clipId: String, startMs: Long, endMs: Long)

    @Transaction
    suspend fun splitAt(clipId: String, splitAtMs: Long): Boolean {
        val current = find(clipId) ?: return false
        val end = current.trimEndMs ?: current.durationMs
        if (current.durationMs <= 0L || splitAtMs <= current.trimStartMs || splitAtMs >= end) return false
        val track = clipsInTrack(current.projectId, current.trackIndex)
        val second = current.copy(clipId = UUID.randomUUID().toString(), position = Int.MIN_VALUE, trimStartMs = splitAtMs, trimEndMs = end)
        moveTrackToTemporaryPositions(current.projectId, current.trackIndex)
        setTrimEnd(current.clipId, splitAtMs)
        insert(second)
        val ordered = buildList {
            track.forEach { item ->
                add(item)
                if (item.clipId == current.clipId) add(second)
            }
        }
        ordered.forEachIndexed { index, item -> setPosition(item.clipId, index) }
        reflowTrack(current.projectId, current.trackIndex)
        return true
    }

    @Transaction
    suspend fun move(clipId: String, direction: Int) {
        require(direction == -1 || direction == 1)
        val current = find(clipId) ?: return
        val targetPosition = current.position + direction
        val neighbor = findAt(current.projectId, current.trackIndex, targetPosition) ?: return
        moveToTemporary(current.clipId)
        setPosition(neighbor.clipId, current.position)
        setPosition(current.clipId, neighbor.position)
        reflowTrack(current.projectId, current.trackIndex)
    }

    @Query("SELECT * FROM timeline_clips WHERE projectId = :projectId AND trackIndex = :trackIndex AND position = :position LIMIT 1")
    suspend fun findAt(projectId: String, trackIndex: Int, position: Int): TimelineClipEntity?

    @Query("DELETE FROM timeline_clips WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

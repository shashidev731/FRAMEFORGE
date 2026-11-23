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

    @Query("SELECT * FROM timeline_clips WHERE projectId = :projectId ORDER BY trackIndex ASC, position ASC")
    suspend fun clipsForProject(projectId: String): List<TimelineClipEntity>

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

    @Query("UPDATE timeline_clips SET audioVolume = :volume, audioFadeInMs = :fadeInMs, audioFadeOutMs = :fadeOutMs WHERE clipId = :clipId")
    suspend fun setAudioMix(clipId: String, volume: Float, fadeInMs: Long, fadeOutMs: Long)

    @Transaction
    suspend fun reflowTrack(projectId: String, trackIndex: Int) {
        withSequentialTimelineOffsets(clipsInTrack(projectId, trackIndex)).forEach { clip ->
            setTimelineStart(clip.clipId, clip.timelineStartMs)
        }
    }

    @Transaction
    suspend fun updateGapBeforeAndReflow(clipId: String, gapMs: Long) {
        val clip = find(clipId) ?: return
        setGapBefore(clipId, gapMs.coerceAtLeast(0L))
        reflowTrack(clip.projectId, clip.trackIndex)
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
        val start = normalizedClipTrimStartMs(current)
        val end = normalizedClipTrimEndMs(current)
        if (current.durationMs <= 0L || splitAtMs <= start || splitAtMs >= end) return false
        val track = clipsInTrack(current.projectId, current.trackIndex)
        val second = current.copy(clipId = UUID.randomUUID().toString(), position = Int.MIN_VALUE, trimStartMs = splitAtMs, trimEndMs = end, gapBeforeMs = 0L)
        moveTrackToTemporaryPositions(current.projectId, current.trackIndex)
        updateTrimRange(current.clipId, start, splitAtMs)
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
    suspend fun duplicateClip(clipId: String): String? {
        val current = find(clipId) ?: return null
        val ordered = clipsInTrack(current.projectId, current.trackIndex).sortedBy { it.position }
        val duplicate = current.copy(
            clipId = UUID.randomUUID().toString(),
            position = Int.MIN_VALUE,
            gapBeforeMs = 0L,
            createdAt = System.currentTimeMillis()
        )
        moveTrackToTemporaryPositions(current.projectId, current.trackIndex)
        insert(duplicate)
        val reordered = buildList {
            ordered.forEach { clip ->
                add(clip)
                if (clip.clipId == current.clipId) add(duplicate)
            }
        }
        reordered.forEachIndexed { index, clip -> setPosition(clip.clipId, index) }
        reflowTrack(current.projectId, current.trackIndex)
        return duplicate.clipId
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

    @Transaction
    suspend fun moveToPosition(clipId: String, requestedPosition: Int) {
        val current = find(clipId) ?: return
        val ordered = clipsInTrack(current.projectId, current.trackIndex).sortedBy { it.position }
        if (ordered.size < 2) return
        val target = requestedPosition.coerceIn(0, ordered.lastIndex)
        if (current.position == target) return
        val reordered = reorderTimelineClips(ordered, clipId, target)
        moveTrackToTemporaryPositions(current.projectId, current.trackIndex)
        reordered.forEach { clip -> setPosition(clip.clipId, clip.position) }
        reflowTrack(current.projectId, current.trackIndex)
    }

    @Query("UPDATE timeline_clips SET trackIndex = :trackIndex, position = :position WHERE clipId = :clipId")
    suspend fun setTrackAndPosition(clipId: String, trackIndex: Int, position: Int)

    @Transaction
    suspend fun moveToTrack(clipId: String, targetTrackIndex: Int) {
        require(targetTrackIndex >= 0)
        val current = find(clipId) ?: return
        if (current.trackIndex == targetTrackIndex) return
        val destinationPosition = nextPosition(current.projectId, targetTrackIndex)
        moveToTemporary(clipId)
        setTrackAndPosition(clipId, targetTrackIndex, destinationPosition)
        val remaining = clipsInTrack(current.projectId, current.trackIndex).sortedBy { it.position }
        moveTrackToTemporaryPositions(current.projectId, current.trackIndex)
        remaining.forEachIndexed { index, item -> setPosition(item.clipId, index) }
        reflowTrack(current.projectId, current.trackIndex)
        reflowTrack(current.projectId, targetTrackIndex)
    }

    @Query("SELECT * FROM timeline_clips WHERE projectId = :projectId AND trackIndex = :trackIndex AND position = :position LIMIT 1")
    suspend fun findAt(projectId: String, trackIndex: Int, position: Int): TimelineClipEntity?

    @Query("DELETE FROM timeline_clips WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)


    @Transaction
    suspend fun restoreProjectClips(projectId: String, snapshot: List<TimelineClipEntity>) {
        require(snapshot.all { it.projectId == projectId }) { "Snapshot contains clips from another project" }
        deleteForProject(projectId)
        snapshot.sortedWith(compareBy<TimelineClipEntity> { it.trackIndex }.thenBy { it.position })
            .forEach { insert(it) }
    }
}

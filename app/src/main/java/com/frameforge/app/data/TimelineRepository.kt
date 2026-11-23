package com.frameforge.app.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TimelineRepository(
    private val dao: TimelineClipDao,
    private val historyDao: EditOperationDao,
    private val database: RoomDatabase
) {
    fun clips(projectId: String): Flow<List<TimelineClipEntity>> = dao.observeClips(projectId)
    fun timelineHistory(projectId: String): Flow<List<EditOperationEntity>> = historyDao.observeTimelineHistory(projectId)

    private suspend fun <T> recordSnapshot(projectId: String, action: suspend () -> T): T =
        database.withTransaction {
            val before = dao.clipsForProject(projectId)
            val result = action()
            val after = dao.clipsForProject(projectId)
            if (before != after) {
                historyDao.appendTimelineSnapshot(
                    projectId = projectId,
                    payload = TimelineSnapshotCodec.encode(before, after),
                    operationId = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis()
                )
            }
            result
        }

    suspend fun add(projectId: String, uri: String, name: String, mimeType: String, durationMs: Long = 5000L) {
        require(projectId.isNotBlank() && uri.isNotBlank())
        val track = if (mimeType.startsWith("audio/")) 1 else 0
        recordSnapshot(projectId) {
            dao.insert(
                TimelineClipEntity(
                    clipId = UUID.randomUUID().toString(),
                    projectId = projectId,
                    mediaUri = uri,
                    displayName = name,
                    mimeType = mimeType,
                    trackIndex = track,
                    position = dao.nextPosition(projectId, track),
                    durationMs = durationMs.coerceAtLeast(0L),
                    createdAt = System.currentTimeMillis()
                )
            )
            dao.reflowTrack(projectId, track)
        }
    }

    suspend fun remove(clipId: String) {
        val clip = dao.find(clipId) ?: return
        recordSnapshot(clip.projectId) { dao.removeAndCompact(clipId) }
    }

    suspend fun duplicate(clipId: String): String? {
        val clip = dao.find(clipId) ?: return null
        return recordSnapshot(clip.projectId) { dao.duplicateClip(clipId) }
    }

    suspend fun splitAt(clipId: String, splitAtMs: Long): Boolean {
        val clip = dao.find(clipId) ?: return false
        return recordSnapshot(clip.projectId) { dao.splitAt(clipId, splitAtMs) }
    }

    suspend fun updateTrim(clip: TimelineClipEntity, startMs: Long, endMs: Long) {
        require(startMs >= 0L && endMs > startMs && endMs <= clip.durationMs)
        val current = dao.find(clip.clipId) ?: return
        recordSnapshot(current.projectId) {
            dao.updateTrimRange(current.clipId, startMs, endMs)
            dao.reflowTrack(current.projectId, current.trackIndex)
        }
    }

    suspend fun setGapBefore(clipId: String, gapMs: Long) {
        val clip = dao.find(clipId) ?: return
        recordSnapshot(clip.projectId) { dao.updateGapBeforeAndReflow(clipId, gapMs.coerceAtLeast(0L)) }
    }

    suspend fun setAudioVolume(clipId: String, volume: Float) {
        val clip = dao.find(clipId) ?: return
        val normalized = normalizedClipAudioVolume(volume)
        if (normalized == clip.audioVolume) return
        recordSnapshot(clip.projectId) { dao.setAudioVolume(clipId, normalized) }
    }

    suspend fun moveEarlier(clipId: String) = move(clipId, -1)
    suspend fun moveLater(clipId: String) = move(clipId, 1)

    private suspend fun move(clipId: String, direction: Int) {
        val clip = dao.find(clipId) ?: return
        recordSnapshot(clip.projectId) { dao.move(clipId, direction) }
    }

    suspend fun moveToPosition(clipId: String, position: Int) {
        val clip = dao.find(clipId) ?: return
        recordSnapshot(clip.projectId) { dao.moveToPosition(clipId, position) }
    }

    suspend fun moveToTrack(clipId: String, trackIndex: Int) {
        val clip = dao.find(clipId) ?: return
        recordSnapshot(clip.projectId) { dao.moveToTrack(clipId, trackIndex) }
    }

    suspend fun undo(projectId: String): Boolean = database.withTransaction {
        val operation = historyDao.undoTimeline(projectId) ?: return@withTransaction false
        val snapshots = TimelineSnapshotCodec.decode(operation.payload)
        dao.restoreProjectClips(projectId, snapshots.before)
        true
    }

    suspend fun redo(projectId: String): Boolean = database.withTransaction {
        val operation = historyDao.redoTimeline(projectId) ?: return@withTransaction false
        val snapshots = TimelineSnapshotCodec.decode(operation.payload)
        dao.restoreProjectClips(projectId, snapshots.after)
        true
    }
}

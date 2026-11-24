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

    suspend fun clear(projectId: String) {
        require(projectId.isNotBlank())
        recordSnapshot(projectId) { dao.deleteForProject(projectId) }
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
        setAudioMix(clipId, volume, clip.audioFadeInMs, clip.audioFadeOutMs)
    }

    suspend fun setAudioMix(clipId: String, volume: Float, fadeInMs: Long, fadeOutMs: Long) {
        val clip = dao.find(clipId) ?: return
        val normalizedVolume = normalizedClipAudioVolume(volume)
        val (normalizedFadeIn, normalizedFadeOut) = normalizedClipAudioFades(clip.durationMs, fadeInMs, fadeOutMs)
        if (normalizedVolume == clip.audioVolume && normalizedFadeIn == clip.audioFadeInMs && normalizedFadeOut == clip.audioFadeOutMs) return
        recordSnapshot(clip.projectId) {
            dao.setAudioMix(clipId, normalizedVolume, normalizedFadeIn, normalizedFadeOut)
        }
    }

    suspend fun setSpeed(clipId: String, speed: Float) {
        val clip = dao.find(clipId) ?: return
        val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
        if (clip.speed == clampedSpeed) return
        recordSnapshot(clip.projectId) {
            dao.setSpeed(clipId, clampedSpeed)
            dao.reflowTrack(clip.projectId, clip.trackIndex)
        }
    }

    suspend fun setFilterEffect(clipId: String, filterEffect: String) {
        val clip = dao.find(clipId) ?: return
        if (clip.filterEffect == filterEffect) return
        recordSnapshot(clip.projectId) {
            dao.setFilterEffect(clipId, filterEffect)
        }
    }

    suspend fun setRotation(clipId: String, degrees: Int) {
        val clip = dao.find(clipId) ?: return
        val normalizedDegrees = ((degrees % 360) + 360) % 360
        if (clip.rotationDegrees == normalizedDegrees) return
        recordSnapshot(clip.projectId) {
            dao.setRotation(clipId, normalizedDegrees)
        }
    }

    suspend fun setTextOverlay(clipId: String, textOverlay: String) {
        val clip = dao.find(clipId) ?: return
        val trimmed = textOverlay.trim()
        if (clip.textOverlay == trimmed) return
        recordSnapshot(clip.projectId) {
            dao.setTextOverlay(clipId, trimmed)
        }
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

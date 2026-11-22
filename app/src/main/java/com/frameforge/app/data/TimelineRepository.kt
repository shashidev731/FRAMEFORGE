package com.frameforge.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TimelineRepository(private val dao: TimelineClipDao) {
    fun clips(projectId: String): Flow<List<TimelineClipEntity>> = dao.observeClips(projectId)

    suspend fun add(projectId: String, uri: String, name: String, mimeType: String, durationMs: Long = 5000L) {
        require(projectId.isNotBlank() && uri.isNotBlank())
        val track = if (mimeType.startsWith("audio/")) 1 else 0
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

    suspend fun remove(clipId: String) = dao.removeAndCompact(clipId)
    suspend fun duplicate(clipId: String): String? = dao.duplicateClip(clipId)
    suspend fun splitAt(clipId: String, splitAtMs: Long): Boolean = dao.splitAt(clipId, splitAtMs)
    suspend fun updateTrim(clip: TimelineClipEntity, startMs: Long, endMs: Long) {
        require(startMs >= 0L && endMs > startMs && endMs <= clip.durationMs)
        dao.updateTrimRange(clip.clipId, startMs, endMs)
        dao.reflowTrack(clip.projectId, clip.trackIndex)
    }
    suspend fun setGapBefore(clipId: String, gapMs: Long) =
        dao.updateGapBeforeAndReflow(clipId, gapMs.coerceAtLeast(0L))
    suspend fun moveEarlier(clipId: String) = dao.move(clipId, -1)
    suspend fun moveLater(clipId: String) = dao.move(clipId, 1)
    suspend fun moveToPosition(clipId: String, position: Int) = dao.moveToPosition(clipId, position)
}

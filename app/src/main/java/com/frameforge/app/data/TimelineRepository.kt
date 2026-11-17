package com.frameforge.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TimelineRepository(private val dao: TimelineClipDao) {
    fun clips(projectId: String): Flow<List<TimelineClipEntity>> = dao.observeClips(projectId)

    suspend fun add(projectId: String, uri: String, name: String, mimeType: String, durationMs: Long = 5000L) {
        require(projectId.isNotBlank() && uri.isNotBlank())
        val track = 0
        dao.insert(
            TimelineClipEntity(
                clipId = UUID.randomUUID().toString(),
                projectId = projectId,
                mediaUri = uri,
                displayName = name,
                mimeType = mimeType,
                trackIndex = track,
                position = dao.nextPosition(projectId, track),
                durationMs = durationMs.coerceAtLeast(1L),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(clipId: String) = dao.removeAndCompact(clipId)
    suspend fun splitAt(clipId: String, splitAtMs: Long): Boolean = dao.splitAt(clipId, splitAtMs)
    suspend fun updateTrim(clip: TimelineClipEntity, startMs: Long, endMs: Long) {
        require(startMs >= 0L && endMs > startMs && endMs <= clip.durationMs)
        dao.updateTrimRange(clip.clipId, startMs, endMs)
    }
    suspend fun moveEarlier(clipId: String) = dao.move(clipId, -1)
    suspend fun moveLater(clipId: String) = dao.move(clipId, 1)
}

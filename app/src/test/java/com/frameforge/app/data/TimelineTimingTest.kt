package com.frameforge.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTimingTest {
    private fun clip(
        id: String,
        position: Int,
        durationMs: Long,
        trimStartMs: Long = 0L,
        trimEndMs: Long? = null,
        gapBeforeMs: Long = 0L
    ) = TimelineClipEntity(
        clipId = id,
        projectId = "project",
        mediaUri = "content://media/$id",
        displayName = id,
        mimeType = "video/mp4",
        trackIndex = 0,
        position = position,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        durationMs = durationMs,
        gapBeforeMs = gapBeforeMs,
        createdAt = 1L
    )

    @Test
    fun offsetsFollowClipOrderAndVisibleTrimDuration() {
        val clips = listOf(
            clip("second", position = 1, durationMs = 9000L, trimStartMs = 1000L, trimEndMs = 6000L),
            clip("first", position = 0, durationMs = 4000L)
        )

        val result = withSequentialTimelineOffsets(clips)

        assertEquals(listOf("first", "second"), result.map { it.clipId })
        assertEquals(listOf(0L, 4000L), result.map { it.timelineStartMs })
    }

    @Test
    fun unknownDurationDoesNotInventTimelineLength() {
        val clips = listOf(
            clip("unknown", position = 0, durationMs = 0L),
            clip("known", position = 1, durationMs = 2500L)
        )

        val result = withSequentialTimelineOffsets(clips)

        assertEquals(listOf(0L, 0L), result.map { it.timelineStartMs })
    }

    @Test
    fun explicitGapIsIncludedBeforeClipAndPreservedOnReflow() {
        val clips = listOf(
            clip("first", position = 0, durationMs = 3000L),
            clip("second", position = 1, durationMs = 2000L, gapBeforeMs = 1500L),
            clip("third", position = 2, durationMs = 1000L)
        )

        val result = withSequentialTimelineOffsets(clips)

        assertEquals(listOf(0L, 4500L, 6500L), result.map { it.timelineStartMs })
    }

    @Test
    fun trimStartAndEndDetermineVisibleDuration() {
        val clips = listOf(
            clip("trimmed", position = 0, durationMs = 10000L, trimStartMs = 2000L, trimEndMs = 7000L),
            clip("next", position = 1, durationMs = 1000L)
        )

        val result = withSequentialTimelineOffsets(clips)

        assertEquals(0L, result[0].timelineStartMs)
        assertEquals(5000L, result[1].timelineStartMs)
    }
}

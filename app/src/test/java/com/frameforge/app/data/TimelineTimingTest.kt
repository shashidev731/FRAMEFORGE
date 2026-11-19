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
    @Test
    fun movingClipToArbitraryPositionReordersAndReflowsOffsets() {
        val clips = listOf(
            clip("first", 0, 3000L),
            clip("middle", 1, 4000L, trimStartMs = 500L, trimEndMs = 2500L, gapBeforeMs = 1000L),
            clip("last", 2, 2000L, gapBeforeMs = 500L)
        )

        val result = reorderTimelineClips(clips, movingClipId = "last", requestedPosition = 0)

        assertEquals(listOf("last", "first", "middle"), result.map { it.clipId })
        assertEquals(listOf(500L, 2500L, 6500L), result.map { it.timelineStartMs })
        assertEquals(500L, result.first().gapBeforeMs)
        assertEquals(1000L, result.last().gapBeforeMs)
        assertEquals(500L, result.last().trimStartMs)
        assertEquals(2500L, result.last().trimEndMs)
    }

    @Test
    fun arbitraryMoveClampsTargetAndKeepsOtherTrackOutOfResult() {
        val clips = listOf(
            clip("a", 0, 1000L),
            clip("b", 1, 1000L),
            clip("c", 2, 1000L)
        )

        val result = reorderTimelineClips(clips, movingClipId = "a", requestedPosition = 99)

        assertEquals(listOf("b", "c", "a"), result.map { it.clipId })
        assertEquals(listOf(0L, 1000L, 2000L), result.map { it.timelineStartMs })
    }

    @Test
    fun trimBoundsAreClampedToSourceDuration() {
        val media = clip("invalid", 0, 5000L, trimStartMs = 9000L, trimEndMs = 12000L)
        assertEquals(5000L, normalizedClipTrimStartMs(media))
        assertEquals(5000L, normalizedClipTrimEndMs(media))
        assertEquals(0L, visibleClipDurationMs(media))
    }

    @Test
    fun negativeTrimStartAndOversizedTrimEndAreNormalized() {
        val media = clip("edge", 0, 5000L, trimStartMs = -200L, trimEndMs = 9000L)
        assertEquals(0L, normalizedClipTrimStartMs(media))
        assertEquals(5000L, normalizedClipTrimEndMs(media))
        assertEquals(5000L, visibleClipDurationMs(media))
    }

}

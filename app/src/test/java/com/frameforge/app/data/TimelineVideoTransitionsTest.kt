package com.frameforge.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineVideoTransitionsTest {
    private fun clip(
        durationMs: Long = 4_000L,
        startMs: Long = 0L,
        speed: Float = 1f,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L
    ) = TimelineClipEntity(
        clipId = "clip",
        projectId = "project",
        mediaUri = "content://media/clip",
        displayName = "clip",
        mimeType = "video/mp4",
        position = 0,
        durationMs = durationMs,
        timelineStartMs = startMs,
        speed = speed,
        videoFadeInMs = fadeInMs,
        videoFadeOutMs = fadeOutMs,
        createdAt = 1L
    )

    @Test
    fun fadeInAndOutProduceExpectedOpacityAtTimelineBoundaries() {
        val value = clip(startMs = 1_000L, fadeInMs = 1_000L, fadeOutMs = 1_000L)
        assertEquals(0f, timelineClipVideoOpacity(value, 999L), 0f)
        assertEquals(0f, timelineClipVideoOpacity(value, 1_000L), 0f)
        assertEquals(0.5f, timelineClipVideoOpacity(value, 1_500L), 0.0001f)
        assertEquals(1f, timelineClipVideoOpacity(value, 2_000L), 0.0001f)
        assertEquals(1f, timelineClipVideoOpacity(value, 4_000L), 0.0001f)
        assertEquals(0.5f, timelineClipVideoOpacity(value, 4_500L), 0.0001f)
        assertEquals(0.001f, timelineClipVideoOpacity(value, 4_999L), 0.0001f)
        assertEquals(0f, timelineClipVideoOpacity(value, 5_000L), 0f)
    }

    @Test
    fun fadeLengthsAreClampedAndProportionallyShortenedWhenTheyOverlap() {
        assertEquals(0L to 0L, normalizedClipVideoFades(0L, 100L, 100L))
        assertEquals(0L to 1_000L, normalizedClipVideoFades(1_000L, -20L, 2_000L))
        assertEquals(500L to 500L, normalizedClipVideoFades(1_000L, 800L, 800L))
    }

    @Test
    fun playbackSpeedChangesFadeTimingInTimelineSpace() {
        val value = clip(durationMs = 4_000L, speed = 2f, fadeInMs = 500L, fadeOutMs = 500L)
        assertEquals(0f, timelineClipVideoOpacity(value, 0L), 0f)
        assertEquals(0.5f, timelineClipVideoOpacity(value, 250L), 0.0001f)
        assertEquals(1f, timelineClipVideoOpacity(value, 1_000L), 0.0001f)
        assertEquals(0.5f, timelineClipVideoOpacity(value, 1_750L), 0.0001f)
        assertEquals(0f, timelineClipVideoOpacity(value, 2_000L), 0f)
    }
}

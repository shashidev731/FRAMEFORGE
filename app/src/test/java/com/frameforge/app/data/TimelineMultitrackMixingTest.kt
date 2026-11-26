package com.frameforge.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMultitrackMixingTest {
    private fun createClip(
        id: String,
        trackIndex: Int,
        position: Int,
        durationMs: Long,
        speed: Float = 1.0f,
        gapBeforeMs: Long = 0L,
        mimeType: String = "audio/mp4"
    ) = TimelineClipEntity(
        clipId = id,
        projectId = "test_project",
        mediaUri = "content://media/$id",
        displayName = "Track $trackIndex Clip $id",
        mimeType = mimeType,
        trackIndex = trackIndex,
        position = position,
        trimStartMs = 0L,
        trimEndMs = durationMs,
        durationMs = durationMs,
        timelineStartMs = 0L,
        gapBeforeMs = gapBeforeMs,
        speed = speed,
        createdAt = System.currentTimeMillis()
    )

    @Test
    fun multitrackAudioClipsAreGroupedByTrackIndex() {
        val clips = listOf(
            createClip("v1", 0, 0, 10000L, mimeType = "video/mp4"),
            createClip("bgm1", 1, 0, 5000L, mimeType = "audio/mp3"),
            createClip("bgm2", 1, 1, 4000L, mimeType = "audio/mp3"),
            createClip("vo1", 2, 0, 3000L, mimeType = "audio/wav"),
            createClip("sfx1", 3, 0, 1000L, mimeType = "audio/ogg")
        )

        val audioTracks = clips.filter { it.trackIndex >= 1 && it.mimeType.startsWith("audio/") }
            .groupBy { it.trackIndex }

        assertEquals(3, audioTracks.keys.size)
        assertEquals(listOf(1, 2, 3), audioTracks.keys.sorted())
        assertEquals(2, audioTracks[1]?.size)
        assertEquals(1, audioTracks[2]?.size)
        assertEquals(1, audioTracks[3]?.size)
    }

    @Test
    fun speedAdjustmentScalesEffectiveDurationCorrectly() {
        val normalClip = createClip("c1", 0, 0, 4000L, speed = 1.0f, mimeType = "video/mp4")
        val slowMoClip = createClip("c2", 0, 1, 4000L, speed = 0.5f, mimeType = "video/mp4")
        val fastMotionClip = createClip("c3", 0, 2, 4000L, speed = 2.0f, mimeType = "video/mp4")

        assertEquals(4000L, (visibleClipDurationMs(normalClip) / normalClip.speed).toLong())
        assertEquals(8000L, (visibleClipDurationMs(slowMoClip) / slowMoClip.speed).toLong())
        assertEquals(2000L, (visibleClipDurationMs(fastMotionClip) / fastMotionClip.speed).toLong())
    }

    @Test
    fun independentTrackTimelineOffsetsDoNotInterfere() {
        val track1Clips = listOf(
            createClip("a1", 1, 0, 2000L, gapBeforeMs = 500L),
            createClip("a2", 1, 1, 3000L, gapBeforeMs = 1000L)
        )
        val track2Clips = listOf(
            createClip("b1", 2, 0, 5000L, gapBeforeMs = 0L)
        )

        val reflowed1 = withSequentialTimelineOffsets(track1Clips)
        val reflowed2 = withSequentialTimelineOffsets(track2Clips)

        assertEquals(500L, reflowed1[0].timelineStartMs)
        assertEquals(3500L, reflowed1[1].timelineStartMs) // 500 + 2000 + 1000
        assertEquals(0L, reflowed2[0].timelineStartMs)
    }
}


class TimelineVisualTrackCompositionTest {
    private fun clip(
        id: String,
        trackIndex: Int,
        position: Int = 0,
        mimeType: String
    ) = TimelineClipEntity(
        clipId = id,
        projectId = "visual_tracks",
        mediaUri = "content://media/$id",
        displayName = id,
        mimeType = mimeType,
        trackIndex = trackIndex,
        position = position,
        durationMs = 2_000L,
        trimEndMs = 2_000L,
        createdAt = 1L
    )

    @Test
    fun visualAndAudioTracksAreGroupedIntoSeparateCompositionLayers() {
        val clips = listOf(
            clip("main", 0, mimeType = "video/mp4"),
            clip("overlayOne", 100, mimeType = "image/png"),
            clip("overlayTwo", 101, mimeType = "video/mp4"),
            clip("music", 1, mimeType = "audio/mp4"),
            clip("voice", 2, mimeType = "audio/wav")
        )

        val visualTracks = groupVisualTimelineTracks(clips)
        val audioTracks = groupAudioTimelineTracks(clips)

        assertEquals(listOf(0, 100, 101), visualTracks.map { it.trackIndex })
        assertEquals(listOf("main"), visualTracks[0].clips.map { it.clipId })
        assertEquals(listOf("overlayOne"), visualTracks[1].clips.map { it.clipId })
        assertEquals(listOf(1, 2), audioTracks.map { it.trackIndex })
        assertEquals(3, visualTracks.size)
        assertEquals(2, audioTracks.size)
    }

    @Test
    fun legacyVisualTracksAreMovedAboveAudioRangeWhenRestoringHistory() {
        val clips = listOf(
            clip("base", 0, mimeType = "video/mp4"),
            clip("legacyOverlayOne", 1, mimeType = "video/mp4"),
            clip("legacyOverlayTwo", 2, mimeType = "image/png"),
            clip("music", 1, mimeType = "audio/mp4"),
            clip("voice", 2, mimeType = "audio/wav"),
            clip("legacyAudio", 0, mimeType = "audio/ogg")
        )

        val normalized = normalizeLegacyTimelineTrackAssignments(clips).associateBy { it.clipId }

        assertEquals(0, normalized.getValue("base").trackIndex)
        assertEquals(100, normalized.getValue("legacyOverlayOne").trackIndex)
        assertEquals(101, normalized.getValue("legacyOverlayTwo").trackIndex)
        assertEquals(1, normalized.getValue("music").trackIndex)
        assertEquals(2, normalized.getValue("voice").trackIndex)
        assertEquals(3, normalized.getValue("legacyAudio").trackIndex)
    }

    @Test
    fun trackAssignmentRejectsCrossKindAndReservedIndices() {
        assertTrue(isTrackAssignmentAllowed("video/mp4", 0))
        assertTrue(isTrackAssignmentAllowed("image/png", 100))
        assertTrue(isTrackAssignmentAllowed("audio/mp4", 1))
        assertTrue(isTrackAssignmentAllowed("audio/wav", 99))
        assertFalse(isTrackAssignmentAllowed("video/mp4", 1))
        assertFalse(isTrackAssignmentAllowed("audio/mp4", 0))
        assertFalse(isTrackAssignmentAllowed("audio/mp4", 100))
        assertFalse(isTrackAssignmentAllowed("video/mp4", 99))
    }
}

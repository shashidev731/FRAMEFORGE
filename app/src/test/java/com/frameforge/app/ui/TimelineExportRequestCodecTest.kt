package com.frameforge.app.ui

import com.frameforge.app.data.TimelineClipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineExportRequestCodecTest {
    private fun request() = TimelineExportRequest(
        projectId = "project-1",
        clips = listOf(
            TimelineClipEntity(
                clipId = "clip-1",
                projectId = "project-1",
                mediaUri = "content://media/video-1",
                displayName = "clip|one.mp4",
                mimeType = "video/mp4",
                trackIndex = 100,
                position = 2,
                trimStartMs = 250L,
                trimEndMs = 2_250L,
                durationMs = 3_000L,
                timelineStartMs = 1_500L,
                gapBeforeMs = 500L,
                audioVolume = 0.75f,
                audioFadeInMs = 200L,
                audioFadeOutMs = 300L,
                speed = 1.5f,
                filterEffect = "WARM",
                rotationDegrees = 90,
                textOverlay = "Hello | FRAMEFORGE",
                createdAt = 123L
            )
        ),
        configuration = ExportConfiguration(
            resolution = "4K",
            frameRate = 60,
            codec = "H.265",
            quality = "High"
        ),
        outputPath = "/tmp/frameforge-export.mp4"
    )

    @Test
    fun roundTripPreservesTimelineAndExportSettings() {
        val original = request()
        val decoded = TimelineExportRequestCodec.decode(TimelineExportRequestCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun encodedRequestDoesNotExposeRawMediaUrisOrOverlayText() {
        val encoded = TimelineExportRequestCodec.encode(request())
        assertTrue(!encoded.contains("content://media/video-1"))
        assertTrue(!encoded.contains("Hello | FRAMEFORGE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsClipsFromAnotherProject() {
        val original = request().copy(clips = listOf(request().clips.single().copy(projectId = "other")))
        TimelineExportRequestCodec.encode(original)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownRequestVersion() {
        TimelineExportRequestCodec.decode("FFEXPORT0|bad")
    }
}

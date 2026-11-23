package com.frameforge.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineSnapshotCodecTest {
    private fun clip(id: String, position: Int, track: Int = 0, uri: String = "content://media/$id") =
        TimelineClipEntity(
            clipId = id, projectId = "project", mediaUri = uri, displayName = "name|$id\n",
            mimeType = if (track == 1) "audio/mp3" else "video/mp4",
            trackIndex = track, position = position, trimStartMs = 120L, trimEndMs = null,
            durationMs = 2400L, timelineStartMs = 300L, gapBeforeMs = 80L,
            audioVolume = if (track == 1) 0.35f else 1f, createdAt = 1234L
        )

    @Test fun roundTripsEmptyAndPopulatedSnapshots() {
        val before = listOf(clip("a", 0), clip("b", 1, 1))
        val after = listOf(clip("c", 0, 1, "content://with|pipe"))
        val decoded = TimelineSnapshotCodec.decode(TimelineSnapshotCodec.encode(before, after))
        assertEquals(before, decoded.before)
        assertEquals(after, decoded.after)
        val empty = TimelineSnapshotCodec.decode(TimelineSnapshotCodec.encode(emptyList(), emptyList()))
        assertEquals(emptyList<TimelineClipEntity>(), empty.before)
        assertEquals(emptyList<TimelineClipEntity>(), empty.after)
    }

    @Test fun rejectsUnknownVersionAndMalformedClipPayload() {
        runCatching { TimelineSnapshotCodec.decode("TSH0||") }.onSuccess { error("Expected unsupported version") }
        runCatching { TimelineSnapshotCodec.decode("TSH1|bad|bad") }.onSuccess { error("Expected malformed payload") }
    }
}

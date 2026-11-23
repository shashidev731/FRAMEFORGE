package com.frameforge.app.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineSnapshotCodecTest {
    private fun clip(id: String, position: Int, track: Int = 0, uri: String = "content://media/$id") =
        TimelineClipEntity(
            clipId = id, projectId = "project", mediaUri = uri, displayName = "name|$id\n",
            mimeType = if (track == 1) "audio/mp3" else "video/mp4",
            trackIndex = track, position = position, trimStartMs = 120L, trimEndMs = null,
            durationMs = 2400L, timelineStartMs = 300L, gapBeforeMs = 80L,
            audioVolume = if (track == 1) 0.35f else 1f,
            audioFadeInMs = if (track == 1) 400L else 0L,
            audioFadeOutMs = if (track == 1) 500L else 0L,
            createdAt = 1234L
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

    @Test fun readsLegacySnapshotsWithDefaultFadeValues() {
        val legacyLine = listOf(
            text("old"), text("project"), text("content://old"), text("old name"), text("audio/mp3"),
            "1", "0", "0", "", "1000", "0", "0", "0.75", "99"
        ).joinToString("|")
        val legacyPayload = "TSH1|${text(legacyLine)}|${text(legacyLine)}"
        val decoded = TimelineSnapshotCodec.decode(legacyPayload)
        assertEquals(1, decoded.before.size)
        assertEquals(0L, decoded.before.single().audioFadeInMs)
        assertEquals(0L, decoded.before.single().audioFadeOutMs)
        assertEquals(99L, decoded.before.single().createdAt)
    }

    @Test fun rejectsUnknownVersionAndMalformedClipPayload() {
        assertEquals(true, runCatching { TimelineSnapshotCodec.decode("TSH0||") }.isFailure)
        assertEquals(true, runCatching { TimelineSnapshotCodec.decode("TSH2|bad|bad") }.isFailure)
    }

    private fun text(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}

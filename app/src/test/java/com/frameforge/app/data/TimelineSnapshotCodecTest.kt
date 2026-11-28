package com.frameforge.app.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineSnapshotCodecTest {
    private fun clip(
        id: String,
        position: Int,
        track: Int = 0,
        uri: String = "content://media/$id",
        speed: Float = 1.0f,
        effect: String = "NONE",
        rotation: Int = 0,
        textOverlay: String = "",
        videoFadeInMs: Long = 0L,
        videoFadeOutMs: Long = 0L
    ) = TimelineClipEntity(
        clipId = id, projectId = "project", mediaUri = uri, displayName = "name|$id\n",
        mimeType = if (track == 1) "audio/mp3" else "video/mp4",
        trackIndex = track, position = position, trimStartMs = 120L, trimEndMs = null,
        durationMs = 2400L, timelineStartMs = 300L, gapBeforeMs = 80L,
        audioVolume = if (track == 1) 0.35f else 1f,
        audioFadeInMs = if (track == 1) 400L else 0L,
        audioFadeOutMs = if (track == 1) 500L else 0L,
        speed = speed,
        filterEffect = effect,
        rotationDegrees = rotation,
        textOverlay = textOverlay,
        videoFadeInMs = videoFadeInMs,
        videoFadeOutMs = videoFadeOutMs,
        createdAt = 1234L
    )

    @Test
    fun roundTripsEmptyAndPopulatedSnapshots() {
        val before = listOf(clip("a", 0, videoFadeInMs = 350L, videoFadeOutMs = 450L), clip("b", 1, 1, speed = 1.5f, effect = "BW", rotation = 90, textOverlay = "Sample"))
        val after = listOf(clip("c", 0, 1, "content://with|pipe", speed = 0.5f, effect = "WARM", rotation = 180, textOverlay = "Title", videoFadeInMs = 700L, videoFadeOutMs = 900L))
        val decoded = TimelineSnapshotCodec.decode(TimelineSnapshotCodec.encode(before, after))
        assertEquals(before, decoded.before)
        assertEquals(after, decoded.after)
        val empty = TimelineSnapshotCodec.decode(TimelineSnapshotCodec.encode(emptyList(), emptyList()))
        assertEquals(emptyList<TimelineClipEntity>(), empty.before)
        assertEquals(emptyList<TimelineClipEntity>(), empty.after)
    }

    @Test
    fun readsLegacySnapshotsWithDefaultFadeValues() {
        val legacyLine = listOf(
            text("old"), text("project"), text("content://old"), text("old name"), text("audio/mp3"),
            "1", "0", "0", "", "1000", "0", "0", "0.75", "99"
        ).joinToString("|")
        val legacyPayload = "TSH1|${text(legacyLine)}|${text(legacyLine)}"
        val decoded = TimelineSnapshotCodec.decode(legacyPayload)
        assertEquals(1, decoded.before.size)
        assertEquals(0L, decoded.before.single().audioFadeInMs)
        assertEquals(0L, decoded.before.single().audioFadeOutMs)
        assertEquals(1.0f, decoded.before.single().speed)
        assertEquals("NONE", decoded.before.single().filterEffect)
        assertEquals(0, decoded.before.single().rotationDegrees)
        assertEquals("", decoded.before.single().textOverlay)
        assertEquals(99L, decoded.before.single().createdAt)
    }

    @Test
    fun readsTsh2SnapshotsWithDefaultNewFields() {
        val tsh2Line = listOf(
            text("tsh2"), text("project"), text("content://tsh2"), text("tsh2 name"), text("audio/mp3"),
            "1", "0", "0", "", "1000", "0", "0", "0.8", "250", "350", "99"
        ).joinToString("|")
        val payload = "TSH2|${text(tsh2Line)}|${text(tsh2Line)}"
        val decoded = TimelineSnapshotCodec.decode(payload)
        assertEquals(1, decoded.before.size)
        assertEquals(250L, decoded.before.single().audioFadeInMs)
        assertEquals(350L, decoded.before.single().audioFadeOutMs)
        assertEquals(1.0f, decoded.before.single().speed)
        assertEquals("NONE", decoded.before.single().filterEffect)
        assertEquals(0, decoded.before.single().rotationDegrees)
        assertEquals("", decoded.before.single().textOverlay)
    }

    @Test
    fun readsTsh3SnapshotsWithDefaultVideoFadeValues() {
        val tsh3Line = listOf(
            text("tsh3"), text("project"), text("content://tsh3"), text("tsh3 name"), text("video/mp4"),
            "0", "0", "0", "", "1000", "0", "0", "1.0", "0", "0", "1.25",
            text("BW"), "90", text("Caption"), "123"
        ).joinToString("|")
        val payload = "TSH3|${text(tsh3Line)}|${text(tsh3Line)}"
        val decoded = TimelineSnapshotCodec.decode(payload)
        assertEquals(1, decoded.before.size)
        assertEquals(0L, decoded.before.single().videoFadeInMs)
        assertEquals(0L, decoded.before.single().videoFadeOutMs)
        assertEquals(123L, decoded.before.single().createdAt)
    }

    @Test
    fun rejectsUnknownVersionAndMalformedClipPayload() {
        assertEquals(true, runCatching { TimelineSnapshotCodec.decode("TSH0||") }.isFailure)
        assertEquals(true, runCatching { TimelineSnapshotCodec.decode("TSH3|bad|bad") }.isFailure)
    }

    private fun text(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}

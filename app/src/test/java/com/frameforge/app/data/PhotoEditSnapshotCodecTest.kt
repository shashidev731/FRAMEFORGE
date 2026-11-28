package com.frameforge.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditSnapshotCodecTest {
    @Test
    fun roundTripsCompletePhotoStateAndDelimiterBearingText() {
        val snapshot = PhotoEditSnapshot(
            sourceUri = "content://media/photos/one|two",
            exposure = 1.25f,
            saturation = 1.4f,
            contrast = 1.2f,
            warmth = -0.3f,
            highlights = -0.4f,
            shadows = 0.6f,
            sharpness = 0.75f,
            overlayText = "Title | subtitle\nsecond line",
            rotationDegrees = 270,
            cropRatio = "9:16",
            cropHorizontalPosition = -0.45f,
            cropVerticalPosition = 0.7f
        )

        assertEquals(snapshot, PhotoEditSnapshotCodec.decode(PhotoEditSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun decodingClampsUnsafeNumericValuesAndUnknownCropRatios() {
        val encoded = PhotoEditSnapshotCodec.encode(
            PhotoEditSnapshot(
                sourceUri = "content://photo",
                exposure = 8f,
                saturation = -2f,
                contrast = 3f,
                warmth = Float.NaN,
                highlights = -5f,
                shadows = 5f,
                sharpness = 4f,
                overlayText = "x".repeat(100),
                rotationDegrees = -90,
                cropRatio = "unknown",
                cropHorizontalPosition = 4f,
                cropVerticalPosition = -4f
            )
        )

        val decoded = PhotoEditSnapshotCodec.decode(encoded)
        assertEquals(2f, decoded.exposure, 0f)
        assertEquals(0f, decoded.saturation, 0f)
        assertEquals(1.5f, decoded.contrast, 0f)
        assertEquals(0f, decoded.warmth, 0f)
        assertEquals(-1f, decoded.highlights, 0f)
        assertEquals(1f, decoded.shadows, 0f)
        assertEquals(1f, decoded.sharpness, 0f)
        assertEquals(80, decoded.overlayText.length)
        assertEquals(270, decoded.rotationDegrees)
        assertEquals("Original", decoded.cropRatio)
        assertEquals(1f, decoded.cropHorizontalPosition, 0f)
        assertEquals(-1f, decoded.cropVerticalPosition, 0f)
    }

    @Test
    fun snapshotIsRestoredOnlyForTheSamePersistedSourceUri() {
        val snapshot = PhotoEditSnapshot(
            sourceUri = "content://photo/one",
            exposure = 0.5f,
            saturation = 1f,
            contrast = 1f,
            warmth = 0f,
            highlights = 0f,
            shadows = 0f,
            sharpness = 0f,
            overlayText = "",
            rotationDegrees = 0,
            cropRatio = "Original",
            cropHorizontalPosition = 0f,
            cropVerticalPosition = 0f
        )
        val payload = PhotoEditSnapshotCodec.encode(snapshot)
        assertEquals(snapshot, decodePhotoSnapshotForSource(payload, "content://photo/one"))
        assertEquals(null, decodePhotoSnapshotForSource(payload, "content://photo/two"))
        assertEquals(null, decodePhotoSnapshotForSource("malformed", "content://photo/one"))
    }

    @Test
    fun sourceUriTokensAreDeterministicAndDistinct() {
        val firstUri = "content://media/photos/primary"
        val secondUri = "content://media/photos/secondary"
        val firstToken = PhotoEditSnapshotCodec.sourceUriToken(firstUri)
        val secondToken = PhotoEditSnapshotCodec.sourceUriToken(secondUri)
        val payload = PhotoEditSnapshotCodec.encode(
            PhotoEditSnapshot(
                sourceUri = firstUri,
                exposure = 0f,
                saturation = 1f,
                contrast = 1f,
                warmth = 0f,
                highlights = 0f,
                shadows = 0f,
                sharpness = 0f,
                overlayText = "",
                rotationDegrees = 0,
                cropRatio = "Original",
                cropHorizontalPosition = 0f,
                cropVerticalPosition = 0f
            )
        )

        assertEquals(firstToken, PhotoEditSnapshotCodec.sourceUriToken(firstUri))
        assertTrue(firstToken != secondToken)
        assertTrue(payload.startsWith("PHS1|$firstToken|"))
        assertTrue(!payload.startsWith("PHS1|$secondToken|"))
    }

    @Test
    fun photoHistorySqlMatchesTheExactEncodedUriPrefix() {
        assertTrue(PHOTO_SOURCE_MATCH_SQL.contains("substr(payload"))
        assertTrue(PHOTO_SOURCE_MATCH_SQL.contains("PHS1|"))
        assertTrue(PHOTO_SOURCE_MATCH_SQL.contains(":sourceUriToken"))
        assertTrue(!PHOTO_SOURCE_MATCH_SQL.contains("LIKE"))
    }

    @Test
    fun rejectsUnknownVersionsAndTruncatedSnapshots() {
        assertTrue(runCatching { PhotoEditSnapshotCodec.decode("PHS0|") }.isFailure)
        assertTrue(runCatching { PhotoEditSnapshotCodec.decode("PHS1|short") }.isFailure)
    }
}

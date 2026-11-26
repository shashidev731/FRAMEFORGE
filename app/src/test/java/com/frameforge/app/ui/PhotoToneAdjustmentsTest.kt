package com.frameforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoToneAdjustmentsTest {
    @Test
    fun neutralAdjustmentsPreserveEveryPixelAndAlpha() {
        val source = intArrayOf(
            0xff204060.toInt(),
            0x804080c0.toInt(),
            0xffe0d0b0.toInt()
        )

        val result = adjustPhotoPixels(source, width = 3, height = 1, highlights = 0f, shadows = 0f, sharpness = 0f)

        assertTrue(source.contentEquals(result))
    }

    @Test
    fun shadowLiftChangesDarkPixelsMoreThanBrightPixels() {
        val source = intArrayOf(0xff202020.toInt(), 0xffe0e0e0.toInt())

        val result = adjustPhotoPixels(source, 2, 1, highlights = 0f, shadows = 1f, sharpness = 0f)

        val darkLift = (result[0] and 0xff) - 32
        val brightLift = (result[1] and 0xff) - 224
        assertTrue(darkLift > brightLift)
        assertTrue(darkLift > 0)
    }

    @Test
    fun highlightReductionTargetsBrightPixelsMoreThanDarkPixels() {
        val source = intArrayOf(0xff202020.toInt(), 0xffe0e0e0.toInt())

        val result = adjustPhotoPixels(source, 2, 1, highlights = -1f, shadows = 0f, sharpness = 0f)

        val darkChange = 32 - (result[0] and 0xff)
        val brightChange = 224 - (result[1] and 0xff)
        assertTrue(brightChange > darkChange)
        assertTrue(brightChange > 0)
    }

    @Test
    fun sharpnessIncreasesContrastAcrossAnEdge() {
        val source = intArrayOf(
            0xff505050.toInt(),
            0xff505050.toInt(),
            0xffb4b4b4.toInt()
        )

        val result = adjustPhotoPixels(source, 3, 1, highlights = 0f, shadows = 0f, sharpness = 1f)

        val originalDifference = (source[2] and 0xff) - (source[1] and 0xff)
        val adjustedDifference = (result[2] and 0xff) - (result[1] and 0xff)
        assertTrue(adjustedDifference > originalDifference)
        assertEquals(source.map { it ushr 24 }, result.map { it ushr 24 })
    }

    @Test
    fun inputPixelsRemainUnchanged() {
        val source = intArrayOf(0xff303030.toInt(), 0xffa0a0a0.toInt())
        val before = source.copyOf()

        adjustPhotoPixels(source, 2, 1, highlights = 0.4f, shadows = 0.6f, sharpness = 0.5f)

        assertTrue(before.contentEquals(source))
    }
}

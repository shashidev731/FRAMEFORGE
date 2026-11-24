package com.frameforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCropGeometryTest {
    @Test
    fun squareCropDefaultsToCenteredPosition() {
        assertEquals(
            PhotoCropRect(left = 500, top = 0, width = 3000, height = 3000),
            calculatePhotoCropRect(4000, 3000, 1f, 0)
        )
    }

    @Test
    fun horizontalPositionMovesCropBetweenBothEdges() {
        val left = calculatePhotoCropRect(4000, 3000, 1f, 0, horizontalPosition = -1f)
        val right = calculatePhotoCropRect(4000, 3000, 1f, 0, horizontalPosition = 1f)

        assertEquals(0, left.left)
        assertEquals(1000, right.left)
        assertEquals(left.width, right.width)
        assertEquals(left.height, right.height)
    }

    @Test
    fun verticalPositionMovesPortraitCropBetweenBothEdges() {
        val top = calculatePhotoCropRect(3000, 4000, 1f, 0, verticalPosition = -1f)
        val bottom = calculatePhotoCropRect(3000, 4000, 1f, 0, verticalPosition = 1f)

        assertEquals(0, top.top)
        assertEquals(1000, bottom.top)
        assertEquals(top.width, bottom.width)
        assertEquals(top.height, bottom.height)
    }

    @Test
    fun quarterTurnUsesInverseRatioBeforeRotation() {
        val crop = calculatePhotoCropRect(4000, 3000, 9f / 16f, 90)

        assertEquals(4000, crop.width)
        assertEquals(2250, crop.height)
        assertEquals(9f / 16f, crop.height.toFloat() / crop.width.toFloat(), 0.001f)
    }

    @Test
    fun cropPositionIsClampedAndAlwaysStaysInsideSource() {
        val crop = calculatePhotoCropRect(640, 480, 0.75f, 0, 9f, -9f)

        assertEquals(280, crop.left)
        assertEquals(0, crop.top)
        assertTrue(crop.left + crop.width <= 640)
        assertTrue(crop.top + crop.height <= 480)
    }

    @Test
    fun invalidRatioAndNonFiniteOffsetsFallBackSafely() {
        assertEquals(
            PhotoCropRect(0, 0, 640, 480),
            calculatePhotoCropRect(640, 480, Float.NaN, 0, Float.NaN, Float.POSITIVE_INFINITY)
        )
    }
}

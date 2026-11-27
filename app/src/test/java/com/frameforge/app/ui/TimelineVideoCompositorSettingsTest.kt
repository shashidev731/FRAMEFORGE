package com.frameforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineVideoCompositorSettingsTest {
    @Test
    fun overlayTracksUseDistinctPictureInPictureCorners() {
        val topRight = timelineOverlayPlacement(100)!!
        val bottomRight = timelineOverlayPlacement(101)!!
        val bottomLeft = timelineOverlayPlacement(102)!!
        val topLeft = timelineOverlayPlacement(103)!!

        assertEquals(1f, topRight.overlayAnchorX, 0f)
        assertEquals(1f, topRight.overlayAnchorY, 0f)
        assertEquals(1f, bottomRight.overlayAnchorX, 0f)
        assertEquals(-1f, bottomRight.overlayAnchorY, 0f)
        assertEquals(-1f, bottomLeft.overlayAnchorX, 0f)
        assertEquals(-1f, bottomLeft.overlayAnchorY, 0f)
        assertEquals(-1f, topLeft.overlayAnchorX, 0f)
        assertEquals(1f, topLeft.overlayAnchorY, 0f)
        assertEquals(0.34f, topRight.scaleX, 0f)
        assertEquals(0.34f, topRight.scaleY, 0f)
    }

    @Test
    fun mainTrackRemainsFullFrame() {
        assertNull(timelineOverlayPlacement(0))
    }

    @Test
    fun unsupportedTrackIndicesWrapThroughAvailableCorners() {
        assertEquals(timelineOverlayPlacement(100), timelineOverlayPlacement(104))
        assertTrue(timelineOverlayPlacement(103)!!.backgroundAnchorX < 0f)
    }
}

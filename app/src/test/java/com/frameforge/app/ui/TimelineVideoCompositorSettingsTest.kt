package com.frameforge.app.ui

import com.frameforge.app.data.TimelineTrackGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineVideoCompositorSettingsTest {
    private fun track(index: Int) = TimelineTrackGroup(index, emptyList())

    @Test
    fun overlaySequencesUseDistinctPictureInPictureCorners() {
        val topRight = timelineOverlayPlacement(0)!!
        val bottomRight = timelineOverlayPlacement(1)!!
        val bottomLeft = timelineOverlayPlacement(2)!!
        val topLeft = timelineOverlayPlacement(3)!!

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
    fun primaryTrackIsNeverScaledAsAnOverlay() {
        assertNull(timelineOverlayPlacement(-1))
        assertEquals(0, timelineBaseVisualInputIndex(listOf(track(0), track(100))))
        assertEquals(1, timelineBaseVisualInputIndex(listOf(track(100), track(0))))
    }

    @Test
    fun firstActiveOverlayBecomesFullFrameBaseWhenPrimaryTrackIsMissing() {
        assertEquals(0, timelineBaseVisualInputIndex(listOf(track(100), track(101))))
        assertEquals(-1, timelineBaseVisualInputIndex(emptyList()))
    }

    @Test
    fun overlayPositionsAreStableByOrdinalAndRepeatAfterFourSlots() {
        assertNotEquals(timelineOverlayPlacement(0), timelineOverlayPlacement(1))
        assertEquals(timelineOverlayPlacement(0), timelineOverlayPlacement(4))
        assertEquals(timelineOverlayPlacement(1), timelineOverlayPlacement(5))
    }
}

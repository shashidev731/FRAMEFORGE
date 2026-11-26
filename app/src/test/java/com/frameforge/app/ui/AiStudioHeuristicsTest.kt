package com.frameforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStudioHeuristicsTest {
    @Test
    fun ignoresBrightnessChangesAtOrBelowTheThreshold() {
        assertNull(sceneChangeConfidence(80f, 115f))
        assertNull(sceneChangeConfidence(80f, 80f))
    }

    @Test
    fun returnsMinimumConfidenceJustAboveTheThreshold() {
        assertEquals(0.5f, sceneChangeConfidence(80f, 116f)!!, 0.0001f)
    }

    @Test
    fun capsConfidenceForLargeBrightnessChanges() {
        assertEquals(1f, sceneChangeConfidence(0f, 255f)!!, 0.0001f)
    }

    @Test
    fun rejectsInvalidLuminanceSamples() {
        assertNull(sceneChangeConfidence(-1f, 120f))
        assertNull(sceneChangeConfidence(20f, 300f))
    }

    @Test
    fun samplesLargeImagesToA512PixelLongestEdge() {
        assertEquals(8, photoAnalysisSampleSize(4096, 2048))
        assertEquals(1, photoAnalysisSampleSize(512, 400))
    }

    @Test
    fun rejectsInvalidPhotoDimensions() {
        val failure = runCatching { photoAnalysisSampleSize(0, 400) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}

package com.frameforge.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineAudioVolumeTest {
    @Test fun volumeIsClampedToSupportedRange() {
        assertEquals(0f, normalizedClipAudioVolume(-0.2f), 0f)
        assertEquals(0.4f, normalizedClipAudioVolume(0.4f), 0f)
        assertEquals(1f, normalizedClipAudioVolume(1.5f), 0f)
    }

    @Test fun nonFiniteVolumeFallsBackToUnity() {
        assertEquals(1f, normalizedClipAudioVolume(Float.NaN), 0f)
        assertEquals(1f, normalizedClipAudioVolume(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, normalizedClipAudioVolume(Float.NEGATIVE_INFINITY), 0f)
    }
}

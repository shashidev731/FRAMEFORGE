package com.frameforge.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineAudioFadesTest {
    @Test fun fadesAreClampedToClipDuration() {
        assertEquals(0L to 0L, normalizedClipAudioFades(0L, 100L, 100L))
        assertEquals(500L to 500L, normalizedClipAudioFades(1000L, 900L, 900L))
        assertEquals(300L to 200L, normalizedClipAudioFades(500L, 300L, 200L))
    }

    @Test fun fadesDoNotOverlapAndPreserveProportionWhenTooLong() {
        val (fadeIn, fadeOut) = normalizedClipAudioFades(1000L, 800L, 800L)
        assertEquals(1000L, fadeIn + fadeOut)
        assertEquals(500L, fadeIn)
        assertEquals(500L, fadeOut)
    }

    @Test fun negativeFadeValuesAreClamped() {
        assertEquals(0L to 100L, normalizedClipAudioFades(100L, -20L, 120L))
    }
}

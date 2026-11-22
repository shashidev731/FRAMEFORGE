package com.frameforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineDurationFormatTest {
    @Test
    fun formatsZeroAndOrdinaryDurations() {
        assertEquals("00:00", formatTimelineDuration(0L))
        assertEquals("01:05", formatTimelineDuration(65_000L))
        assertEquals("123:45", formatTimelineDuration(7_425_000L))
    }

    @Test
    fun clampsNegativeDurationToZero() {
        assertEquals("00:00", formatTimelineDuration(-50L))
    }

    @Test
    fun formatsVeryLargeDurationsWithoutIntegerOverflow() {
        val seconds = Long.MAX_VALUE / 1000L
        val minutes = seconds / 60L
        val remaining = seconds % 60L
        assertEquals("%02d:%02d".format(minutes, remaining), formatTimelineDuration(Long.MAX_VALUE))
    }
}

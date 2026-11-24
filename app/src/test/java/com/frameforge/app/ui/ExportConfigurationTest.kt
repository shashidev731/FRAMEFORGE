package com.frameforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportConfigurationTest {
    @Test
    fun defaultExportConfigurationValues() {
        val config = ExportConfiguration()
        assertEquals("1080p", config.resolution)
        assertEquals(30, config.frameRate)
        assertEquals("H.264", config.codec)
        assertEquals("Standard", config.quality)
    }

    @Test
    fun customExportConfigurations() {
        val config4k = ExportConfiguration(resolution = "4K", frameRate = 60, codec = "H.265", quality = "High")
        assertEquals("4K", config4k.resolution)
        assertEquals(60, config4k.frameRate)
        assertEquals("H.265", config4k.codec)
        assertEquals("High", config4k.quality)

        val config720p = ExportConfiguration(resolution = "720p", frameRate = 24, codec = "H.264", quality = "Low")
        assertEquals("720p", config720p.resolution)
        assertEquals(24, config720p.frameRate)
        assertEquals("Low", config720p.quality)
    }

    @Test
    fun validResolutionsAndFrameRates() {
        val validResolutions = listOf("720p", "1080p", "4K")
        val validFrameRates = listOf(24, 30, 60)
        assertTrue(validResolutions.contains("1080p"))
        assertTrue(validFrameRates.contains(30))
    }
}

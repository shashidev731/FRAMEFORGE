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


class ExportOutputSpecTest {
    @Test
    fun mapsResolutionQualityAndCodecToRequestedEncoderProfile() {
        val low720 = exportOutputSpec(
            ExportConfiguration(resolution = "720p", frameRate = 24, codec = "H.264", quality = "Low")
        )
        assertEquals(720, low720.shortSidePx)
        assertEquals(24, low720.targetFrameRate)
        assertEquals(1_500_000, low720.videoBitrateBps)

        val standard1080 = exportOutputSpec(ExportConfiguration())
        assertEquals(1080, standard1080.shortSidePx)
        assertEquals(8_000_000, standard1080.videoBitrateBps)

        val high4kHevc = exportOutputSpec(
            ExportConfiguration(resolution = "4K", frameRate = 60, codec = "H.265", quality = "High")
        )
        assertEquals(2160, high4kHevc.shortSidePx)
        assertEquals(60, high4kHevc.targetFrameRate)
        assertEquals(33_750_000, high4kHevc.videoBitrateBps)
    }

    @Test
    fun unsupportedValuesFallBackToSafeDefaults() {
        val spec = exportOutputSpec(
            ExportConfiguration(resolution = "8K", frameRate = 120, codec = "unknown", quality = "ultra")
        )
        assertEquals(1080, spec.shortSidePx)
        assertEquals(30, spec.targetFrameRate)
        assertEquals(8_000_000, spec.videoBitrateBps)
    }
}

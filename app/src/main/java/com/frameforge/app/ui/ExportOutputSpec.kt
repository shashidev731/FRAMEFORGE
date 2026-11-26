package com.frameforge.app.ui

internal data class ExportOutputSpec(
    val shortSidePx: Int,
    val targetFrameRate: Int,
    val videoBitrateBps: Int
)

/**
 * Converts UI choices into conservative encoder requests. The device encoder
 * may still fall back when a resolution or bitrate is unsupported.
 */
internal fun exportOutputSpec(configuration: ExportConfiguration): ExportOutputSpec {
    val shortSidePx = when (configuration.resolution.trim().lowercase()) {
        "720p" -> 720
        "4k" -> 2160
        else -> 1080
    }
    val targetFrameRate = configuration.frameRate.takeIf { it in setOf(24, 30, 60) } ?: 30
    val baseBitrate = when (shortSidePx) {
        720 -> 3_000_000
        1080 -> 8_000_000
        else -> 30_000_000
    }
    val qualityMultiplier = when (configuration.quality.trim().lowercase()) {
        "low" -> 0.5
        "high" -> 1.5
        else -> 1.0
    }
    val codecMultiplier = if (configuration.codec.trim().equals("H.265", ignoreCase = true)) 0.75 else 1.0
    val requestedBitrate = (baseBitrate * qualityMultiplier * codecMultiplier)
        .toLong()
        .coerceIn(500_000L, Int.MAX_VALUE.toLong())
        .toInt()

    return ExportOutputSpec(
        shortSidePx = shortSidePx,
        targetFrameRate = targetFrameRate,
        videoBitrateBps = requestedBitrate
    )
}

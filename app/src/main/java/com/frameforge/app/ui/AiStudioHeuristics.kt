package com.frameforge.app.ui

import kotlin.math.abs

/**
 * Returns a conservative scene-change hint from two average luminance samples.
 * This is a brightness heuristic, not semantic scene recognition.
 */
internal fun sceneChangeConfidence(previousLuminance: Float, currentLuminance: Float): Float? {
    if (previousLuminance !in 0f..255f || currentLuminance !in 0f..255f) return null
    val delta = abs(currentLuminance - previousLuminance)
    return if (delta > 35f) (delta / 100f).coerceIn(0.5f, 1f) else null
}

/** Returns a power-of-two decode sample that keeps analysis images within the requested edge. */
internal fun photoAnalysisSampleSize(width: Int, height: Int, maxDimension: Int = 512): Int {
    require(width > 0 && height > 0 && maxDimension > 0)
    var sampleSize = 1
    while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

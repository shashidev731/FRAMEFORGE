package com.frameforge.app.ui

import kotlin.math.roundToInt

internal data class PhotoCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

/**
 * Calculates a bounded crop rectangle. Positions are normalized from -1 (leading edge)
 * through 0 (center) to 1 (trailing edge). The inverse target ratio is used before a
 * quarter-turn rotation so the final bitmap keeps the requested aspect ratio.
 */
internal fun calculatePhotoCropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    targetRatio: Float,
    rotationDegrees: Int,
    horizontalPosition: Float = 0f,
    verticalPosition: Float = 0f
): PhotoCropRect {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
    val fallbackRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
    val safeRatio = targetRatio.takeIf { it.isFinite() && it > 0f } ?: fallbackRatio
    val cropRatio = if (rotationDegrees % 180 != 0) 1f / safeRatio else safeRatio
    val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()

    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > cropRatio) {
        cropHeight = sourceHeight
        cropWidth = (cropHeight * cropRatio).roundToInt().coerceIn(1, sourceWidth)
    } else {
        cropWidth = sourceWidth
        cropHeight = (cropWidth / cropRatio).roundToInt().coerceIn(1, sourceHeight)
    }

    val maxLeft = sourceWidth - cropWidth
    val maxTop = sourceHeight - cropHeight
    val x = horizontalPosition.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
    val y = verticalPosition.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
    val left = (maxLeft * ((x + 1f) / 2f)).roundToInt().coerceIn(0, maxLeft)
    val top = (maxTop * ((y + 1f) / 2f)).roundToInt().coerceIn(0, maxTop)

    return PhotoCropRect(left, top, cropWidth, cropHeight)
}

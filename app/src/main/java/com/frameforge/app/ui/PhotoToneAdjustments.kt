package com.frameforge.app.ui

import kotlin.math.roundToInt

/**
 * Applies luminance-weighted highlight/shadow correction and a restrained
 * cross-kernel unsharp mask to ARGB pixels. The source array is never mutated.
 */
internal fun adjustPhotoPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    highlights: Float,
    shadows: Float,
    sharpness: Float
): IntArray {
    require(width > 0 && height > 0)
    val pixelCount = width.toLong() * height.toLong()
    require(pixelCount <= pixels.size.toLong() && pixelCount <= Int.MAX_VALUE.toLong()) {
        "The supplied pixel buffer is smaller than the requested image dimensions."
    }

    val source = pixels.copyOf(pixelCount.toInt())
    val output = IntArray(source.size)
    val highlightAmount = highlights.coerceIn(-1f, 1f)
    val shadowAmount = shadows.coerceIn(-1f, 1f)
    val sharpnessAmount = sharpness.coerceIn(0f, 1f) * 0.85f

    fun channel(pixel: Int, shift: Int): Int = (pixel ushr shift) and 0xff

    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val pixel = source[index]
            val red = channel(pixel, 16)
            val green = channel(pixel, 8)
            val blue = channel(pixel, 0)
            val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
            val shadowWeight = (1f - luminance) * (1f - luminance)
            val highlightWeight = luminance * luminance
            val toneOffset = (
                shadowAmount * shadowWeight + highlightAmount * highlightWeight
            ) * 64f

            // Clamp at image edges and read the four-neighbour kernel without
            // allocating a temporary array for every pixel.
            val top = source[(y - 1).coerceAtLeast(0) * width + x]
            val bottom = source[(y + 1).coerceAtMost(height - 1) * width + x]
            val left = source[y * width + (x - 1).coerceAtLeast(0)]
            val right = source[y * width + (x + 1).coerceAtMost(width - 1)]
            val inverseCount = 0.25f
            val neighborRed = (
                channel(top, 16) + channel(bottom, 16) +
                    channel(left, 16) + channel(right, 16)
                ) * inverseCount
            val neighborGreen = (
                channel(top, 8) + channel(bottom, 8) +
                    channel(left, 8) + channel(right, 8)
                ) * inverseCount
            val neighborBlue = (
                channel(top, 0) + channel(bottom, 0) +
                    channel(left, 0) + channel(right, 0)
                ) * inverseCount
            // The four-neighbour values are already averages. Subtract each
            // average once to form the high-frequency detail for the unsharp mask.
            val redDetail = red - neighborRed
            val greenDetail = green - neighborGreen
            val blueDetail = blue - neighborBlue

            val outRed = (red + toneOffset + redDetail * sharpnessAmount).roundToInt().coerceIn(0, 255)
            val outGreen = (green + toneOffset + greenDetail * sharpnessAmount).roundToInt().coerceIn(0, 255)
            val outBlue = (blue + toneOffset + blueDetail * sharpnessAmount).roundToInt().coerceIn(0, 255)
            output[index] = (pixel and -0x1000000) or (outRed shl 16) or (outGreen shl 8) or outBlue
        }
    }
    return output
}

package com.frameforge.app.data

import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class PhotoEditSnapshot(
    val sourceUri: String,
    val exposure: Float,
    val saturation: Float,
    val contrast: Float,
    val warmth: Float,
    val highlights: Float,
    val shadows: Float,
    val sharpness: Float,
    val overlayText: String,
    val rotationDegrees: Int,
    val cropRatio: String,
    val cropHorizontalPosition: Float,
    val cropVerticalPosition: Float
)

/** Versioned, delimiter-safe Room payload for a complete non-destructive photo edit state. */
internal object PhotoEditSnapshotCodec {
    private const val VERSION = "PHS1"
    private val cropRatios = setOf("Original", "1:1", "4:5", "9:16", "16:9", "3:2")

    fun encode(snapshot: PhotoEditSnapshot): String = listOf(
        VERSION,
        encodeText(snapshot.sourceUri),
        snapshot.exposure.toString(),
        snapshot.saturation.toString(),
        snapshot.contrast.toString(),
        snapshot.warmth.toString(),
        snapshot.highlights.toString(),
        snapshot.shadows.toString(),
        snapshot.sharpness.toString(),
        encodeText(snapshot.overlayText),
        snapshot.rotationDegrees.toString(),
        encodeText(snapshot.cropRatio),
        snapshot.cropHorizontalPosition.toString(),
        snapshot.cropVerticalPosition.toString()
    ).joinToString("|")

    fun decode(payload: String): PhotoEditSnapshot {
        val fields = payload.split("|")
        require(fields.size == 14 && fields[0] == VERSION) { "Unsupported or malformed photo edit snapshot" }
        val sourceUri = decodeText(fields[1])
        require(sourceUri.isNotBlank()) { "Photo edit snapshot has no source URI" }
        val cropRatio = decodeText(fields[11]).takeIf { it in cropRatios } ?: "Original"
        val rotation = fields[10].toIntOrNull() ?: 0
        return PhotoEditSnapshot(
            sourceUri = sourceUri,
            exposure = parseFloat(fields[2], 0f, -2f, 2f),
            saturation = parseFloat(fields[3], 1f, 0f, 2f),
            contrast = parseFloat(fields[4], 1f, 0.5f, 1.5f),
            warmth = parseFloat(fields[5], 0f, -1f, 1f),
            highlights = parseFloat(fields[6], 0f, -1f, 1f),
            shadows = parseFloat(fields[7], 0f, -1f, 1f),
            sharpness = parseFloat(fields[8], 0f, 0f, 1f),
            overlayText = decodeText(fields[9]).take(80),
            rotationDegrees = ((rotation % 360) + 360) % 360,
            cropRatio = cropRatio,
            cropHorizontalPosition = parseFloat(fields[12], 0f, -1f, 1f),
            cropVerticalPosition = parseFloat(fields[13], 0f, -1f, 1f)
        )
    }

    private fun parseFloat(value: String, default: Float, min: Float, max: Float): Float =
        value.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(min, max) ?: default

    private fun encodeText(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}

/** Returns a decoded snapshot only when it belongs to the currently selected source URI. */
internal fun decodePhotoSnapshotForSource(payload: String?, sourceUri: String): PhotoEditSnapshot? {
    if (payload.isNullOrBlank() || sourceUri.isBlank()) return null
    return runCatching { PhotoEditSnapshotCodec.decode(payload) }
        .getOrNull()
        ?.takeIf { it.sourceUri == sourceUri }
}

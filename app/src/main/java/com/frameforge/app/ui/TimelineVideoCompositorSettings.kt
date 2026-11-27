package com.frameforge.app.ui

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import com.frameforge.app.data.TimelineTrackGroup
import com.frameforge.app.data.effectiveClipDurationMs
import com.frameforge.app.data.saturatingTimelineEndMs

internal data class TimelineOverlayPlacement(
    val scaleX: Float,
    val scaleY: Float,
    val overlayAnchorX: Float,
    val overlayAnchorY: Float,
    val backgroundAnchorX: Float,
    val backgroundAnchorY: Float
)

/** Returns the primary visual sequence index, promoting the first active visual track if needed. */
internal fun timelineBaseVisualInputIndex(visualTracks: List<TimelineTrackGroup>): Int =
    visualTracks.indexOfFirst { it.trackIndex == 0 }
        .takeIf { it >= 0 }
        ?: if (visualTracks.isEmpty()) -1 else 0

/** Places each non-primary visual sequence in a separate corner of the main video. */
internal fun timelineOverlayPlacement(overlayOrdinal: Int): TimelineOverlayPlacement? {
    if (overlayOrdinal < 0) return null
    return when (overlayOrdinal % 4) {
        0 -> TimelineOverlayPlacement(0.34f, 0.34f, 1f, 1f, 0.82f, 0.82f)
        1 -> TimelineOverlayPlacement(0.34f, 0.34f, 1f, -1f, 0.82f, -0.82f)
        2 -> TimelineOverlayPlacement(0.34f, 0.34f, -1f, -1f, -0.82f, -0.82f)
        else -> TimelineOverlayPlacement(0.34f, 0.34f, -1f, 1f, -0.82f, 0.82f)
    }
}

/**
 * Visual sequences are appended before audio sequences when the composition is built.
 * Input IDs therefore refer to the filtered visualTracks list, not persisted track indices.
 */
@UnstableApi
internal class TimelineVideoCompositorSettings(
    private val visualTracks: List<TimelineTrackGroup>
) : VideoCompositorSettings {

    private val baseInputId = timelineBaseVisualInputIndex(visualTracks)

    override fun getOutputSize(inputSizes: List<Size>): Size =
        inputSizes.getOrNull(baseInputId) ?: inputSizes.firstOrNull() ?: Size(1920, 1080)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val track = visualTracks.getOrNull(inputId) ?: return StaticOverlaySettings.Builder().build()
        val positionMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
        val activeClip = track.clips.firstOrNull { clip ->
            val durationMs = effectiveClipDurationMs(clip)
            durationMs > 0L &&
                positionMs >= clip.timelineStartMs &&
                positionMs < saturatingTimelineEndMs(clip.timelineStartMs, durationMs)
        }
        val isBaseInput = inputId == baseInputId
        val alpha = if (!isBaseInput && activeClip == null) 0f else 1f
        val builder = StaticOverlaySettings.Builder().setAlphaScale(alpha)
        if (!isBaseInput) {
            timelineOverlayPlacement(inputId - baseInputId - 1)?.let { placement ->
                builder
                    .setScale(placement.scaleX, placement.scaleY)
                    .setOverlayFrameAnchor(placement.overlayAnchorX, placement.overlayAnchorY)
                    .setBackgroundFrameAnchor(placement.backgroundAnchorX, placement.backgroundAnchorY)
            }
        }
        return builder.build()
    }
}

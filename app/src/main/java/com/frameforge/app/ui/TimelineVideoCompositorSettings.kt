package com.frameforge.app.ui

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import com.frameforge.app.data.FIRST_OVERLAY_TRACK_INDEX
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

/** Places each overlay lane in a separate corner of the main video. */
internal fun timelineOverlayPlacement(trackIndex: Int): TimelineOverlayPlacement? {
    if (trackIndex < FIRST_OVERLAY_TRACK_INDEX) return null
    return when ((trackIndex - FIRST_OVERLAY_TRACK_INDEX) % 4) {
        0 -> TimelineOverlayPlacement(0.34f, 0.34f, 1f, 1f, 0.82f, 0.82f)
        1 -> TimelineOverlayPlacement(0.34f, 0.34f, 1f, -1f, 0.82f, -0.82f)
        2 -> TimelineOverlayPlacement(0.34f, 0.34f, -1f, -1f, -0.82f, -0.82f)
        else -> TimelineOverlayPlacement(0.34f, 0.34f, -1f, 1f, -0.82f, 0.82f)
    }
}

/**
 * Visual tracks are appended before audio tracks when the composition is built,
 * so Media3's video input IDs match the sorted visualTracks list.
 */
@UnstableApi
internal class TimelineVideoCompositorSettings(
    private val visualTracks: List<TimelineTrackGroup>
) : VideoCompositorSettings {

    override fun getOutputSize(inputSizes: List<Size>): Size =
        inputSizes.firstOrNull() ?: Size(1920, 1080)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val track = visualTracks.getOrNull(inputId) ?: return StaticOverlaySettings.Builder().build()
        val positionMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
        val activeClip = track.clips.firstOrNull { clip ->
            val durationMs = effectiveClipDurationMs(clip)
            durationMs > 0L &&
                positionMs >= clip.timelineStartMs &&
                positionMs < saturatingTimelineEndMs(clip.timelineStartMs, durationMs)
        }
        val isOverlayTrack = track.trackIndex >= FIRST_OVERLAY_TRACK_INDEX
        val alpha = if (isOverlayTrack && activeClip == null) 0f else 1f
        val builder = StaticOverlaySettings.Builder().setAlphaScale(alpha)
        timelineOverlayPlacement(track.trackIndex)?.let { placement ->
            builder
                .setScale(placement.scaleX, placement.scaleY)
                .setOverlayFrameAnchor(placement.overlayAnchorX, placement.overlayAnchorY)
                .setBackgroundFrameAnchor(placement.backgroundAnchorX, placement.backgroundAnchorY)
        }
        return builder.build()
    }
}

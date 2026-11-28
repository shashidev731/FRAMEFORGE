package com.frameforge.app.data

/** Clamp video fade lengths to a clip's timeline duration and prevent their overlap. */
internal fun normalizedClipVideoFades(durationMs: Long, fadeInMs: Long, fadeOutMs: Long): Pair<Long, Long> {
    val duration = durationMs.coerceAtLeast(0L)
    var fadeIn = fadeInMs.coerceIn(0L, duration)
    var fadeOut = fadeOutMs.coerceIn(0L, duration)
    val total = fadeIn.toDouble() + fadeOut.toDouble()
    if (total > duration.toDouble() && total > 0.0) {
        fadeIn = (fadeIn.toDouble() * duration.toDouble() / total).toLong()
        fadeOut = duration - fadeIn
    }
    return fadeIn to fadeOut
}

/**
 * Returns the clip's compositor opacity at a timeline position.
 * The primary visual track fades against the compositor's black background;
 * overlay tracks fade against the lower visual layers.
 */
internal fun timelineClipVideoOpacity(clip: TimelineClipEntity, timelinePositionMs: Long): Float {
    val duration = effectiveClipDurationMs(clip)
    if (duration <= 0L) return 0f
    val start = clip.timelineStartMs.coerceAtLeast(0L)
    val end = saturatingTimelineEndMs(start, duration)
    if (timelinePositionMs < start || timelinePositionMs >= end) return 0f

    val elapsed = timelinePositionMs - start
    val (fadeIn, fadeOut) = normalizedClipVideoFades(duration, clip.videoFadeInMs, clip.videoFadeOutMs)
    val fadeInOpacity = if (fadeIn == 0L) 1f else
        (elapsed.toDouble() / fadeIn.toDouble()).toFloat().coerceIn(0f, 1f)
    val remaining = duration - elapsed
    val fadeOutOpacity = if (fadeOut == 0L) 1f else
        (remaining.toDouble() / fadeOut.toDouble()).toFloat().coerceIn(0f, 1f)
    return minOf(fadeInOpacity, fadeOutOpacity)
}

package com.frameforge.app.data

/** Trim values are clamped to the source duration before layout or rendering. */
internal fun normalizedClipTrimStartMs(clip: TimelineClipEntity): Long = clip.trimStartMs.coerceIn(0L, clip.durationMs.coerceAtLeast(0L))

internal fun normalizedClipTrimEndMs(clip: TimelineClipEntity): Long = (clip.trimEndMs ?: clip.durationMs).coerceIn(normalizedClipTrimStartMs(clip), clip.durationMs.coerceAtLeast(0L))

internal fun visibleClipDurationMs(clip: TimelineClipEntity): Long = (normalizedClipTrimEndMs(clip) - normalizedClipTrimStartMs(clip)).coerceAtLeast(0L)

/** Keeps persisted audio volume finite and in the supported 0–100% range. */
internal fun normalizedClipAudioVolume(volume: Float): Float =
    if (volume.isFinite()) volume.coerceIn(0f, 1f) else 1f

/** Clamp clip fades and proportionally shorten them when they would overlap. */
internal fun normalizedClipAudioFades(durationMs: Long, fadeInMs: Long, fadeOutMs: Long): Pair<Long, Long> {
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
 * Assigns sequential timeline positions within one track.
 * A clip's visible duration is its trim range, not necessarily its source duration.
 */
internal fun withSequentialTimelineOffsets(clips: List<TimelineClipEntity>): List<TimelineClipEntity> {
    var cursorMs = 0L
    return clips.sortedBy { it.position }.map { clip ->
        val gapMs = clip.gapBeforeMs.coerceAtLeast(0L)
        cursorMs = if (gapMs > Long.MAX_VALUE - cursorMs) Long.MAX_VALUE else cursorMs + gapMs
        val positioned = clip.copy(timelineStartMs = cursorMs)
        val visibleDurationMs = visibleClipDurationMs(clip)
        cursorMs = if (visibleDurationMs > Long.MAX_VALUE - cursorMs) Long.MAX_VALUE
            else cursorMs + visibleDurationMs
        positioned
    }
}

/**
 * Reorders one track's clips and recalculates their starts without changing
 * per-clip trims or gap settings.
 */
internal fun reorderTimelineClips(
    clips: List<TimelineClipEntity>,
    movingClipId: String,
    requestedPosition: Int
): List<TimelineClipEntity> {
    val ordered = clips.sortedBy { it.position }
    if (ordered.size < 2) return withSequentialTimelineOffsets(ordered)
    val source = ordered.indexOfFirst { it.clipId == movingClipId }
    if (source < 0) return withSequentialTimelineOffsets(ordered)
    val target = requestedPosition.coerceIn(0, ordered.lastIndex)
    if (source == target) return withSequentialTimelineOffsets(ordered)
    val reordered = ordered.toMutableList().apply {
        val moving = removeAt(source)
        add(target, moving)
    }.mapIndexed { index, clip -> clip.copy(position = index) }
    return withSequentialTimelineOffsets(reordered)
}

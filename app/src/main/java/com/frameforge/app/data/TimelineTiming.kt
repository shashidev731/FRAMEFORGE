package com.frameforge.app.data

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
        val endMs = clip.trimEndMs ?: clip.durationMs
        val visibleDurationMs = (endMs - clip.trimStartMs).coerceAtLeast(0L)
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

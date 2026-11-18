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

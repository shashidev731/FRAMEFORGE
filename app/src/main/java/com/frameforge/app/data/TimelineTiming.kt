package com.frameforge.app.data

import kotlin.math.ceil
import kotlin.math.roundToLong

/** Trim values are clamped to the source duration before layout or rendering. */
internal fun normalizedClipTrimStartMs(clip: TimelineClipEntity): Long = clip.trimStartMs.coerceIn(0L, clip.durationMs.coerceAtLeast(0L))

internal fun normalizedClipTrimEndMs(clip: TimelineClipEntity): Long = (clip.trimEndMs ?: clip.durationMs).coerceIn(normalizedClipTrimStartMs(clip), clip.durationMs.coerceAtLeast(0L))

internal fun visibleClipDurationMs(clip: TimelineClipEntity): Long =
    (normalizedClipTrimEndMs(clip) - normalizedClipTrimStartMs(clip)).coerceAtLeast(0L)

/** Normalizes persisted speed values so invalid data cannot corrupt timeline arithmetic. */
internal fun normalizedClipSpeed(speed: Float): Float =
    if (speed.isFinite()) speed.coerceIn(0.25f, 4.0f) else 1.0f

/** Returns the clip's duration on the timeline after applying its playback speed. */
internal fun effectiveClipSpeed(clip: TimelineClipEntity): Float =
    if (clip.mimeType.startsWith("audio/")) 1.0f else normalizedClipSpeed(clip.speed)

internal fun effectiveClipDurationMs(clip: TimelineClipEntity): Long {
    val sourceDurationMs = visibleClipDurationMs(clip)
    if (sourceDurationMs == 0L) return 0L
    val scaledDuration = ceil(sourceDurationMs.toDouble() / effectiveClipSpeed(clip))
    return if (!scaledDuration.isFinite() || scaledDuration >= Long.MAX_VALUE.toDouble()) {
        Long.MAX_VALUE
    } else {
        scaledDuration.toLong().coerceAtLeast(0L)
    }
}

/** Adds non-negative timeline durations without allowing Long overflow. */
internal fun saturatingTimelineEndMs(startMs: Long, durationMs: Long): Long {
    val start = startMs.coerceAtLeast(0L)
    val duration = durationMs.coerceAtLeast(0L)
    return if (duration > Long.MAX_VALUE - start) Long.MAX_VALUE else start + duration
}

/** Converts a timeline playhead position back into the source timestamp used by split/trim APIs. */
internal fun sourceTimeAtTimelinePositionMs(clip: TimelineClipEntity, timelinePositionMs: Long): Long {
    val trimStartMs = normalizedClipTrimStartMs(clip)
    val trimEndMs = normalizedClipTrimEndMs(clip)
    val elapsedTimelineMs = if (timelinePositionMs <= clip.timelineStartMs) 0L
        else timelinePositionMs - clip.timelineStartMs
    val sourceOffset = elapsedTimelineMs.toDouble() * effectiveClipSpeed(clip).toDouble()
    val roundedOffset = if (!sourceOffset.isFinite() || sourceOffset >= Long.MAX_VALUE.toDouble()) {
        Long.MAX_VALUE
    } else {
        sourceOffset.roundToLong().coerceAtLeast(0L)
    }
    return saturatingTimelineEndMs(trimStartMs, roundedOffset).coerceIn(trimStartMs, trimEndMs)
}

/** True only for a playhead strictly inside a clip's visible timeline interval. */
internal fun isTimelinePositionInsideClip(clip: TimelineClipEntity, timelinePositionMs: Long): Boolean =
    timelinePositionMs > clip.timelineStartMs &&
        timelinePositionMs < saturatingTimelineEndMs(clip.timelineStartMs, effectiveClipDurationMs(clip))

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
        cursorMs = saturatingTimelineEndMs(cursorMs, effectiveClipDurationMs(clip))
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

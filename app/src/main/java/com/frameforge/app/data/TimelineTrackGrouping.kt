package com.frameforge.app.data

/**
 * Track indices are partitioned by media kind because timeline ordering,
 * Room uniqueness and reflow are scoped to (project, trackIndex).
 *
 * 0       = primary visual track
 * 1..99   = audio tracks
 * 100+    = visual overlay tracks
 */
internal const val FIRST_OVERLAY_TRACK_INDEX = 100
internal const val LAST_AUDIO_TRACK_INDEX = FIRST_OVERLAY_TRACK_INDEX - 1

internal data class TimelineTrackGroup(
    val trackIndex: Int,
    val clips: List<TimelineClipEntity>
)

internal fun isAudioTimelineClip(clip: TimelineClipEntity): Boolean =
    clip.mimeType.startsWith("audio/", ignoreCase = true)

internal fun isVisualTimelineClip(clip: TimelineClipEntity): Boolean =
    clip.mimeType.startsWith("video/", ignoreCase = true) ||
        clip.mimeType.startsWith("image/", ignoreCase = true)

/** Rejects assignments that would mix audio and video in one Room track. */
internal fun isTrackAssignmentAllowed(mimeType: String, trackIndex: Int): Boolean {
    if (trackIndex < 0) return false
    return when {
        mimeType.startsWith("audio/", ignoreCase = true) ->
            trackIndex in 1..LAST_AUDIO_TRACK_INDEX
        mimeType.startsWith("video/", ignoreCase = true) ||
            mimeType.startsWith("image/", ignoreCase = true) ->
            trackIndex == 0 || trackIndex >= FIRST_OVERLAY_TRACK_INDEX
        else -> false
    }
}

/**
 * Re-maps snapshots written before visual overlays had a separate index range.
 * Audio tracks keep their existing 1..99 indices. Legacy audio on track 0 and
 * any audio tracks in the reserved overlay range are moved to free audio slots.
 */
internal fun normalizeLegacyTimelineTrackAssignments(
    clips: List<TimelineClipEntity>
): List<TimelineClipEntity> {
    val occupiedAudioTracks = clips.asSequence()
        .filter(::isAudioTimelineClip)
        .map { it.trackIndex }
        .filter { it in 1..LAST_AUDIO_TRACK_INDEX }
        .toMutableSet()
    val remappedAudioTracks = mutableMapOf<Int, Int>()

    fun allocateAudioTrack(): Int {
        val available = (1..LAST_AUDIO_TRACK_INDEX).firstOrNull { it !in occupiedAudioTracks }
            ?: error("No available audio track index remains")
        occupiedAudioTracks += available
        return available
    }

    return clips.map { clip ->
        when {
            isAudioTimelineClip(clip) && clip.trackIndex in 1..LAST_AUDIO_TRACK_INDEX -> clip
            isAudioTimelineClip(clip) -> {
                val normalized = remappedAudioTracks.getOrPut(clip.trackIndex, ::allocateAudioTrack)
                clip.copy(trackIndex = normalized)
            }
            isVisualTimelineClip(clip) && clip.trackIndex in 1 until FIRST_OVERLAY_TRACK_INDEX ->
                clip.copy(trackIndex = FIRST_OVERLAY_TRACK_INDEX + clip.trackIndex - 1)
            isVisualTimelineClip(clip) && clip.trackIndex < 0 -> clip.copy(trackIndex = 0)
            else -> clip
        }
    }
}

internal fun groupVisualTimelineTracks(clips: List<TimelineClipEntity>): List<TimelineTrackGroup> =
    clips.asSequence()
        .filter { isVisualTimelineClip(it) && (it.trackIndex == 0 || it.trackIndex >= FIRST_OVERLAY_TRACK_INDEX) }
        .groupBy { it.trackIndex }
        .toSortedMap()
        .map { (trackIndex, trackClips) ->
            TimelineTrackGroup(trackIndex, trackClips.sortedBy { it.timelineStartMs })
        }

/**
 * Returns only visual tracks that can contribute at least one frame to a composition.
 * The returned order is stable and must be reused when creating Media3 sequences and
 * configuring VideoCompositorSettings because its input IDs are positional.
 */
internal fun composableVisualTimelineTracks(clips: List<TimelineClipEntity>): List<TimelineTrackGroup> =
    groupVisualTimelineTracks(clips).filter { track ->
        track.clips.any { effectiveClipDurationMs(it) > 0L }
    }

internal fun groupAudioTimelineTracks(clips: List<TimelineClipEntity>): List<TimelineTrackGroup> =
    clips.asSequence()
        .filter { isAudioTimelineClip(it) && it.trackIndex in 1..LAST_AUDIO_TRACK_INDEX }
        .groupBy { it.trackIndex }
        .toSortedMap()
        .map { (trackIndex, trackClips) ->
            TimelineTrackGroup(trackIndex, trackClips.sortedBy { it.timelineStartMs })
        }

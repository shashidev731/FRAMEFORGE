package com.frameforge.app

/**
 * SQLite statements for the 10 -> 11 timeline track migration.
 *
 * Do not alias the UPDATE target: Android versions supported by minSdk 26
 * can ship SQLite versions older than 3.33.
 */
internal object TimelineTrackMigrationSql {
    val statements = listOf(
        """
            UPDATE timeline_clips
            SET trackIndex = trackIndex + 99
            WHERE trackIndex BETWEEN 1 AND 99
              AND (lower(mimeType) LIKE 'video/%' OR lower(mimeType) LIKE 'image/%')
        """.trimIndent(),
        """
            UPDATE timeline_clips
            SET timelineStartMs = MAX(COALESCE(timeline_clips.gapBeforeMs, 0), 0) + COALESCE((
                SELECT SUM(
                    MAX(COALESCE(previous.gapBeforeMs, 0), 0)
                    + CAST((
                        MAX(COALESCE(previous.trimEndMs, previous.durationMs) - previous.trimStartMs, 0) * 1.0
                        / CASE
                            WHEN lower(previous.mimeType) LIKE 'audio/%' THEN 1.0
                            WHEN previous.speed >= 0.25 AND previous.speed <= 4.0 THEN previous.speed
                            ELSE 1.0
                          END
                    ) AS INTEGER)
                    + CASE WHEN (
                        MAX(COALESCE(previous.trimEndMs, previous.durationMs) - previous.trimStartMs, 0) * 1.0
                        / CASE
                            WHEN lower(previous.mimeType) LIKE 'audio/%' THEN 1.0
                            WHEN previous.speed >= 0.25 AND previous.speed <= 4.0 THEN previous.speed
                            ELSE 1.0
                          END
                    ) > CAST((
                        MAX(COALESCE(previous.trimEndMs, previous.durationMs) - previous.trimStartMs, 0) * 1.0
                        / CASE
                            WHEN lower(previous.mimeType) LIKE 'audio/%' THEN 1.0
                            WHEN previous.speed >= 0.25 AND previous.speed <= 4.0 THEN previous.speed
                            ELSE 1.0
                          END
                    ) AS INTEGER) THEN 1 ELSE 0 END
                )
                FROM timeline_clips AS previous
                WHERE previous.projectId = timeline_clips.projectId
                  AND previous.trackIndex = timeline_clips.trackIndex
                  AND previous.position < timeline_clips.position
            ), 0)
        """.trimIndent()
    )
}

internal fun nextAvailableAudioTrack(occupiedTracks: Set<Int>): Int =
    (1..99).firstOrNull { it !in occupiedTracks }
        ?: throw IllegalStateException("No free audio track is available for legacy clips.")

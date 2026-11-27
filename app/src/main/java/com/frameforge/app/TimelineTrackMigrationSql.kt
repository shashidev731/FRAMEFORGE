package com.frameforge.app

/**
 * SQLite statements for the 10 -> 11 timeline track migration.
 *
 * Do not add an alias to the UPDATE target: SQLite added that syntax in 3.33,
 * newer than the SQLite builds shipped on some Android versions at minSdk 26.
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
            SET trackIndex = (
                SELECT COALESCE(MAX(other.trackIndex), 0) + 1
                FROM timeline_clips AS other
                WHERE other.projectId = timeline_clips.projectId
                  AND lower(other.mimeType) LIKE 'audio/%'
                  AND other.trackIndex BETWEEN 1 AND 99
            )
            WHERE trackIndex = 0
              AND lower(mimeType) LIKE 'audio/%'
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

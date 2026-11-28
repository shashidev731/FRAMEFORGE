package com.frameforge.app

/** Room 11→12 migration adding persisted per-clip video fade lengths. */
internal object TimelineTransitionMigrationSql {
    val statements = listOf(
        "ALTER TABLE timeline_clips ADD COLUMN videoFadeInMs INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE timeline_clips ADD COLUMN videoFadeOutMs INTEGER NOT NULL DEFAULT 0"
    )
}

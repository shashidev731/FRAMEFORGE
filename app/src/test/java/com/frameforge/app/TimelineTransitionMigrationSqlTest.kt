package com.frameforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTransitionMigrationSqlTest {
    @Test
    fun migrationAddsBothVideoFadeColumnsWithLegacySafeDefaults() {
        assertEquals(2, TimelineTransitionMigrationSql.statements.size)
        assertTrue(TimelineTransitionMigrationSql.statements[0].contains("videoFadeInMs INTEGER NOT NULL DEFAULT 0"))
        assertTrue(TimelineTransitionMigrationSql.statements[1].contains("videoFadeOutMs INTEGER NOT NULL DEFAULT 0"))
        assertTrue(TimelineTransitionMigrationSql.statements.all { it.startsWith("ALTER TABLE timeline_clips ADD COLUMN") })
    }
}

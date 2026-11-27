package com.frameforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTrackMigrationSqlTest {
    @Test
    fun migrationUsesUpdateSyntaxSupportedByOlderAndroidSqlite() {
        val updateTargetAlias = Regex("""UPDATE\\s+timeline_clips\\s+AS\\s+""", RegexOption.IGNORE_CASE)

        assertEquals(3, TimelineTrackMigrationSql.statements.size)
        assertTrue(TimelineTrackMigrationSql.statements.all {
            it.trimStart().startsWith("UPDATE timeline_clips", ignoreCase = true)
        })
        assertFalse(TimelineTrackMigrationSql.statements.any(updateTargetAlias::containsMatchIn))
    }

    @Test
    fun correlatedUpdatesReferenceTheCurrentClipWithoutAnUpdateAlias() {
        val moveAudio = TimelineTrackMigrationSql.statements[1]
        val reflow = TimelineTrackMigrationSql.statements[2]

        assertTrue(moveAudio.contains("other.projectId = timeline_clips.projectId"))
        assertTrue(reflow.contains("previous.projectId = timeline_clips.projectId"))
        assertTrue(reflow.contains("previous.trackIndex = timeline_clips.trackIndex"))
        assertTrue(reflow.contains("previous.position < timeline_clips.position"))
    }
}

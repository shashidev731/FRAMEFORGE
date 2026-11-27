package com.frameforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTrackMigrationSqlTest {
    @Test
    fun migrationUsesUpdateSyntaxSupportedByOlderAndroidSqlite() {
        assertEquals(2, TimelineTrackMigrationSql.statements.size)
        assertTrue(TimelineTrackMigrationSql.statements.all {
            it.trimStart().startsWith("UPDATE timeline_clips", ignoreCase = true)
        })
        assertFalse(TimelineTrackMigrationSql.statements.any {
            it.contains("UPDATE timeline_clips AS", ignoreCase = true)
        })
    }

    @Test
    fun correlatedReflowReferencesTheCurrentClipWithoutAnUpdateAlias() {
        val reflow = TimelineTrackMigrationSql.statements[1]

        assertTrue(reflow.contains("previous.projectId = timeline_clips.projectId"))
        assertTrue(reflow.contains("previous.trackIndex = timeline_clips.trackIndex"))
        assertTrue(reflow.contains("previous.position < timeline_clips.position"))
    }

    @Test
    fun legacyAudioClipsShareTheLowestAvailableTrack() {
        assertEquals(1, nextAvailableAudioTrack(emptySet()))
        assertEquals(2, nextAvailableAudioTrack(setOf(1, 3, 7)))
        assertEquals(99, nextAvailableAudioTrack((1..98).toSet()))
    }

    @Test(expected = IllegalStateException::class)
    fun legacyMigrationFailsClearlyWhenAllAudioTracksAreOccupied() {
        nextAvailableAudioTrack((1..99).toSet())
    }
}

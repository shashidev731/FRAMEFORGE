package com.frameforge.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditOperationDaoHistoryIsolationTest {
    @Test
    fun generalRedoPruningLeavesTimelineAndPhotoSnapshotsAlone() {
        assertTrue(GENERAL_REDO_BRANCH_DELETE_SQL.contains("operationType NOT IN ('timeline_snapshot', 'photo_snapshot')"))
        assertTrue(GENERAL_REDO_BRANCH_DELETE_SQL.contains("isApplied = 0"))
    }

    @Test
    fun timelineRedoPruningOnlyRemovesTimelineSnapshots() {
        assertTrue(TIMELINE_REDO_BRANCH_DELETE_SQL.contains("operationType = 'timeline_snapshot'"))
        assertTrue(TIMELINE_REDO_BRANCH_DELETE_SQL.contains("isApplied = 0"))
    }

    @Test
    fun photoRedoPruningOnlyRemovesPhotoSnapshots() {
        assertTrue(PHOTO_REDO_BRANCH_DELETE_SQL.contains("operationType = 'photo_snapshot'"))
        assertTrue(PHOTO_REDO_BRANCH_DELETE_SQL.contains("isApplied = 0"))
        assertFalse(PHOTO_REDO_BRANCH_DELETE_SQL.contains("timeline_snapshot"))
    }

    @Test
    fun clearingPhotoHistoryDoesNotDeleteOtherOperationTypes() {
        assertTrue(PHOTO_HISTORY_DELETE_SQL.contains("operationType = 'photo_snapshot'"))
        assertFalse(PHOTO_HISTORY_DELETE_SQL.contains("timeline_snapshot"))
    }
}

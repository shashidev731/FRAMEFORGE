package com.frameforge.app.data

import org.junit.Assert.assertTrue
import org.junit.Test

class EditOperationDaoHistoryIsolationTest {
    @Test
    fun generalRedoPruningLeavesTimelineSnapshotsAlone() {
        assertTrue(GENERAL_REDO_BRANCH_DELETE_SQL.contains("operationType != 'timeline_snapshot'"))
        assertTrue(GENERAL_REDO_BRANCH_DELETE_SQL.contains("isApplied = 0"))
    }

    @Test
    fun timelineRedoPruningOnlyRemovesTimelineSnapshots() {
        assertTrue(TIMELINE_REDO_BRANCH_DELETE_SQL.contains("operationType = 'timeline_snapshot'"))
        assertTrue(TIMELINE_REDO_BRANCH_DELETE_SQL.contains("isApplied = 0"))
    }
}

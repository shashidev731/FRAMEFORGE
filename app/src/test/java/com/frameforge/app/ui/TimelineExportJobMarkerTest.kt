package com.frameforge.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class TimelineExportJobMarkerTest {
    @Test
    fun cancelledWorkRemovesItsPrivateRetryRequestAndMarkers() {
        val filesDir = temporaryDirectory()
        try {
            val jobsDirectory = File(filesDir, "export-jobs").apply { assertTrue(mkdirs()) }
            val requestFile = File(jobsDirectory, "request.ffjob").apply { writeText("immutable request") }
            val workId = UUID.randomUUID()

            TimelineExportJobMarker.persist(filesDir, workId, requestFile)

            assertTrue(TimelineExportJobMarker.cleanupCancelled(filesDir, workId.toString()))
            assertFalse(requestFile.exists())
            assertFalse(File(jobsDirectory, "active-work-id.txt").exists())
            assertFalse(File(jobsDirectory, "active-request-path.txt").exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun staleCancellationCannotDeleteTheNewerExportsRequest() {
        val filesDir = temporaryDirectory()
        try {
            val jobsDirectory = File(filesDir, "export-jobs").apply { assertTrue(mkdirs()) }
            val olderRequest = File(jobsDirectory, "older.ffjob").apply { writeText("older") }
            val newerRequest = File(jobsDirectory, "newer.ffjob").apply { writeText("newer") }
            val olderWorkId = UUID.randomUUID()
            val newerWorkId = UUID.randomUUID()

            TimelineExportJobMarker.persist(filesDir, olderWorkId, olderRequest)
            TimelineExportJobMarker.persist(filesDir, newerWorkId, newerRequest)

            assertFalse(TimelineExportJobMarker.cleanupCancelled(filesDir, olderWorkId.toString()))
            assertTrue(newerRequest.exists())
            assertFalse(olderRequest.exists())
            assertTrue(TimelineExportJobMarker.cleanupCancelled(filesDir, newerWorkId.toString()))
            assertFalse(newerRequest.exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun refusesToDeleteARequestOutsideThePrivateExportDirectory() {
        val filesDir = temporaryDirectory()
        try {
            val jobsDirectory = File(filesDir, "export-jobs").apply { assertTrue(mkdirs()) }
            val requestFile = File(jobsDirectory, "request.ffjob").apply { writeText("request") }
            val workId = UUID.randomUUID()
            TimelineExportJobMarker.persist(filesDir, workId, requestFile)

            val marker = File(jobsDirectory, "active-request-path.txt")
            marker.writeText(File(filesDir, "do-not-delete.ffjob").absolutePath)
            val outsideFile = File(filesDir, "do-not-delete.ffjob").apply { writeText("keep") }

            assertFalse(TimelineExportJobMarker.cleanupCancelled(filesDir, workId.toString()))
            assertTrue(outsideFile.exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    private fun temporaryDirectory(): File =
        File(System.getProperty("java.io.tmpdir"), "frameforge-marker-${UUID.randomUUID()}").apply {
            check(mkdirs()) { "Could not create a temporary test directory." }
        }
}

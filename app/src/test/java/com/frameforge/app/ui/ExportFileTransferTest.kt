package com.frameforge.app.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class ExportFileTransferTest {
    @Test
    fun copiesTheEntireExportToTheCallerOwnedStream() {
        val bytes = ByteArray(32_768) { index -> (index % 251).toByte() }
        val source = File.createTempFile("frameforge-export", ".mp4")
        try {
            source.writeBytes(bytes)
            val destination = ByteArrayOutputStream()

            val copiedBytes = copyExportFile(source, destination)

            assertEquals(bytes.size.toLong(), copiedBytes)
            assertArrayEquals(bytes, destination.toByteArray())
        } finally {
            source.delete()
        }
    }

    @Test
    fun rejectsAnEmptyExportFile() {
        val source = File.createTempFile("frameforge-empty-export", ".mp4")
        try {
            val failure = runCatching {
                copyExportFile(source, ByteArrayOutputStream())
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
        } finally {
            source.delete()
        }
    }

    @Test
    fun rejectsAMissingExportFile() {
        val source = File(File(System.getProperty("java.io.tmpdir")), "missing-frameforge-export.mp4")
        source.delete()

        val failure = runCatching {
            copyExportFile(source, ByteArrayOutputStream())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}

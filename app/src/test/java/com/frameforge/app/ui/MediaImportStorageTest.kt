package com.frameforge.app.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class MediaImportStorageTest {
    @Test
    fun sanitizesProviderNamesWithoutPreservingPathSegments() {
        assertEquals("My_Video__final_.mp4", sanitizeImportedMediaName("../../My Video (final).mp4"))
        assertEquals("imported-media", sanitizeImportedMediaName("   "))
        assertEquals("recording.mp3", sanitizeImportedMediaName("C:\\Users\\User\\recording.mp3"))
    }

    @Test
    fun copiesTheCompleteSelectedMediaIntoPrivateStorage() {
        val bytes = ByteArray(65_537) { index -> (index % 239).toByte() }
        val destination = File.createTempFile("frameforge-import", ".bin")
        try {
            val copied = copyImportedMedia(ByteArrayInputStream(bytes), destination)

            assertEquals(bytes.size.toLong(), copied)
            assertArrayEquals(bytes, destination.readBytes())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun removesPartialFileWhenImportFails() {
        val destination = File.createTempFile("frameforge-empty-import", ".bin")
        val failure = runCatching {
            copyImportedMedia(ByteArrayInputStream(byteArrayOf()), destination)
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(!destination.exists() || destination.length() == 0L)
        destination.delete()
    }
}

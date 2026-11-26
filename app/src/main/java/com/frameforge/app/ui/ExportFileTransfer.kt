package com.frameforge.app.ui

import java.io.File
import java.io.OutputStream

/**
 * Copies a completed export to a caller-owned destination stream.
 *
 * The caller owns and closes [destination], allowing this helper to work with
 * Storage Access Framework document providers.
 */
internal fun copyExportFile(source: File, destination: OutputStream): Long {
    require(source.isFile && source.length() > 0L) {
        "The exported video is missing or empty."
    }
    val expectedLength = source.length()
    val copiedBytes = source.inputStream().use { input -> input.copyTo(destination) }
    check(copiedBytes == expectedLength) {
        "The exported video copy was incomplete."
    }
    return copiedBytes
}

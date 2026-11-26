package com.frameforge.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

internal data class ImportedMediaRecord(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val copiedLocally: Boolean
)

/**
 * Prefer a persisted document-provider read grant. If the provider refuses a
 * persistable grant, copy the selected file into app-private storage so the
 * library reference remains readable after the picker activity ends.
 */
internal fun persistOrCopyImportedMedia(
    context: Context,
    sourceUri: Uri,
    displayName: String
): Pair<Uri, Boolean> {
    if (sourceUri.scheme != "content") return sourceUri to false

    try {
        context.contentResolver.takePersistableUriPermission(
            sourceUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        return sourceUri to false
    } catch (_: Exception) {
        // Some document providers return readable but non-persistable URIs.
    }

    val directory = File(context.filesDir, "imported_media")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IOException("Could not create the private media import directory.")
    }
    val destination = File(
        directory,
        UUID.randomUUID().toString() + "_" + sanitizeImportedMediaName(displayName)
    )
    val input = context.contentResolver.openInputStream(sourceUri)
        ?: throw IOException("The selected media could not be opened for copying.")
    copyImportedMedia(input, destination)
    return Uri.fromFile(destination) to true
}

internal fun sanitizeImportedMediaName(displayName: String): String {
    val leafName = displayName.trim()
        .substringAfterLast('/')
        .substringAfterLast(92.toChar())
    val safeName = leafName
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.', '_')
        .take(100)
    return safeName.ifBlank { "imported-media" }
}

internal fun copyImportedMedia(input: InputStream, destination: File): Long {
    try {
        val copiedBytes = input.use { source ->
            destination.outputStream().use { target -> source.copyTo(target) }
        }
        if (copiedBytes <= 0L) throw IOException("The selected media file is empty.")
        return copiedBytes
    } catch (failure: Exception) {
        destination.delete()
        throw failure
    }
}

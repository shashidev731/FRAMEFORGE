package com.frameforge.app.ui

import com.frameforge.app.data.TimelineClipEntity
import com.frameforge.app.data.TimelineSnapshotCodec
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

internal data class TimelineExportRequest(
    val projectId: String,
    val clips: List<TimelineClipEntity>,
    val configuration: ExportConfiguration,
    val outputPath: String
)

internal object TimelineExportRequestCodec {
    private const val VERSION = "FFEXPORT1"

    fun encode(request: TimelineExportRequest): String {
        require(request.projectId.isNotBlank()) { "Export project ID is required." }
        require(request.clips.isNotEmpty()) { "At least one clip is required for export." }
        require(request.clips.all { it.projectId == request.projectId }) {
            "All export clips must belong to the same project."
        }
        require(request.outputPath.isNotBlank()) { "Export output path is required." }
        require(java.io.File(request.outputPath).isAbsolute) { "Export output path must be absolute." }

        val snapshot = TimelineSnapshotCodec.encode(request.clips, request.clips)
        return listOf(
            VERSION,
            encodeText(request.projectId),
            encodeText(snapshot),
            encodeText(request.configuration.resolution),
            request.configuration.frameRate.toString(),
            encodeText(request.configuration.codec),
            encodeText(request.configuration.quality),
            encodeText(request.outputPath)
        ).joinToString("|")
    }

    fun decode(payload: String): TimelineExportRequest {
        val fields = payload.split("|")
        require(fields.size == 8 && fields[0] == VERSION) { "Unsupported export request format." }
        val clips = TimelineSnapshotCodec.decode(decodeText(fields[2])).after
        val request = TimelineExportRequest(
            projectId = decodeText(fields[1]),
            clips = clips,
            configuration = ExportConfiguration(
                resolution = decodeText(fields[3]),
                frameRate = fields[4].toInt(),
                codec = decodeText(fields[5]),
                quality = decodeText(fields[6])
            ),
            outputPath = decodeText(fields[7])
        )
        require(request.projectId.isNotBlank() && request.clips.isNotEmpty()) {
            "Export request is missing its project or clips."
        }
        require(request.clips.all { it.projectId == request.projectId }) {
            "Export request contains clips from another project."
        }
        require(request.outputPath.isNotBlank() && java.io.File(request.outputPath).isAbsolute) {
            "Export request output path is invalid."
        }
        return request
    }

    private fun encodeText(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}


/**
 * Persists the private request file associated with the currently displayed WorkManager export.
 *
 * WorkInfo intentionally exposes progress and output data, not the original input Data. Keeping
 * the request path in an app-private marker lets cancellation clean up the immutable retry
 * snapshot without depending on an unavailable WorkInfo.inputData property.
 */
internal object TimelineExportJobMarker {
    private const val DIRECTORY = "export-jobs"
    private const val WORK_ID_FILE = "active-work-id.txt"
    private const val REQUEST_PATH_FILE = "active-request-path.txt"

    fun persist(filesDir: File, workId: UUID, requestFile: File) {
        val jobsDirectory = File(filesDir, DIRECTORY).canonicalFile
        require(requestFile.canonicalFile.parentFile == jobsDirectory) {
            "Export requests must stay inside the private export-jobs directory."
        }
        require(requestFile.extension == "ffjob" && requestFile.isFile) {
            "A complete export request file is required."
        }
        if (!jobsDirectory.exists() && !jobsDirectory.mkdirs()) {
            throw IOException("The export job directory could not be created.")
        }

        val workIdFile = File(jobsDirectory, WORK_ID_FILE)
        val previousWorkId = runCatching { workIdFile.readText(Charsets.UTF_8).trim() }.getOrNull()
        if (!previousWorkId.isNullOrBlank() && previousWorkId != workId.toString()) {
            cleanupCancelled(filesDir, previousWorkId)
        }

        writeAtomically(File(jobsDirectory, REQUEST_PATH_FILE), requestFile.canonicalPath)
        writeAtomically(workIdFile, workId.toString())
    }

    /**
     * Deletes the request snapshot only when the cancelled work item still owns the active marker.
     * A stale cancellation callback must never remove a newer export's retry input.
     */
    fun cleanupCancelled(filesDir: File, expectedWorkId: String): Boolean {
        val jobsDirectory = File(filesDir, DIRECTORY).canonicalFile
        val workIdFile = File(jobsDirectory, WORK_ID_FILE)
        val activeWorkId = runCatching { workIdFile.readText(Charsets.UTF_8).trim() }.getOrNull()
        if (activeWorkId != expectedWorkId) return false

        val requestPathFile = File(jobsDirectory, REQUEST_PATH_FILE)
        val requestPath = if (requestPathFile.exists()) {
            runCatching { requestPathFile.readText(Charsets.UTF_8).trim() }.getOrNull() ?: return false
        } else {
            null
        }
        if (!requestPath.isNullOrBlank()) {
            val requestFile = runCatching { File(requestPath).canonicalFile }.getOrNull() ?: return false
            if (requestFile.parentFile != jobsDirectory || requestFile.extension != "ffjob") return false
            if (requestFile.exists() && !requestFile.delete()) return false
        }

        val requestMarkerRemoved = !requestPathFile.exists() || requestPathFile.delete()
        val workMarkerRemoved = !workIdFile.exists() || workIdFile.delete()
        return requestMarkerRemoved && workMarkerRemoved
    }

    private fun writeAtomically(target: File, value: String) {
        val temporary = File(target.parentFile, "${target.name}.${UUID.randomUUID()}.tmp")
        temporary.writeText(value, Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IOException("The previous export marker could not be replaced.")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("The export marker could not be saved.")
        }
    }
}

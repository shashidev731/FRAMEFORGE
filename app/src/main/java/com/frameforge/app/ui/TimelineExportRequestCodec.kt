package com.frameforge.app.ui

import com.frameforge.app.data.TimelineClipEntity
import com.frameforge.app.data.TimelineSnapshotCodec
import java.nio.charset.StandardCharsets
import java.util.Base64

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

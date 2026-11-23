package com.frameforge.app.data

import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class TimelineSnapshotPair(
    val before: List<TimelineClipEntity>,
    val after: List<TimelineClipEntity>
)

internal object TimelineSnapshotCodec {
    private const val VERSION = "TSH1"

    fun encode(before: List<TimelineClipEntity>, after: List<TimelineClipEntity>): String =
        listOf(VERSION, encodeList(before), encodeList(after)).joinToString("|")

    fun decode(payload: String): TimelineSnapshotPair {
        val fields = payload.split("|")
        require(fields.size == 3 && fields[0] == VERSION) { "Unsupported timeline history snapshot" }
        return TimelineSnapshotPair(decodeList(fields[1]), decodeList(fields[2]))
    }

    private fun encodeList(clips: List<TimelineClipEntity>): String {
        val lines = clips.sortedWith(compareBy<TimelineClipEntity> { it.trackIndex }.thenBy { it.position })
            .joinToString("\n") { clip ->
                listOf(
                    encodeText(clip.clipId),
                    encodeText(clip.projectId),
                    encodeText(clip.mediaUri),
                    encodeText(clip.displayName),
                    encodeText(clip.mimeType),
                    clip.trackIndex.toString(),
                    clip.position.toString(),
                    clip.trimStartMs.toString(),
                    clip.trimEndMs?.toString() ?: "",
                    clip.durationMs.toString(),
                    clip.timelineStartMs.toString(),
                    clip.gapBeforeMs.toString(),
                    clip.audioVolume.toString(),
                    clip.createdAt.toString()
                ).joinToString("|")
            }
        return encodeText(lines)
    }

    private fun decodeList(encoded: String): List<TimelineClipEntity> {
        val text = decodeText(encoded)
        if (text.isEmpty()) return emptyList()
        return text.split("\n").map { line ->
            val fields = line.split("|")
            require(fields.size == 14) { "Malformed timeline history clip" }
            TimelineClipEntity(
                clipId = decodeText(fields[0]),
                projectId = decodeText(fields[1]),
                mediaUri = decodeText(fields[2]),
                displayName = decodeText(fields[3]),
                mimeType = decodeText(fields[4]),
                trackIndex = fields[5].toInt(),
                position = fields[6].toInt(),
                trimStartMs = fields[7].toLong(),
                trimEndMs = fields[8].takeIf { it.isNotEmpty() }?.toLong(),
                durationMs = fields[9].toLong(),
                timelineStartMs = fields[10].toLong(),
                gapBeforeMs = fields[11].toLong(),
                audioVolume = fields[12].toFloat(),
                createdAt = fields[13].toLong()
            )
        }
    }

    private fun encodeText(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}

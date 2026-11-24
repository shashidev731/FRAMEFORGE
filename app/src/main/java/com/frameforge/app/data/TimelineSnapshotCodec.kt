package com.frameforge.app.data

import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class TimelineSnapshotPair(
    val before: List<TimelineClipEntity>,
    val after: List<TimelineClipEntity>
)

internal object TimelineSnapshotCodec {
    private const val VERSION = "TSH3"
    private const val VERSION_TSH2 = "TSH2"
    private const val LEGACY_VERSION = "TSH1"

    fun encode(before: List<TimelineClipEntity>, after: List<TimelineClipEntity>): String =
        listOf(VERSION, encodeList(before), encodeList(after)).joinToString("|")

    fun decode(payload: String): TimelineSnapshotPair {
        val fields = payload.split("|")
        require(fields.size == 3 && (fields[0] == VERSION || fields[0] == VERSION_TSH2 || fields[0] == LEGACY_VERSION)) {
            "Unsupported timeline history snapshot"
        }
        return TimelineSnapshotPair(decodeList(fields[1], fields[0]), decodeList(fields[2], fields[0]))
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
                    clip.audioFadeInMs.toString(),
                    clip.audioFadeOutMs.toString(),
                    clip.speed.toString(),
                    encodeText(clip.filterEffect),
                    clip.rotationDegrees.toString(),
                    encodeText(clip.textOverlay),
                    clip.createdAt.toString()
                ).joinToString("|")
            }
        return encodeText(lines)
    }

    private fun decodeList(encoded: String, version: String): List<TimelineClipEntity> {
        val text = decodeText(encoded)
        if (text.isEmpty()) return emptyList()
        return text.split("\n").map { line ->
            val fields = line.split("|")
            val expectedFields = when (version) {
                LEGACY_VERSION -> 14
                VERSION_TSH2 -> 16
                else -> 20
            }
            require(fields.size == expectedFields) { "Malformed timeline history clip" }
            when (version) {
                LEGACY_VERSION -> TimelineClipEntity(
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
                    audioFadeInMs = 0L,
                    audioFadeOutMs = 0L,
                    speed = 1.0f,
                    filterEffect = "NONE",
                    rotationDegrees = 0,
                    textOverlay = "",
                    createdAt = fields[13].toLong()
                )
                VERSION_TSH2 -> TimelineClipEntity(
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
                    audioFadeInMs = fields[13].toLong(),
                    audioFadeOutMs = fields[14].toLong(),
                    speed = 1.0f,
                    filterEffect = "NONE",
                    rotationDegrees = 0,
                    textOverlay = "",
                    createdAt = fields[15].toLong()
                )
                else -> TimelineClipEntity(
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
                    audioFadeInMs = fields[13].toLong(),
                    audioFadeOutMs = fields[14].toLong(),
                    speed = fields[15].toFloatOrNull() ?: 1.0f,
                    filterEffect = decodeText(fields[16]),
                    rotationDegrees = fields[17].toIntOrNull() ?: 0,
                    textOverlay = decodeText(fields[18]),
                    createdAt = fields[19].toLong()
                )
            }
        }
    }

    private fun encodeText(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}

package com.frameforge.app.ui

import android.net.Uri
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ProgressHolder
import java.io.File
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.ui.PlayerView
import com.frameforge.app.data.TimelineClipEntity
import com.frameforge.app.ui.theme.Line
import com.frameforge.app.ui.theme.Muted
import com.frameforge.app.ui.theme.SurfaceWhite

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
fun TimelineCompositionPreview(clips: List<TimelineClipEntity>) {
    val context = LocalContext.current
    val compositionResult = remember(clips) {
        runCatching { buildTimelineComposition(clips) }
    }
    val composition = compositionResult.getOrNull()
    val player = remember(context) { CompositionPlayer.Builder(context).build() }
    var exportTransformer by remember { mutableStateOf<Transformer?>(null) }
    var exportFile by remember { mutableStateOf<File?>(null) }
    var activeOutputFile by remember { mutableStateOf<File?>(null) }
    var exportStatus by remember { mutableStateOf("Ready to export") }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportProgress by remember { mutableStateOf(0) }
    var exportCancelled by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose {
            player.release()
            if (exportTransformer != null) {
                exportCancelled = true
                runCatching { exportTransformer?.cancel() }
            }
            // Let the Transformer finish cancellation before callbacks clean up its output.
            activeOutputFile = null
        }
    }

    LaunchedEffect(exportTransformer) {
        val current = exportTransformer ?: return@LaunchedEffect
        val holder = ProgressHolder()
        while (exportTransformer === current) {
            val state = runCatching { current.getProgress(holder) }.getOrDefault(Transformer.PROGRESS_STATE_UNAVAILABLE)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) exportProgress = holder.progress
            delay(500)
        }
    }

    LaunchedEffect(composition) {
        if (composition != null) {
            player.pause()
            player.setComposition(composition)
            player.prepare()
        } else {
            player.stop()
        }
    }

    val visualCount = clips.count { it.trackIndex == 0 && it.durationMs > 0L }
    val audioCount = clips.count { it.trackIndex == 1 && it.durationMs > 0L }
    val unsupportedTracks = clips.any { it.trackIndex > 1 }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Timeline preview", style = MaterialTheme.typography.titleMedium)
            when {
                compositionResult.isFailure -> Text(
                    "Preview could not be prepared: ${compositionResult.exceptionOrNull()?.message ?: "unsupported media"}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                composition == null -> Text(
                    "Add at least one visual clip with a readable duration to preview the timeline.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                else -> AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).also { view ->
                            view.useController = true
                            view.player = player
                        }
                    },
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxWidth().height(230.dp)
                )
            }
            Text(
                "$visualCount visual clip(s) • $audioCount audio clip(s)" +
                    if (audioCount == 0) " • no separate audio track" else "",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            if (unsupportedTracks) {
                Text(
                    "Additional overlay tracks are saved, but this preview currently supports the main visual track and one audio track.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (composition != null) {
                Text(
                    "Preview uses trimmed clip ranges. Crossfades and extra overlay tracks are not enabled yet.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = exportTransformer == null,
                        onClick = {
                            val outputDirectory = File(
                                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
                                "FRAMEFORGE"
                            ).apply { mkdirs() }
                            val output = File(outputDirectory, "FRAMEFORGE_${System.currentTimeMillis()}.mp4")
                            exportFile = null
                            exportCancelled = false
                            exportError = null
                            exportProgress = 0
                            exportStatus = "Starting export…"
                            activeOutputFile = output
                            val transformer = Transformer.Builder(context)
                                .addListener(object : Transformer.Listener {
                                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                        activeOutputFile = null
                                        if (exportCancelled) {
                                            output.delete()
                                            exportFile = null
                                            exportStatus = "Export cancelled"
                                            exportError = null
                                        } else if (output.exists() && output.length() > 0L) {
                                            exportFile = output
                                            exportProgress = 100
                                            exportStatus = "Export complete"
                                            exportError = null
                                        } else {
                                            output.delete()
                                            exportFile = null
                                            exportError = "Export finished without a valid output file."
                                            exportStatus = "Export failed"
                                        }
                                        exportTransformer = null
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        output.delete()
                                        activeOutputFile = null
                                        if (exportCancelled) {
                                            exportStatus = "Export cancelled"
                                            exportError = null
                                        } else {
                                            exportError = exportException.message ?: "The timeline could not be exported."
                                            exportStatus = "Export failed"
                                        }
                                        exportTransformer = null
                                    }
                                })
                                .build()
                            exportTransformer = transformer
                            runCatching { transformer.start(composition, output.absolutePath) }
                                .onSuccess { exportStatus = "Exporting MP4…" }
                                .onFailure {
                                    output.delete()
                                    activeOutputFile = null
                                    exportError = it.message ?: "Unable to start export."
                                    exportStatus = "Export failed"
                                    exportTransformer = null
                                }
                        }
                    ) { Text(if (exportTransformer == null) "Export MP4" else "Exporting…") }
                    if (exportTransformer != null) {
                        OutlinedButton(onClick = {
                            exportCancelled = true
                            runCatching { exportTransformer?.cancel() }
                            exportFile = null
                            exportError = null
                            exportStatus = "Cancelling export…"
                        }) { Text("Cancel") }
                    }
                    if (exportFile?.exists() == true) {
                        OutlinedButton(onClick = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                exportFile!!
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share FRAMEFORGE export"))
                        }) { Text("Share") }
                    }
                }
                Text(
                    if (exportTransformer != null) "$exportStatus • $exportProgress%"
                    else exportStatus,
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (exportError != null) {
                    Text(exportError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildTimelineComposition(clips: List<TimelineClipEntity>): Composition? {
    val visualClips = clips.asSequence()
        .filter { it.trackIndex == 0 && (it.mimeType.startsWith("video/") || it.mimeType.startsWith("image/")) }
        .sortedBy { it.timelineStartMs }
        .toList()

    val audioClips = clips.asSequence()
        .filter { it.trackIndex == 1 && it.mimeType.startsWith("audio/") }
        .sortedBy { it.timelineStartMs }
        .toList()

    if (visualClips.none { visibleDurationMs(it) > 0L }) return null

    val visualSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
    var visualCursorMs = 0L
    visualClips.forEach { clip ->
        val durationMs = visibleDurationMs(clip)
        if (durationMs > 0L) {
            require(clip.timelineStartMs >= visualCursorMs) {
                "Visual clips overlap. Overlapping clip compositing is not supported yet."
            }
            val gapMs = clip.timelineStartMs - visualCursorMs
            if (gapMs > 0L) visualSequence.addGap(gapMs * 1_000L)
            visualSequence.addItem(buildEditedTimelineItem(clip, durationMs))
            visualCursorMs = clip.timelineStartMs + durationMs
        }
    }

    val sequences = buildList {
        add(visualSequence.build())
        if (audioClips.any { visibleDurationMs(it) > 0L }) {
            val audioSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            var audioCursorMs = 0L
            audioClips.forEach { clip ->
                val durationMs = visibleDurationMs(clip)
                if (durationMs > 0L) {
                    require(clip.timelineStartMs >= audioCursorMs) {
                        "Audio clips overlap. Overlapping audio mixing is not supported yet."
                    }
                    val gapMs = clip.timelineStartMs - audioCursorMs
                    if (gapMs > 0L) audioSequence.addGap(gapMs * 1_000L)
                    audioSequence.addItem(buildEditedTimelineItem(clip, durationMs))
                    audioCursorMs = clip.timelineStartMs + durationMs
                }
            }
            add(audioSequence.build())
        }
    }
    return Composition.Builder(sequences).build()
}

private fun normalizedTrimStartMs(clip: TimelineClipEntity): Long = clip.trimStartMs.coerceIn(0L, clip.durationMs.coerceAtLeast(0L))

private fun normalizedTrimEndMs(clip: TimelineClipEntity): Long = (clip.trimEndMs ?: clip.durationMs).coerceIn(normalizedTrimStartMs(clip), clip.durationMs.coerceAtLeast(0L))

private fun visibleDurationMs(clip: TimelineClipEntity): Long = (normalizedTrimEndMs(clip) - normalizedTrimStartMs(clip)).coerceAtLeast(0L)

@OptIn(UnstableApi::class)
private fun buildEditedTimelineItem(clip: TimelineClipEntity, durationMs: Long): EditedMediaItem {
    val mediaItemBuilder = MediaItem.Builder()
        .setUri(Uri.parse(clip.mediaUri))
        .setMimeType(clip.mimeType)

    if (clip.mimeType.startsWith("image/")) {
        mediaItemBuilder.setImageDurationMs(durationMs)
    } else {
        val startMs = normalizedTrimStartMs(clip)
        val endMs = normalizedTrimEndMs(clip)
        mediaItemBuilder.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
    }
    return EditedMediaItem.Builder(mediaItemBuilder.build())
        .setDurationUs(durationMs * 1_000L)
        .build()
}

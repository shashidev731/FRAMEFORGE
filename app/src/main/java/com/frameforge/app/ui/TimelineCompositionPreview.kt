package com.frameforge.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.SpannableString
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition as MediaComposition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.frameforge.app.data.*
import com.frameforge.app.ui.theme.Line
import com.frameforge.app.ui.theme.Muted
import com.frameforge.app.ui.theme.SurfaceWhite
import kotlinx.coroutines.delay
import java.io.File

data class ExportConfiguration(
    val resolution: String = "1080p",
    val frameRate: Int = 30,
    val codec: String = "H.264",
    val quality: String = "Standard"
)

@OptIn(UnstableApi::class)
@Composable
fun TimelineCompositionPreview(
    clips: List<TimelineClipEntity>,
    onPlayheadChange: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var exportConfig by remember { mutableStateOf(ExportConfiguration()) }

    val compositionResult: Result<MediaComposition?> = remember(clips) {
        runCatching { buildTimelineComposition(clips) }
    }
    val composition: MediaComposition? = compositionResult.getOrNull()
    val player = remember(context) { CompositionPlayer.Builder(context).build() }
    var exportTransformer by remember { mutableStateOf<Transformer?>(null) }
    var exportFile by remember { mutableStateOf<File?>(null) }
    var activeOutputFile by remember { mutableStateOf<File?>(null) }
    var exportStatus by remember { mutableStateOf("Ready to export") }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportProgress by remember { mutableStateOf(0) }
    var exportCancelled by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableStateOf(0L) }
    var previewDurationMs by remember { mutableStateOf(0L) }

    DisposableEffect(player) {
        onDispose {
            player.release()
            if (exportTransformer != null) {
                exportCancelled = true
                runCatching { exportTransformer?.cancel() }
            }
            activeOutputFile = null
        }
    }

    LaunchedEffect(player, composition) {
        previewPositionMs = 0L
        previewDurationMs = 0L
        if (composition != null) {
            while (true) {
                runCatching { player.currentPosition }.getOrNull()?.let {
                    previewPositionMs = it.coerceAtLeast(0L)
                    onPlayheadChange(previewPositionMs)
                }
                runCatching { player.duration }.getOrNull()?.let { if (it > 0L) previewDurationMs = it }
                delay(250)
            }
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
    val audioCount = clips.count { it.trackIndex >= 1 && it.mimeType.startsWith("audio/") }
    val audioTracksCount = clips.filter { it.trackIndex >= 1 && it.mimeType.startsWith("audio/") }.map { it.trackIndex }.distinct().size

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Timeline preview & composition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (composition != null && exportTransformer == null) {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Tune, "Export settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

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
                "$visualCount visual clip(s) • $audioCount audio clip(s) across $audioTracksCount audio track(s)" +
                    if (audioCount == 0) " (no audio)" else " (mixed simultaneously)",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )

            if (composition != null) {
                if (previewDurationMs > 0L) {
                    Text(
                        "Playhead • ${formatTimelineDuration(previewPositionMs)} / ${formatTimelineDuration(previewDurationMs)}",
                        color = Muted,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = previewPositionMs.toFloat().coerceIn(0f, previewDurationMs.toFloat()),
                        onValueChange = { position ->
                            previewPositionMs = position.toLong()
                            onPlayheadChange(position.toLong())
                            runCatching { player.seekTo(position.toLong()) }
                        },
                        valueRange = 0f..previewDurationMs.toFloat()
                    )
                } else {
                    Text("Playhead controls appear after preview duration loads.", color = Muted, style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = exportTransformer == null,
                        onClick = {
                            val outputDirectory = File(
                                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
                                "FRAMEFORGE"
                            ).apply { mkdirs() }
                            val output = File(outputDirectory, "FRAMEFORGE_${exportConfig.resolution}_${System.currentTimeMillis()}.mp4")
                            exportFile = null
                            exportCancelled = false
                            exportError = null
                            exportProgress = 0
                            exportStatus = "Starting export…"
                            activeOutputFile = output

                            val videoMime = if (exportConfig.codec == "H.265") MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
                            val transformerBuilder = Transformer.Builder(context)
                                .setVideoMimeType(videoMime)
                                .addListener(object : Transformer.Listener {
                                    override fun onCompleted(comp: MediaComposition, exportResult: ExportResult) {
                                        activeOutputFile = null
                                        if (exportCancelled) {
                                            output.delete()
                                            exportFile = null
                                            exportStatus = "Export cancelled"
                                            exportError = null
                                        } else if (output.exists() && output.length() > 0L) {
                                            exportFile = output
                                            exportProgress = 100
                                            exportStatus = "Export complete (${(output.length() / 1024 / 1024).coerceAtLeast(1)} MB)"
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
                                        comp: MediaComposition,
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

                            val transformer = transformerBuilder.build()
                            exportTransformer = transformer
                            runCatching { transformer.start(composition, output.absolutePath) }
                                .onSuccess { exportStatus = "Exporting MP4 (${exportConfig.resolution} ${exportConfig.codec})…" }
                                .onFailure {
                                    output.delete()
                                    activeOutputFile = null
                                    exportError = it.message ?: "Unable to start export."
                                    exportStatus = "Export failed"
                                    exportTransformer = null
                                }
                        }
                    ) {
                        Icon(Icons.Default.MovieCreation, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (exportTransformer == null) "Export ${exportConfig.resolution}" else "Exporting…")
                    }

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
                        Button(
                            onClick = {
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
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }

                if (exportTransformer != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { exportProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("$exportStatus • $exportProgress%", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(exportStatus, color = Muted, style = MaterialTheme.typography.bodySmall)
                }

                if (exportError != null) {
                    Text(exportError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Configuration", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Resolution", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("720p", "1080p", "4K").forEach { res ->
                            FilterChip(
                                selected = exportConfig.resolution == res,
                                onClick = { exportConfig = exportConfig.copy(resolution = res) },
                                label = { Text(res) }
                            )
                        }
                    }

                    Text("Frame Rate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(24 to "24 fps", 30 to "30 fps", 60 to "60 fps").forEach { (fps, label) ->
                            FilterChip(
                                selected = exportConfig.frameRate == fps,
                                onClick = { exportConfig = exportConfig.copy(frameRate = fps) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text("Video Codec", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("H.264", "H.265").forEach { codec ->
                            FilterChip(
                                selected = exportConfig.codec == codec,
                                onClick = { exportConfig = exportConfig.copy(codec = codec) },
                                label = { Text(codec) }
                            )
                        }
                    }

                    Text("Quality Preset", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("High", "Standard", "Low").forEach { q ->
                            FilterChip(
                                selected = exportConfig.quality == q,
                                onClick = { exportConfig = exportConfig.copy(quality = q) },
                                label = { Text(q) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showExportDialog = false }) { Text("Save Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private const val AUDIO_TRACK_TYPE = 1
private const val VIDEO_TRACK_TYPE = 2

@OptIn(UnstableApi::class)
private fun buildTimelineComposition(clips: List<TimelineClipEntity>): MediaComposition? {
    val visualClips = clips.asSequence()
        .filter { it.trackIndex == 0 && (it.mimeType.startsWith("video/") || it.mimeType.startsWith("image/")) }
        .sortedBy { it.timelineStartMs }
        .toList()

    // Multitrack audio: group by trackIndex for all tracks >= 1 containing audio
    val audioClipsByTrack = clips.asSequence()
        .filter { it.trackIndex >= 1 && it.mimeType.startsWith("audio/") }
        .groupBy { it.trackIndex }

    if (visualClips.none { effectiveClipDurationMs(it) > 0L }) return null

    val visualSequence = EditedMediaItemSequence.Builder(setOf(VIDEO_TRACK_TYPE))
    var visualCursorMs = 0L
    visualClips.forEach { clip ->
        val durationMs = effectiveClipDurationMs(clip)
        if (durationMs > 0L) {
            val gapMs = (clip.timelineStartMs - visualCursorMs).coerceAtLeast(0L)
            if (gapMs > 0L) visualSequence.addGap(gapMs * 1_000L)
            visualSequence.addItem(buildEditedTimelineItem(clip, durationMs))
            visualCursorMs = maxOf(visualCursorMs, saturatingTimelineEndMs(clip.timelineStartMs, durationMs))
        }
    }

    val sequences = buildList {
        add(visualSequence.build())

        // Add each distinct audio track as its own concurrent sequence for multitrack mixing
        audioClipsByTrack.keys.sorted().forEach { trackIndex ->
            val trackClips = audioClipsByTrack.getValue(trackIndex).sortedBy { it.timelineStartMs }
            if (trackClips.any { visibleClipDurationMs(it) > 0L }) {
                val audioSequence = EditedMediaItemSequence.Builder(setOf(AUDIO_TRACK_TYPE))
                var audioCursorMs = 0L
                trackClips.forEach { clip ->
                    val durationMs = visibleClipDurationMs(clip)
                    if (durationMs > 0L) {
                        val gapMs = (clip.timelineStartMs - audioCursorMs).coerceAtLeast(0L)
                        if (gapMs > 0L) audioSequence.addGap(gapMs * 1_000L)
                        audioSequence.addItem(buildEditedTimelineItem(clip, durationMs))
                        audioCursorMs = maxOf(audioCursorMs, saturatingTimelineEndMs(clip.timelineStartMs, durationMs))
                    }
                }
                add(audioSequence.build())
            }
        }
    }

    return MediaComposition.Builder(sequences).build()
}

@OptIn(UnstableApi::class)
private fun buildEditedTimelineItem(clip: TimelineClipEntity, durationMs: Long): EditedMediaItem {
    val mediaItemBuilder = MediaItem.Builder()
        .setUri(Uri.parse(clip.mediaUri))
        .setMimeType(clip.mimeType)

    val isImage = clip.mimeType.startsWith("image/")
    if (isImage) {
        mediaItemBuilder.setImageDurationMs(durationMs)
    } else {
        val startMs = normalizedClipTrimStartMs(clip)
        val endMs = normalizedClipTrimEndMs(clip)
        mediaItemBuilder.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
    }

    val itemBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
    if (!isImage && clip.durationMs > 0L) {
        // Media3 expects the full source duration here, before clipping and speed effects.
        val sourceDurationUs = clip.durationMs.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
        itemBuilder.setDurationUs(sourceDurationUs)
    }

    val audioEffects = mutableListOf<AudioProcessor>()
    val videoEffects = mutableListOf<Effect>()

    // Audio effects: volume and fades
    if (clip.mimeType.startsWith("audio/")) {
        val volume = normalizedClipAudioVolume(clip.audioVolume)
        val (fadeInMs, fadeOutMs) = normalizedClipAudioFades(durationMs, clip.audioFadeInMs, clip.audioFadeOutMs)
        if (volume != 1.0f || fadeInMs > 0L || fadeOutMs > 0L) {
            val gainBuilder = DefaultGainProvider.Builder(volume)
            if (fadeInMs > 0L) {
                gainBuilder.addFadeAt(0L, fadeInMs * 1_000L, DefaultGainProvider.FADE_IN_LINEAR)
            }
            if (fadeOutMs > 0L) {
                gainBuilder.addFadeAt((durationMs - fadeOutMs).coerceAtLeast(0L) * 1_000L, fadeOutMs * 1_000L, DefaultGainProvider.FADE_OUT_LINEAR)
            }
            audioEffects.add(GainProcessor(gainBuilder.build()))
        }
    }

    // Video effects: rotation, filter color, speed, and text overlays
    if (clip.mimeType.startsWith("video/") || clip.mimeType.startsWith("image/")) {
        if (clip.rotationDegrees != 0) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(clip.rotationDegrees.toFloat())
                    .build()
            )
        }

        when (clip.filterEffect.uppercase()) {
            "BW", "GRAYSCALE" -> videoEffects.add(RgbFilter.createGrayscaleFilter())
            "INVERT" -> videoEffects.add(RgbFilter.createInvertedFilter())
            "CONTRAST" -> videoEffects.add(Contrast(0.35f))
            "BRIGHT" -> videoEffects.add(Brightness(0.12f))
            "WARM" -> videoEffects.add(RgbAdjustment.Builder().setRedScale(1.15f).setBlueScale(0.85f).build())
            "COOL" -> videoEffects.add(RgbAdjustment.Builder().setRedScale(0.85f).setBlueScale(1.15f).build())
            "SEPIA" -> videoEffects.add(RgbAdjustment.Builder().setRedScale(1.2f).setGreenScale(1.0f).setBlueScale(0.8f).build())
        }

        if (clip.mimeType.startsWith("video/") && normalizedClipSpeed(clip.speed) != 1.0f) {
            videoEffects.add(SpeedChangeEffect(normalizedClipSpeed(clip.speed)))
        }

        if (clip.textOverlay.isNotBlank()) {
            val textOverlay = TextOverlay.createStaticTextOverlay(SpannableString(clip.textOverlay))
            videoEffects.add(OverlayEffect(listOf(textOverlay)))
        }
    }

    if (audioEffects.isNotEmpty() || videoEffects.isNotEmpty()) {
        itemBuilder.setEffects(Effects(audioEffects, videoEffects))
    }

    return itemBuilder.build()
}

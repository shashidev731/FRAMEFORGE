package com.frameforge.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.SpannableString
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.FrameDropEffect
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
import androidx.media3.ui.PlayerView
import com.frameforge.app.data.*
import com.frameforge.app.ui.theme.Line
import com.frameforge.app.ui.theme.Muted
import com.frameforge.app.ui.theme.SurfaceWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class ExportConfiguration(
    val resolution: String = "1080p",
    val frameRate: Int = 30,
    val codec: String = "H.264",
    val quality: String = "Standard"
)

@UnstableApi
@ExperimentalApi
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
    var exportWorkId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportStartError by remember { mutableStateOf<String?>(null) }
    var isQueueingExport by remember { mutableStateOf(false) }
    var isRestoringExport by remember { mutableStateOf(true) }
    val workManager = remember(context) { WorkManager.getInstance(context) }
    LaunchedEffect(context) {
        val marker = File(context.filesDir, "export-jobs/active-work-id.txt")
        val restoredId = withContext(Dispatchers.IO) {
            runCatching { marker.takeIf { it.exists() }?.readText()?.trim() }.getOrNull()
        }
        if (!restoredId.isNullOrBlank() && runCatching { UUID.fromString(restoredId) }.isSuccess) {
            exportWorkId = restoredId
        }
        isRestoringExport = false
    }
    val exportWorkInfoFlow = remember(exportWorkId) {
        val id = exportWorkId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        id?.let { workManager.getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }
    val exportWorkInfo by exportWorkInfoFlow.collectAsState(initial = null)
    val isWorkRunning = exportWorkInfo?.state?.let {
        it == androidx.work.WorkInfo.State.ENQUEUED ||
            it == androidx.work.WorkInfo.State.BLOCKED ||
            it == androidx.work.WorkInfo.State.RUNNING
    } == true
    val isExportActive = isRestoringExport || isQueueingExport || isWorkRunning
    val exportProgress = if (exportWorkInfo?.state == androidx.work.WorkInfo.State.SUCCEEDED) {
        100
    } else {
        exportWorkInfo?.progress?.getInt(TimelineExportWorker.KEY_PROGRESS, 0)?.coerceIn(0, 100) ?: 0
    }
    val exportFile = exportWorkInfo?.outputData?.getString(TimelineExportWorker.KEY_OUTPUT_PATH)
        ?.let(::File)?.takeIf { it.exists() }
    val exportError = exportWorkInfo?.outputData?.getString(TimelineExportWorker.KEY_ERROR) ?: exportStartError
    val exportStatus = when {
        isRestoringExport -> "Restoring export status…"
        isQueueingExport -> "Preparing export…"
        exportWorkInfo?.state == androidx.work.WorkInfo.State.ENQUEUED -> "Export queued…"
        exportWorkInfo?.state == androidx.work.WorkInfo.State.BLOCKED -> "Waiting to export…"
        exportWorkInfo?.state == androidx.work.WorkInfo.State.RUNNING -> "Exporting MP4…"
        exportWorkInfo?.state == androidx.work.WorkInfo.State.SUCCEEDED -> exportFile?.let {
            "Export complete (${(it.length() / 1024 / 1024).coerceAtLeast(1)} MB)"
        } ?: "Export completed, but the output file is unavailable."
        exportWorkInfo?.state == androidx.work.WorkInfo.State.FAILED -> "Export failed"
        exportWorkInfo?.state == androidx.work.WorkInfo.State.CANCELLED -> "Export cancelled"
        else -> if (exportWorkId == null) "Ready to export" else "Previous export status is unavailable; you can start a new export."
    }

    LaunchedEffect(exportWorkInfo?.id, exportWorkInfo?.state) {
        if (exportWorkInfo?.state == androidx.work.WorkInfo.State.CANCELLED) {
            val cancelledWorkId = exportWorkInfo?.id?.toString()
            if (cancelledWorkId != null) {
                withContext(Dispatchers.IO) {
                    TimelineExportJobMarker.cleanupCancelled(context.filesDir, cancelledWorkId)
                }
            }
        }
    }
    var previewPositionMs by remember { mutableStateOf(0L) }
    var previewDurationMs by remember { mutableStateOf(0L) }
    val saveScope = rememberCoroutineScope()
    var saveStatus by remember { mutableStateOf<String?>(null) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    val saveExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destinationUri ->
        val source = pendingSaveFile
        pendingSaveFile = null
        if (destinationUri != null && source != null) {
            saveScope.launch {
                saveStatus = "Saving copy…"
                try {
                    val copiedBytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                            copyExportFile(source, output)
                        } ?: throw IOException("The selected destination could not be opened.")
                    }
                    saveStatus = "Saved to selected location (" +
                        (copiedBytes / 1024 / 1024).coerceAtLeast(1) + " MB)"
                } catch (failure: Exception) {
                    withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.delete(destinationUri, null, null) }
                    }
                    saveStatus = "Save failed: " + (failure.message ?: "Unable to copy the exported video.")
                }
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            // Exports are owned by WorkManager and intentionally continue if this screen leaves.
            player.release()
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

    LaunchedEffect(composition) {
        if (composition != null) {
            player.pause()
            player.setComposition(composition)
            player.prepare()
        } else {
            player.stop()
        }
    }

    val visualCount = clips.count {
        isVisualTimelineClip(it) && (it.trackIndex == 0 || it.trackIndex >= FIRST_OVERLAY_TRACK_INDEX) && it.durationMs > 0L
    }
    val audioCount = clips.count {
        isAudioTimelineClip(it) && it.trackIndex in 1..LAST_AUDIO_TRACK_INDEX
    }
    val audioTracksCount = groupAudioTimelineTracks(clips).size

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
                if (composition != null && !isExportActive) {
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
                        enabled = !isExportActive,
                        onClick = {
                            val exportCompositionResult = runCatching { buildTimelineComposition(clips, exportConfig) }
                            val exportComposition = exportCompositionResult.getOrNull()
                            if (exportCompositionResult.isFailure || exportComposition == null) {
                                exportStartError = exportCompositionResult.exceptionOrNull()?.message
                                    ?: "The timeline could not be prepared for the selected export settings."
                                return@Button
                            }
                            val projectId = clips.firstOrNull()?.projectId
                            if (projectId.isNullOrBlank() || clips.any { it.projectId != projectId }) {
                                exportStartError = "Select clips from one project before exporting."
                                return@Button
                            }
                            val outputDirectory = File(
                                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
                                "FRAMEFORGE"
                            )
                            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                                exportStartError = "The export folder could not be created."
                                return@Button
                            }
                            val output = File(
                                outputDirectory,
                                "FRAMEFORGE_${exportConfig.resolution}_${System.currentTimeMillis()}.mp4"
                            )
                            val requestFile = File(
                                context.filesDir,
                                "export-jobs/${UUID.randomUUID()}.ffjob"
                            )
                            val request = TimelineExportRequest(
                                projectId = projectId,
                                clips = clips,
                                configuration = exportConfig,
                                outputPath = output.absolutePath
                            )
                            exportStartError = null
                            saveStatus = null
                            isQueueingExport = true
                            saveScope.launch {
                                var queuedWorkId: String? = null
                                try {
                                    withContext(Dispatchers.IO) {
                                        val requestDirectory = requestFile.parentFile
                                            ?: throw IOException("The export request folder is unavailable.")
                                        if (!requestDirectory.exists() && !requestDirectory.mkdirs()) {
                                            throw IOException("The export request folder could not be created.")
                                        }
                                        requestFile.writeText(
                                            TimelineExportRequestCodec.encode(request),
                                            Charsets.UTF_8
                                        )
                                    }
                                    val workRequest = OneTimeWorkRequestBuilder<TimelineExportWorker>()
                                        .setInputData(workDataOf(TimelineExportWorker.KEY_REQUEST_PATH to requestFile.absolutePath))
                                        .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                                        .addTag(TimelineExportWorker.TAG)
                                        .build()
                                    queuedWorkId = workRequest.id.toString()
                                    withContext(Dispatchers.IO) {
                                        TimelineExportJobMarker.persist(context.filesDir, workRequest.id, requestFile)
                                    }
                                    exportWorkId = workRequest.id.toString()
                                    workManager.enqueueUniqueWork(
                                        "frameforge-export",
                                        ExistingWorkPolicy.REPLACE,
                                        workRequest
                                    )
                                } catch (failure: Exception) {
                                    withContext(Dispatchers.IO) {
                                        requestFile.delete()
                                        queuedWorkId?.let {
                                            TimelineExportJobMarker.cleanupCancelled(context.filesDir, it)
                                        }
                                    }
                                    exportStartError = failure.message ?: "Unable to queue the export."
                                } finally {
                                    isQueueingExport = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.MovieCreation, null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                isRestoringExport -> "Restoring…"
                                isQueueingExport -> "Preparing…"
                                isWorkRunning -> "Exporting…"
                                else -> "Export ${exportConfig.resolution}"
                            }
                        )
                    }

                    if (isWorkRunning) {
                        OutlinedButton(onClick = {
                            exportWorkId?.let { id ->
                                runCatching { workManager.cancelWorkById(UUID.fromString(id)) }
                            }
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

                if (exportFile?.exists() == true) {
                    OutlinedButton(
                        onClick = {
                            exportFile?.let { source ->
                                pendingSaveFile = source
                                saveExportLauncher.launch(source.name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save copy to device…")
                    }
                }
                saveStatus?.let { status ->
                    Text(
                        status,
                        color = if (status.startsWith("Save failed")) MaterialTheme.colorScheme.error else Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isWorkRunning) {
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
                    Text(
                        "The selected rate drops source frames when needed. It does not create new frames when the source is slower.",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall
                    )

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
                    Text(
                        "Resolution and bitrate are requested from the device encoder. Unsupported profiles may fall back to a lower output size.",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall
                    )
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

@UnstableApi
@ExperimentalApi
internal fun buildTimelineComposition(
    clips: List<TimelineClipEntity>,
    exportConfiguration: ExportConfiguration? = null
): MediaComposition? {
    // Reuse this filtered order for both Media3 sequences and compositor input IDs.
    // Tracks with no visible frames must not shift the sequence-to-track mapping.
    val visualTracks = composableVisualTimelineTracks(clips)
    val audioTracks = groupAudioTimelineTracks(clips)

    // An audio-only project has no video composition to preview or export.
    if (visualTracks.isEmpty()) return null

    val sequences = buildList {
        // The active primary track (track 0 when present, otherwise the first active visual track)
        // is the base. Remaining visual sequences are composited above it in ascending order.
        visualTracks.forEach { track ->
            buildTimelineTrackSequence(track.clips, VIDEO_TRACK_TYPE, ::effectiveClipDurationMs)
                ?.let(::add)
        }

        // Audio tracks occupy 1..99 and are mixed concurrently with the visual layers.
        audioTracks.forEach { track ->
            buildTimelineTrackSequence(track.clips, AUDIO_TRACK_TYPE, ::visibleClipDurationMs)
                ?.let(::add)
        }
    }

    if (sequences.isEmpty()) return null
    val builder = MediaComposition.Builder(sequences)
    // Visual sequence order matches the sorted visualTracks list; the
    // compositor positions overlay lanes as picture-in-picture layers.
    builder.setVideoCompositorSettings(TimelineVideoCompositorSettings(visualTracks))
    if (exportConfiguration != null) {
        val outputSpec = exportOutputSpec(exportConfiguration)
        val videoEffects: List<Effect> = listOf(
            Presentation.createForShortSide(outputSpec.shortSidePx),
            FrameDropEffect.createDefaultFrameDropEffect(outputSpec.targetFrameRate.toFloat())
        )
        builder.setEffects(Effects(emptyList<AudioProcessor>(), videoEffects))
    }
    return builder.build()
}

@UnstableApi
@ExperimentalApi
private fun buildTimelineTrackSequence(
    clips: List<TimelineClipEntity>,
    trackType: Int,
    durationForClip: (TimelineClipEntity) -> Long
): EditedMediaItemSequence? {
    val validClips = clips.filter { durationForClip(it) > 0L }.sortedBy { it.timelineStartMs }
    if (validClips.isEmpty()) return null

    val sequence = EditedMediaItemSequence.Builder(setOf(trackType))
    var cursorMs = 0L
    validClips.forEach { clip ->
        val durationMs = durationForClip(clip)
        val gapMs = (clip.timelineStartMs - cursorMs).coerceAtLeast(0L)
        if (gapMs > 0L) {
            val gapUs = gapMs.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
            sequence.addGap(gapUs)
        }
        sequence.addItem(buildEditedTimelineItem(clip, durationMs))
        cursorMs = maxOf(cursorMs, saturatingTimelineEndMs(clip.timelineStartMs, durationMs))
    }
    return sequence.build()
}
@UnstableApi
@ExperimentalApi
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

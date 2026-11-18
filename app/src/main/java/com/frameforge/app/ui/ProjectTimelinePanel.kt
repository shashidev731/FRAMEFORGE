package com.frameforge.app.ui

import android.net.Uri
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.widget.ImageView
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameforge.app.data.MediaEntity
import com.frameforge.app.data.TimelineClipEntity
import com.frameforge.app.data.TimelineRepository
import com.frameforge.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProjectTimelinePanel(projectId: String, media: List<MediaEntity>, repository: TimelineRepository) {
    val clips by repository.clips(projectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedClipId by remember(projectId) { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    val selectedClip = clips.firstOrNull { it.clipId == selectedClipId }
    val available = media.filter { item -> clips.none { it.mediaUri == item.uri } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Project timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${clips.map { it.trackIndex }.distinct().size} track(s) • ${clips.size} clip(s)", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                Button(onClick = { addMenu = true }, enabled = available.isNotEmpty()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Add media")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    available.forEach { item ->
                        DropdownMenuItem(
                            text = { Column {
                                Text(item.displayName, maxLines = 1)
                                Text(item.mimeType, color = Muted, style = MaterialTheme.typography.labelSmall)
                            } },
                            onClick = {
                                addMenu = false
                                scope.launch {
                                    val duration = withContext(Dispatchers.IO) { readTimelineMediaDuration(context, item.uri, item.mimeType) }
                                    repository.add(projectId, item.uri, item.displayName, item.mimeType, duration)
                                }
                            }
                        )
                    }
                }
            }
        }
        TimelineCompositionPreview(clips)
        if (selectedClip != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = Ink, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Selected clip: " + selectedClip.displayName, color = SurfaceWhite, maxLines = 1, fontWeight = FontWeight.SemiBold)
                    var gapDraftMs by remember(selectedClip.clipId, selectedClip.gapBeforeMs) {
                        mutableFloatStateOf(selectedClip.gapBeforeMs.coerceIn(0L, 600_000L).toFloat())
                    }
                    Text(
                        "Clip start offset: " + formatTimelineDuration(selectedClip.timelineStartMs) +
                            " • Gap before: " + formatTimelineDuration(selectedClip.gapBeforeMs),
                        color = SurfaceWhite.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = gapDraftMs,
                        onValueChange = { gapDraftMs = it },
                        valueRange = 0f..600_000f,
                        steps = 599
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Adjust gap up to 10 minutes", color = SurfaceWhite.copy(alpha = .65f),
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Button(
                            onClick = { scope.launch { repository.setGapBefore(selectedClip.clipId, gapDraftMs.toLong()) } },
                            enabled = gapDraftMs.toLong() != selectedClip.gapBeforeMs
                        ) { Text("Apply position") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    repository.setGapBefore(selectedClip.clipId, (selectedClip.gapBeforeMs - 1000L).coerceAtLeast(0L))
                                }
                            },
                            enabled = selectedClip.gapBeforeMs > 0L,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text("−1s") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    repository.setGapBefore(selectedClip.clipId, (selectedClip.gapBeforeMs + 1000L).coerceAtMost(600_000L))
                                }
                            },
                            enabled = selectedClip.gapBeforeMs < 600_000L,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text("+1s") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    repository.setGapBefore(selectedClip.clipId, (selectedClip.gapBeforeMs + 5000L).coerceAtMost(600_000L))
                                }
                            },
                            enabled = selectedClip.gapBeforeMs <= 595_000L,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text("+5s") }
                        OutlinedButton(
                            onClick = { scope.launch { repository.setGapBefore(selectedClip.clipId, 0L) } },
                            enabled = selectedClip.gapBeforeMs > 0L,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text("Reset") }
                    }
                    if (selectedClip.mimeType.startsWith("video/")) {
                        key(selectedClip.clipId) {
                            AndroidView(
                                factory = { viewContext ->
                                    VideoView(viewContext).apply {
                                        val controller = MediaController(viewContext)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setVideoURI(Uri.parse(selectedClip.mediaUri))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(210.dp),
                                onRelease = { it.stopPlayback() }
                            )
                        }
                    } else if (selectedClip.mimeType.startsWith("image/")) {
                        AndroidView(
                            factory = { viewContext ->
                                ImageView(viewContext).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    setImageURI(Uri.parse(selectedClip.mediaUri))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(210.dp)
                        )
                    } else if (selectedClip.mimeType.startsWith("audio/")) {
                        var audioPlayer by remember(selectedClip.clipId) { mutableStateOf<MediaPlayer?>(null) }
                        var audioPlaying by remember(selectedClip.clipId) { mutableStateOf(false) }
                        var audioLoading by remember(selectedClip.clipId) { mutableStateOf(false) }
                        var audioError by remember(selectedClip.clipId) { mutableStateOf<String?>(null) }
                        DisposableEffect(selectedClip.clipId) {
                            onDispose {
                                runCatching { audioPlayer?.stop() }
                                runCatching { audioPlayer?.release() }
                                audioPlayer = null
                                audioPlaying = false
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = !audioLoading,
                                onClick = {
                                    val current = audioPlayer
                                    if (current != null) {
                                        runCatching {
                                            if (current.isPlaying) {
                                                current.pause()
                                                audioPlaying = false
                                            } else {
                                                current.start()
                                                audioPlaying = true
                                            }
                                        }.onFailure { audioError = it.message ?: "Audio playback failed" }
                                    } else {
                                        audioLoading = true
                                        audioError = null
                                        runCatching {
                                            MediaPlayer().also { player ->
                                                audioPlayer = player
                                                player.setOnPreparedListener {
                                                    audioLoading = false
                                                    it.start()
                                                    audioPlaying = true
                                                }
                                                player.setOnCompletionListener {
                                                    audioPlaying = false
                                                    runCatching { it.seekTo(0) }
                                                }
                                                player.setOnErrorListener { _, _, _ ->
                                                    audioLoading = false
                                                    audioPlaying = false
                                                    audioError = "Unable to play this audio file."
                                                    runCatching { audioPlayer?.release() }
                                                    audioPlayer = null
                                                    true
                                                }
                                                player.setDataSource(context, Uri.parse(selectedClip.mediaUri))
                                                player.prepareAsync()
                                            }
                                        }.onFailure {
                                            audioLoading = false
                                            audioError = it.message ?: "Unable to open audio file."
                                            runCatching { audioPlayer?.release() }
                                            audioPlayer = null
                                        }
                                    }
                                }
                            ) { Text(if (audioLoading) "Loading…" else if (audioPlaying) "Pause audio" else "Play audio") }
                            Text(audioError ?: "Audio preview", color = if (audioError == null) SurfaceWhite.copy(alpha = .75f) else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text("Unsupported media preview.", color = SurfaceWhite.copy(alpha = .75f))
                    }
                    if (selectedClip.durationMs > 0L) {
                        val durationFloat = selectedClip.durationMs.toFloat()
                        val clipEnd = selectedClip.trimEndMs ?: selectedClip.durationMs
                        var trimStart by remember(selectedClip.clipId, selectedClip.trimStartMs) {
                            mutableFloatStateOf(selectedClip.trimStartMs.toFloat().coerceIn(0f, durationFloat - 1f))
                        }
                        var trimEnd by remember(selectedClip.clipId, selectedClip.trimEndMs, selectedClip.durationMs) {
                            mutableFloatStateOf(clipEnd.toFloat().coerceIn(1f, durationFloat))
                        }
                        Text("Trim start • " + formatTimelineDuration(trimStart.toLong()), color = SurfaceWhite, style = MaterialTheme.typography.labelMedium)
                        Slider(value = trimStart, onValueChange = { trimStart = it.coerceAtMost(trimEnd - 1f) },
                            valueRange = 0f..(durationFloat - 1f).coerceAtLeast(0f))
                        Text("Trim end • " + formatTimelineDuration(trimEnd.toLong()), color = SurfaceWhite, style = MaterialTheme.typography.labelMedium)
                        Slider(value = trimEnd, onValueChange = { trimEnd = it.coerceAtLeast(trimStart + 1f) },
                            valueRange = (trimStart + 1f).coerceAtMost(durationFloat)..durationFloat)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { scope.launch { repository.updateTrim(selectedClip, trimStart.toLong(), trimEnd.toLong()) } },
                                enabled = trimEnd - trimStart >= 1f
                            ) { Text("Apply trim") }
                            OutlinedButton(
                                onClick = {
                                    val midpoint = trimStart.toLong() + (trimEnd.toLong() - trimStart.toLong()) / 2L
                                    scope.launch { repository.splitAt(selectedClip.clipId, midpoint) }
                                },
                                enabled = trimEnd - trimStart > 1000f
                            ) { Text("Split") }
                        }
                    } else {
                        Text("Duration unavailable. Trim and split are disabled until the media duration can be read.",
                            color = SurfaceWhite.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Trimming changes only this timeline segment; the source file remains untouched.",
                        color = SurfaceWhite.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
            if (clips.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.Movie, null, tint = Accent, modifier = Modifier.size(30.dp))
                    Text("Your timeline is empty", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (media.isEmpty()) "Import media into the Media Library first, then add it here."
                        else "Add media from your library to build this project's clip sequence.",
                        color = Muted, style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    clips.groupBy { it.trackIndex }.toSortedMap().forEach { (trackIndex, trackClips) ->
                        item(key = "track-header-$trackIndex") {
                            Text(
                                if (trackIndex == 1) "AUDIO TRACK" else if (trackIndex == 0) "VIDEO / IMAGE TRACK" else "OVERLAY TRACK $trackIndex",
                                color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }
                        itemsIndexed(trackClips, key = { _, clip -> clip.clipId }) { index, clip ->
                            TimelineClipRow(
                                clip = clip, index = index, selected = selectedClipId == clip.clipId,
                                canMoveUp = index > 0, canMoveDown = index < trackClips.lastIndex,
                                onSelect = { selectedClipId = clip.clipId },
                                onMoveUp = { scope.launch { repository.moveEarlier(clip.clipId) } },
                                onMoveDown = { scope.launch { repository.moveLater(clip.clipId) } },
                                onRemove = { scope.launch { repository.remove(clip.clipId) } },
                                onDragStep = { direction -> scope.launch { if (direction < 0) repository.moveEarlier(clip.clipId) else repository.moveLater(clip.clipId) } }
                            )
                        }
                    }
                }
            }
        }
        Text(
            "Clips are saved per project. Preview and MP4 export support the main visual sequence plus one separate audio sequence, including trims and gaps. Overlapping clips, extra overlay tracks, transitions, and multiple simultaneous audio tracks are not supported yet.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TimelineClipRow(
    clip: TimelineClipEntity, index: Int, selected: Boolean, canMoveUp: Boolean, canMoveDown: Boolean,
    onSelect: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onRemove: () -> Unit,
    onDragStep: (Int) -> Unit
) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (selected) SurfaceWhite else Canvas,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else Line),
        modifier = Modifier
            .pointerInput(clip.clipId, canMoveUp, canMoveDown) {
                var accumulated = 0f
                detectDragGesturesAfterLongPress(
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount.y
                        val threshold = 48.dp.toPx()
                        if (accumulated <= -threshold && canMoveUp) {
                            onDragStep(-1)
                            accumulated = 0f
                        } else if (accumulated >= threshold && canMoveDown) {
                            onDragStep(1)
                            accumulated = 0f
                        }
                    }
                )
            }
            .clickable(onClick = onSelect)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(9.dp))
            Icon(
                when {
                    clip.mimeType.startsWith("video/") -> Icons.Default.Movie
                    clip.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
                    else -> Icons.Default.Image
                }, null, tint = Accent
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(clip.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val clipEndMs = clip.trimEndMs ?: clip.durationMs
                val clipLengthMs = (clipEndMs - clip.trimStartMs).coerceAtLeast(0L)
                Text(
                    if (clip.durationMs > 0L) "Starts ${formatTimelineDuration(clip.timelineStartMs)} • ${formatTimelineDuration(clipLengthMs)} • Gap ${formatTimelineDuration(clip.gapBeforeMs)}"
                    else "Starts ${formatTimelineDuration(clip.timelineStartMs)} • Duration unknown • Gap ${formatTimelineDuration(clip.gapBeforeMs)}",
                    color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Default.ArrowUpward, "Move earlier") }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Default.ArrowDownward, "Move later") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove from timeline", tint = Muted) }
        }
    }
}


private fun readTimelineMediaDuration(context: android.content.Context, uriText: String, mimeType: String): Long {
    if (mimeType.startsWith("image/")) return 5000L
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(uriText))
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L } ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatTimelineDuration(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

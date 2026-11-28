package com.frameforge.app.ui

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.frameforge.app.ui.theme.*
import com.frameforge.app.data.EditHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer

@Composable
fun VideoStudioScreen(modifier: Modifier = Modifier, projectId: String? = null, editHistoryRepository: EditHistoryRepository? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("No video selected") }
    var durationSeconds by remember { mutableFloatStateOf(0f) }
    var startSeconds by remember { mutableFloatStateOf(0f) }
    var endSeconds by remember { mutableFloatStateOf(0f) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("Choose a video to preview and trim.") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            videoUri = uri
            videoName = uri.lastPathSegment?.substringAfterLast('/')?.take(80) ?: "Selected video"
            scope.launch {
                val duration = withContext(Dispatchers.IO) {
                    runCatching {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, uri)
                            val value = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            value / 1000f
                        } finally {
                            runCatching { retriever.release() }
                        }
                    }.getOrDefault(0f)
                }
                durationSeconds = duration
                startSeconds = 0f
                endSeconds = duration
                status = if (duration > 0f) "Video loaded. Set a trim range and export a new MP4." else "Could not read this video's duration or metadata."
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { outputUri ->
        val source = videoUri
        if (source != null && outputUri != null) {
            scope.launch {
                exporting = true
                exportProgress = 0f
                status = "Exporting trimmed video…"
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        trimVideoToMp4(context, source, outputUri,
                            (startSeconds * 1_000_000).toLong(),
                            (endSeconds * 1_000_000).toLong()) { progress ->
                            exportProgress = progress
                        }
                    }
                }
                exporting = false
                exportProgress = 0f
                status = result.fold(
                    onSuccess = { "Export complete. Your trimmed MP4 is a new file; the original video was not changed." },
                    onFailure = { "Export failed: ${it.message ?: "unsupported video or destination"}. Try another MP4 file." }
                )
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Video Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Preview a local video and export a trimmed MP4 copy.", color = Muted)
        Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.VideoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text(if (videoUri == null) "Choose video" else "Change video")
        }
        if (videoUri != null) {
            Text(videoName, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Surface(shape = RoundedCornerShape(18.dp), color = Ink, border = BorderStroke(1.dp, Line)) {
                key(videoUri) {
                AndroidView(
                    factory = { viewContext ->
                        val view = VideoView(viewContext)
                        val controller = MediaController(viewContext)
                        controller.setAnchorView(view)
                        view.setMediaController(controller)
                        view.setVideoURI(videoUri)
                        view.setOnPreparedListener { view.start() }
                        view
                    },
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                    onRelease = { it.stopPlayback() }
                )
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trim range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Start • ${formatSeconds(startSeconds)}", fontWeight = FontWeight.Medium)
                    Slider(
                        value = startSeconds,
                        onValueChange = { startSeconds = it.coerceAtMost((endSeconds - 0.2f).coerceAtLeast(0f)) },
                        valueRange = 0f..(durationSeconds - 0.2f).coerceAtLeast(0.2f),
                        enabled = durationSeconds > 0.2f
                    )
                    Text("End • ${formatSeconds(endSeconds)}", fontWeight = FontWeight.Medium)
                    Slider(
                        value = endSeconds.coerceIn((startSeconds + 0.2f).coerceAtMost(durationSeconds), durationSeconds.coerceAtLeast(0.2f)),
                        onValueChange = { endSeconds = it.coerceAtLeast(startSeconds + 0.2f) },
                        valueRange = (startSeconds + 0.2f).coerceAtMost(durationSeconds)..durationSeconds.coerceAtLeast(0.2f),
                        enabled = durationSeconds > 0.2f
                    )
                    Text("Total duration: ${formatSeconds(durationSeconds)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("Fast trim starts at the previous video keyframe, so the first frame may be slightly earlier than the selected start.", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = {
                exporter.launch("FRAMEFORGE_trim_${System.currentTimeMillis()}.mp4")
            },
                enabled = durationSeconds > 0.2f && endSeconds > startSeconds + 0.1f && !exporting,
                modifier = Modifier.fillMaxWidth()) {
                if (exporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.ContentCut, null)
                Spacer(Modifier.width(8.dp))
                Text(if (exporting) "Exporting…" else "Export trimmed MP4")
            }
        } else {
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Movie, null, tint = Accent, modifier = Modifier.size(40.dp))
                    Text("Your video preview appears here", fontWeight = FontWeight.SemiBold)
                    Text("Choose a local video to start.", color = Muted)
                }
            }
        }
        Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Trim export remuxes supported audio/video tracks into a new MP4. It does not re-encode or modify the source.", color = Muted,
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatSeconds(value: Float): String {
    val total = value.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

internal fun mapExtractorSampleFlagsToCodecBufferFlags(sampleFlags: Int): Int {
    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
        throw IOException("Encrypted samples cannot be remuxed without decryption support.")
    }
    var codecFlags = 0
    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
    }
    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
    }
    return codecFlags
}

private fun trimVideoToMp4(context: Context, input: Uri, output: Uri, startUs: Long, endUs: Long, onProgress: (Float) -> Unit = {}) {
    require(startUs >= 0L && endUs > startUs) { "Choose a valid trim range" }
    val extractor = MediaExtractor()
    var descriptor: ParcelFileDescriptor? = null
    var muxer: MediaMuxer? = null
    var muxerStarted = false
    var completed = false
    try {
        extractor.setDataSource(context, input, null)
        descriptor = context.contentResolver.openFileDescriptor(output, "rw")
            ?: throw IOException("Cannot open export destination")
        muxer = MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val trackMap = IntArray(extractor.trackCount) { -1 }
        var selectedTracks = 0
        var requestedBufferBytes = 1024 * 1024
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                trackMap[index] = muxer.addTrack(format)
                extractor.selectTrack(index)
                selectedTracks++
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    requestedBufferBytes = maxOf(requestedBufferBytes, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
        }
        require(selectedTracks > 0) { "No supported audio or video tracks were found" }
        val buffer = ByteBuffer.allocate(requestedBufferBytes.coerceAtMost(64 * 1024 * 1024))
        muxer.start()
        muxerStarted = true
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val info = MediaCodec.BufferInfo()
        var baseTimeUs = -1L
        var samplesWritten = 0
        while (true) {
            val sourceTrack = extractor.sampleTrackIndex
            val sampleTime = extractor.sampleTime
            if (sourceTrack < 0 || sampleTime < 0L || sampleTime > endUs) break
            val targetTrack = trackMap.getOrElse(sourceTrack) { -1 }
            if (targetTrack >= 0) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (size > buffer.capacity()) {
                    throw IOException("A media sample exceeds the safe export buffer. Try a shorter or lower-resolution source.")
                }
                if (baseTimeUs < 0L) baseTimeUs = sampleTime
                info.set(0, size, (sampleTime - baseTimeUs).coerceAtLeast(0L), mapExtractorSampleFlagsToCodecBufferFlags(extractor.sampleFlags))
                muxer.writeSampleData(targetTrack, buffer, info)
                samplesWritten++
            }
            if (!extractor.advance()) break
            if (endUs > startUs) {
                val progress = ((sampleTime - startUs).toFloat() / (endUs - startUs).toFloat()).coerceIn(0f, 1f)
                onProgress(progress)
            }
        }
        onProgress(1f)
        require(samplesWritten > 0) { "No samples were available in the selected range" }
        muxer.stop()
        muxerStarted = false
        completed = true
    } finally {
        if (muxerStarted) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        runCatching { extractor.release() }
        runCatching { descriptor?.close() }
        if (!completed) runCatching { context.contentResolver.delete(output, null, null) }
    }
}

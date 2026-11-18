package com.frameforge.app.ui

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

@Composable
fun AudioWaveform(uriText: String, durationMs: Long = 0L, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var peaks by remember(uriText, durationMs) { mutableStateOf<List<Float>>(emptyList()) }
    var failed by remember(uriText, durationMs) { mutableStateOf(false) }

    LaunchedEffect(uriText, durationMs) {
        peaks = emptyList()
        failed = false
        try {
            peaks = withContext(Dispatchers.IO) { extractAudioWaveformPeaks(context, Uri.parse(uriText), durationMs = durationMs) }
            failed = peaks.isEmpty()
        } catch (_: Exception) {
            failed = true
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(30.dp)) {
        if (peaks.isEmpty()) {
            val center = size.height / 2f
            drawLine(
                Color(0xFF64748B).copy(alpha = if (failed) 0.22f else 0.12f),
                Offset(0f, center), Offset(size.width, center), strokeWidth = 2.dp.toPx()
            )
        } else {
            val step = size.width / peaks.size
            val middle = size.height / 2f
            peaks.forEachIndexed { index, peak ->
                val halfHeight = max(2.dp.toPx(), peak * middle * 0.92f)
                val x = (index + 0.5f) * step
                drawLine(
                    Color(0xFF64748B),
                    Offset(x, middle - halfHeight),
                    Offset(x, middle + halfHeight),
                    strokeWidth = max(1.dp.toPx(), step * 0.58f)
                )
            }
        }
    }
}

/** Decode the audio track to PCM and calculate peak amplitudes from its actual samples. */
internal suspend fun extractAudioWaveformPeaks(
    context: android.content.Context,
    uri: Uri,
    bucketCount: Int = 180,
    durationMs: Long = 0L
): List<Float> {
    require(bucketCount > 0)
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    try {
        extractor.setDataSource(context, uri, null)
        var audioTrack = -1
        var sourceFormat: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            val trackMime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
            if (trackMime.startsWith("audio/")) {
                audioTrack = index
                sourceFormat = candidate
                break
            }
        }
        val format = sourceFormat ?: return emptyList()
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
        extractor.selectTrack(audioTrack)
        val codec = MediaCodec.createDecoderByType(mime)
        decoder = codec
        codec.configure(format, null, null, 0)
        codec.start()

        val peaks = FloatArray(bucketCount)
        var totalSamples = 0L
        var inputDone = false
        var outputDone = false
        var outputFormat = format
        var maxAmplitude = 0f
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) else 1
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val expectedSamples = if (sampleRate > 0 && durationUs > 0L) durationUs * sampleRate * channels / 1_000_000L else 0L
        val info = MediaCodec.BufferInfo()

        while (!outputDone) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val input = codec.getInputBuffer(inputIndex)
                        ?: throw IllegalStateException("Audio decoder input buffer unavailable")
                    input.clear()
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (outputIndex >= 0) {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0) {
                        val pcm = output.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        pcm.position(info.offset)
                        pcm.limit(info.offset + info.size)
                        val encoding = if (android.os.Build.VERSION.SDK_INT >= 24 && outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                        val bytesPerSample = when (encoding) {
                            AudioFormat.ENCODING_PCM_FLOAT -> 4
                            AudioFormat.ENCODING_PCM_8BIT -> 1
                            else -> 2
                        }
                        while (pcm.remaining() >= bytesPerSample) {
                            val amplitude = when (encoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> abs(pcm.float).coerceIn(0f, 1f)
                                AudioFormat.ENCODING_PCM_8BIT -> abs((pcm.get().toInt() and 0xFF) - 128) / 128f
                                else -> abs(pcm.short.toInt() / 32768f)
                            }
                            maxAmplitude = max(maxAmplitude, amplitude)
                            val bucket = if (expectedSamples > 0L) {
                                ((totalSamples * bucketCount) / expectedSamples).toInt().coerceIn(0, bucketCount - 1)
                            } else {
                                ((info.presentationTimeUs.coerceAtLeast(0L) * bucketCount) /
                                    max(1L, effectiveDurationUs)).toInt().coerceIn(0, bucketCount - 1)
                            }
                            peaks[bucket] = max(peaks[bucket], amplitude)
                            totalSamples++
                        }
                    }
                    val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (endOfStream) outputDone = true
                }
            }
        }

        if (totalSamples == 0L || maxAmplitude <= 0f) return emptyList()
        val normalization = peaks.maxOrNull()?.takeIf { it > 0f } ?: maxAmplitude
        return peaks.map { (it / normalization).coerceIn(0f, 1f) }
    } finally {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        runCatching { extractor.release() }
    }
}

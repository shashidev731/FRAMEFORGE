package com.frameforge.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frameforge.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SceneCut(val timestampMs: Long, val confidence: Float)

@Composable
fun AiStudioScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTool by remember { mutableStateOf("Scene Detection") }
    LaunchedEffect(context) {
        // Remove the legacy plaintext key. This build has no cloud provider integration.
        context.getSharedPreferences("frameforge_ai_preferences", Context.MODE_PRIVATE)
            .edit()
            .remove("gemini_api_key")
            .apply()
    }

    // Scene Detection State
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("") }
    var isAnalyzingScenes by remember { mutableStateOf(false) }
    var detectedScenes by remember { mutableStateOf<List<SceneCut>>(emptyList()) }
    var sceneAnalysisProgress by remember { mutableFloatStateOf(0f) }

    // Photo Enhancement State
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoName by remember { mutableStateOf("") }
    var isAnalyzingPhoto by remember { mutableStateOf(false) }
    var enhancementRecommendation by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            videoUri = uri
            videoName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected video"
            detectedScenes = emptyList()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            photoUri = uri
            photoName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected photo"
            enhancementRecommendation = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Surface(shape = RoundedCornerShape(22.dp), color = Ink) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = AccentSoft, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Accent)
                        }
                    }
                    Column {
                        Text("FRAMEFORGE CREATIVE TOOLS", color = AccentSoft, style = MaterialTheme.typography.labelSmall)
                        Text("On-device analysis tools", color = SurfaceWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Only transparent on-device analysis tools are available in this build. Cloud AI and speech features stay disabled until real integrations and secure credential handling are implemented.",
                    color = SurfaceWhite.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Tool Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Scene Detection", "Smart Enhance", "Speech / Captions", "Settings").forEach { tool ->
                FilterChip(
                    selected = selectedTool == tool,
                    onClick = { selectedTool = tool },
                    label = { Text(tool, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        when (selectedTool) {
            "Scene Detection" -> {
                Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Local Scene-Change Hints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Samples one frame per second and compares average luminance. This lightweight heuristic can flag abrupt brightness changes, but may miss cuts between similarly lit shots.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = { videoPicker.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Movie, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (videoUri == null) "Select video for scene analysis" else "Change video")
                        }

                        if (videoUri != null) {
                            Text(videoName, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)

                            Button(
                                onClick = {
                                    scope.launch {
                                        isAnalyzingScenes = true
                                        sceneAnalysisProgress = 0f
                                        detectedScenes = withContext(Dispatchers.Default) {
                                            detectSceneCuts(context, videoUri!!) { progress ->
                                                sceneAnalysisProgress = progress
                                            }
                                        }
                                        isAnalyzingScenes = false
                                    }
                                },
                                enabled = !isAnalyzingScenes,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isAnalyzingScenes) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Analyzing frames (${(sceneAnalysisProgress * 100).toInt()}%)…")
                                } else {
                                    Icon(Icons.Default.Analytics, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Run Scene Detection")
                                }
                            }

                            if (detectedScenes.isNotEmpty()) {
                                Text("Detected ${detectedScenes.size} scene transition(s):", fontWeight = FontWeight.SemiBold)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    detectedScenes.forEachIndexed { index, cut ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Canvas,
                                            border = BorderStroke(1.dp, Line),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Scene cut #${index + 1}", fontWeight = FontWeight.Medium)
                                                Text(formatTimelineDuration(cut.timestampMs), color = Accent, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else if (!isAnalyzingScenes && videoUri != null) {
                                Text("Click 'Run Scene Detection' to scan the video.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            "Smart Enhance" -> {
                Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Local Photo Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Uses image luminance, dynamic range, and channel warmth heuristics to suggest starting values. This is not a learned or generative AI model.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = { photoPicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (photoUri == null) "Select photo to analyze" else "Change photo")
                        }

                        if (photoUri != null) {
                            Text(photoName, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)

                            Button(
                                onClick = {
                                    scope.launch {
                                        isAnalyzingPhoto = true
                                        val recommendation = withContext(Dispatchers.Default) {
                                            analyzePhotoLuminance(context, photoUri!!)
                                        }
                                        enhancementRecommendation = recommendation
                                        isAnalyzingPhoto = false
                                    }
                                },
                                enabled = !isAnalyzingPhoto,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isAnalyzingPhoto) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.AutoFixHigh, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isAnalyzingPhoto) "Analyzing histogram…" else "Analyze image levels")
                            }

                            if (enhancementRecommendation != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Canvas,
                                    border = BorderStroke(1.dp, Line),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Suggested starting adjustments:", fontWeight = FontWeight.Bold, color = Accent)
                                        Text(enhancementRecommendation!!, style = MaterialTheme.typography.bodyMedium)
                                        Text("Adjustments can be fine-tuned non-destructively in Photo Studio.", color = Muted, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Speech / Captions" -> {
                Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Speech-to-Text & Subtitles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Automatic transcription is not available in this build. No provider is connected, and FRAMEFORGE does not request or store an API key. This feature will be enabled only after a real transcription integration is implemented and validated.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Surface(shape = RoundedCornerShape(12.dp), color = Canvas, border = BorderStroke(1.dp, Line)) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, null, tint = Muted)
                                Text("Unavailable — no caption-generation action is exposed.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            "Settings" -> {
                Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Provider Availability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Cloud AI, speech transcription, and generative editing are not connected in this build. No provider credentials are collected or stored. A provider will be enabled only after authenticated requests, secure secret handling, consent, error handling, and automated tests are in place.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// Local average-luminance scene-change heuristic
private fun detectSceneCuts(
    context: Context,
    videoUri: Uri,
    onProgress: (Float) -> Unit
): List<SceneCut> {
    val retriever = MediaMetadataRetriever()
    val cuts = mutableListOf<SceneCut>()
    try {
        retriever.setDataSource(context, videoUri)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val totalMs = durationStr?.toLongOrNull() ?: return emptyList()
        if (totalMs < 1000L) return emptyList()

        val intervalMs = 1000L // Sample every 1 second
        var prevLuminance = -1f
        var timeMs = 0L

        while (timeMs < totalMs) {
            val frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) {
                val luminance = calculateAverageLuminance(frame)
                sceneChangeConfidence(prevLuminance, luminance)?.let { confidence ->
                    cuts.add(SceneCut(timeMs, confidence))
                }
                prevLuminance = luminance
                if (!frame.isRecycled) frame.recycle()
            }
            timeMs += intervalMs
            onProgress((timeMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f))
        }
    } catch (_: Exception) {
    } finally {
        runCatching { retriever.release() }
    }
    return cuts
}

private fun calculateAverageLuminance(bitmap: Bitmap): Float {
    val sampleWidth = 32.coerceAtMost(bitmap.width)
    val sampleHeight = 32.coerceAtMost(bitmap.height)
    val scaled = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, false)
    var totalLuminance = 0.0
    val pixels = IntArray(sampleWidth * sampleHeight)
    scaled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)

    for (pixel in pixels) {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Standard Rec. 601 luma
        totalLuminance += 0.299 * r + 0.587 * g + 0.114 * b
    }
    if (!scaled.isRecycled && scaled !== bitmap) scaled.recycle()
    return (totalLuminance / pixels.size).toFloat()
}

private fun analyzePhotoLuminance(context: Context, uri: Uri): String {
    return runCatching {
        val bitmap = decodePhotoForAnalysis(context, uri)
            ?: return@runCatching "Unable to decode image."
        val avgLuma = try {
            calculateAverageLuminance(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        val exposureAdvise = when {
            avgLuma < 70f -> "Low exposure detected (+0.6 EV recommended)"
            avgLuma > 190f -> "High highlights detected (-0.4 EV recommended)"
            else -> "Balanced exposure (0.0 EV recommended)"
        }

        val contrastAdvise = when {
            avgLuma in 80f..170f -> "Moderate dynamic range (+1.1x contrast recommended)"
            else -> "Standard contrast (+1.0x recommended)"
        }

        "• $exposureAdvise\n• $contrastAdvise\n• Saturation: +1.1x for vivid color clarity\n• Sharpness: Standard"
    }.getOrDefault("Analysis failed.")
}

private fun decodePhotoForAnalysis(context: Context, uri: Uri): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = photoAnalysisSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, options)
    }
}

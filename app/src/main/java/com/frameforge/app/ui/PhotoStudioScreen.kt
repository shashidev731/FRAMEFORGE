package com.frameforge.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.frameforge.app.ui.theme.*
import com.frameforge.app.data.EditHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun PhotoStudioScreen(modifier: Modifier = Modifier, projectId: String? = null, editHistoryRepository: EditHistoryRepository? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var exposure by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var overlayText by remember { mutableStateOf("") }
    var rotation by remember { mutableIntStateOf(0) }
    var cropRatio by remember { mutableStateOf("Original") }
    var isProcessing by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Choose a photo to start editing.") }
    var exportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exportSummary by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                isProcessing = true
                val decoded = withContext(Dispatchers.IO) {
                    decodePhotoForEditing(context, uri)
                }
                sourceBitmap = decoded
                sourceName = uri.lastPathSegment?.substringAfterLast('/')?.take(70) ?: "Selected photo"
                exposure = 0f
                saturation = 1f
                contrast = 1f
                warmth = 0f
                overlayText = ""
                rotation = 0
                cropRatio = "Original"
                showOriginal = false
                status = if (decoded == null) "This image could not be opened." else "Original loaded. Adjust the controls; the source file stays unchanged."
                isProcessing = false
            }
        }
    }

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri: Uri? ->
        val bitmap = exportBitmap
        if (uri != null && bitmap != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw IOException("JPEG export failed")
                        } ?: throw IOException("Could not open destination")
                    }.isSuccess
                }
                status = if (saved) "Export complete. Your edited copy was saved; the original remains unchanged." else "Export failed. Choose another destination and try again."
                if (saved && projectId != null) {
                    runCatching {
                        editHistoryRepository?.record(projectId, "PHOTO_EXPORT", exportSummary)
                    }
                }
                exportBitmap = null
            }
        }
    }

    LaunchedEffect(sourceBitmap, exposure, saturation, contrast, warmth, overlayText, rotation, cropRatio) {
        val source = sourceBitmap ?: run { previewBitmap = null; return@LaunchedEffect }
        isProcessing = true
        delay(120)
        val result = withContext(Dispatchers.Default) {
            runCatching { renderPhotoEdit(source, exposure, saturation, contrast, warmth, overlayText, rotation, cropRatio) }.getOrNull()
        }
        previewBitmap = result
        isProcessing = false
        if (result == null) status = "Preview could not be generated. Try a smaller image."
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Photo Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Non-destructive photo adjustments with a real exported image.", color = Muted)
        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AddPhotoAlternate, null)
            Spacer(Modifier.width(8.dp))
            Text(if (sourceBitmap == null) "Choose photo" else "Change photo")
        }
        if (sourceBitmap == null) {
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Image, null, tint = Accent, modifier = Modifier.size(40.dp))
                    Text("Your photo preview appears here", fontWeight = FontWeight.SemiBold)
                    Text("Choose a photo from your device to begin.", color = Muted)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(sourceName, modifier = Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                TextButton(onClick = { showOriginal = !showOriginal }, enabled = previewBitmap != null) {
                    Icon(Icons.Default.Compare, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (showOriginal) "Show edited" else "Compare original")
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Ink, border = BorderStroke(1.dp, Line)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).padding(8.dp),
                    contentAlignment = Alignment.Center) {
                    val visibleBitmap = if (showOriginal) sourceBitmap else previewBitmap
                    visibleBitmap?.let { Image(bitmap = it.asImageBitmap(),
                        contentDescription = if (showOriginal) "Original photo" else "Edited photo preview",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) }
                    if (isProcessing) CircularProgressIndicator(color = Accent)
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Adjustments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Exposure  •  ${"%.1f".format(exposure)} EV", fontWeight = FontWeight.Medium)
                    Slider(value = exposure, onValueChange = { exposure = it }, valueRange = -2f..2f)
                    Text("Saturation  •  ${"%.1f".format(saturation)}×", fontWeight = FontWeight.Medium)
                    Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f)
                    Text("Contrast  •  ${"%.1f".format(contrast)}×", fontWeight = FontWeight.Medium)
                    Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.5f)
                    Text("Warmth  •  ${"%.1f".format(warmth)}", fontWeight = FontWeight.Medium)
                    Slider(value = warmth, onValueChange = { warmth = it }, valueRange = -1f..1f)
                    OutlinedTextField(
                        value = overlayText,
                        onValueChange = { overlayText = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text overlay (optional)") },
                        singleLine = true
                    )
                    Text("Crop ratio", fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Original", "1:1", "4:5", "9:16").forEach { ratio ->
                            FilterChip(selected = cropRatio == ratio, onClick = { cropRatio = ratio }, label = { Text(ratio) })
                        }
                    }
                    Text("Rotation", fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { rotation = (rotation + 270) % 360 }) {
                            Icon(Icons.Default.RotateLeft, null); Text("Left")
                        }
                        OutlinedButton(onClick = { rotation = (rotation + 90) % 360 }) {
                            Icon(Icons.Default.RotateRight, null); Text("Right")
                        }
                        TextButton(onClick = { exposure = 0f; saturation = 1f; contrast = 1f; warmth = 0f; overlayText = ""; rotation = 0; cropRatio = "Original" }) {
                            Text("Reset")
                        }
                    }
                }
            }
            Button(
                onClick = {
                    exportBitmap = previewBitmap
                    exportSummary = "crop=$cropRatio; exposure=$exposure EV; saturation=$saturation; contrast=$contrast; warmth=$warmth; rotation=$rotation; text=$overlayText"
                    savePicker.launch("FRAMEFORGE_${System.currentTimeMillis()}.jpg")
                },
                enabled = previewBitmap != null && !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("Export edited photo")
            }
        }
        Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Export creates a new JPEG copy. The imported original is never overwritten. For phone memory safety, editing is capped to 3072 px on the longest edge.",
            color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun decodePhotoForEditing(context: android.content.Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 3072 || bounds.outHeight / sample > 3072) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun renderPhotoEdit(
    source: Bitmap,
    exposure: Float,
    saturation: Float,
    contrast: Float,
    warmth: Float,
    overlayText: String,
    rotation: Int,
    cropRatio: String
): Bitmap {
    val targetRatio = when (cropRatio) {
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        "9:16" -> 9f / 16f
        else -> source.width.toFloat() / source.height.toFloat()
    }
    // For quarter-turn rotations, crop in the inverse ratio so the final rendered
    // image matches the selected aspect ratio after rotation.
    val cropTargetRatio = if (rotation % 180 != 0) 1f / targetRatio else targetRatio
    val sourceRatio = source.width.toFloat() / source.height.toFloat()
    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > cropTargetRatio) {
        cropHeight = source.height
        cropWidth = (cropHeight * cropTargetRatio).toInt().coerceIn(1, source.width)
    } else {
        cropWidth = source.width
        cropHeight = (cropWidth / cropTargetRatio).toInt().coerceIn(1, source.height)
    }
    val left = (source.width - cropWidth) / 2
    val top = (source.height - cropHeight) / 2
    val cropped = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)

    val brightness = Math.pow(2.0, exposure.toDouble()).toFloat()
    val factor = brightness * contrast
    val contrastOffset = 128f * (1f - contrast)
    val color = ColorMatrix().apply {
        setSaturation(saturation)
        postConcat(ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, contrastOffset + warmth * 24f,
            0f, factor, 0f, 0f, contrastOffset,
            0f, 0f, factor, 0f, contrastOffset - warmth * 24f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
    val adjusted = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
    Canvas(adjusted).drawBitmap(cropped, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(color)
    })
    val rotated = if (rotation % 360 == 0) adjusted else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(adjusted, 0, 0, adjusted.width, adjusted.height, matrix, true)
    }
    if (overlayText.isBlank()) return rotated
    val output = rotated.copy(Bitmap.Config.ARGB_8888, true)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textSize = (output.width.coerceAtMost(output.height) * 0.065f).coerceAtLeast(28f)
        setShadowLayer(5f, 2f, 2f, android.graphics.Color.BLACK)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    Canvas(output).drawText(overlayText, output.width * 0.05f, output.height * 0.9f, textPaint)
    return output
}

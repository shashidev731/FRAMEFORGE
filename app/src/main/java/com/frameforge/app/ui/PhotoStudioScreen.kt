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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.frameforge.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun PhotoStudioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var exposure by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableIntStateOf(0) }
    var cropRatio by remember { mutableStateOf("Original") }
    var isProcessing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Choose a photo to start editing.") }
    var exportBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                isProcessing = true
                val decoded = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
                sourceBitmap = decoded
                sourceName = uri.lastPathSegment?.substringAfterLast('/')?.take(70) ?: "Selected photo"
                exposure = 0f
                saturation = 1f
                rotation = 0
                cropRatio = "Original"
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
                exportBitmap = null
            }
        }
    }

    LaunchedEffect(sourceBitmap, exposure, saturation, rotation, cropRatio) {
        val source = sourceBitmap ?: run { previewBitmap = null; return@LaunchedEffect }
        isProcessing = true
        val result = withContext(Dispatchers.Default) {
            runCatching { renderPhotoEdit(source, exposure, saturation, rotation, cropRatio) }.getOrNull()
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
            Text(sourceName, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Surface(shape = RoundedCornerShape(18.dp), color = Ink, border = BorderStroke(1.dp, Line)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).padding(8.dp),
                    contentAlignment = Alignment.Center) {
                    previewBitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "Edited photo preview",
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
                        TextButton(onClick = { exposure = 0f; saturation = 1f; rotation = 0; cropRatio = "Original" }) {
                            Text("Reset")
                        }
                    }
                }
            }
            Button(
                onClick = {
                    exportBitmap = previewBitmap
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
        Text("Export creates a new JPEG copy. The imported original is never overwritten.", color = Muted,
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun renderPhotoEdit(
    source: Bitmap,
    exposure: Float,
    saturation: Float,
    rotation: Int,
    cropRatio: String
): Bitmap {
    val targetRatio = when (cropRatio) {
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        "9:16" -> 9f / 16f
        else -> source.width.toFloat() / source.height.toFloat()
    }
    val sourceRatio = source.width.toFloat() / source.height.toFloat()
    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > targetRatio) {
        cropHeight = source.height
        cropWidth = (cropHeight * targetRatio).toInt().coerceIn(1, source.width)
    } else {
        cropWidth = source.width
        cropHeight = (cropWidth / targetRatio).toInt().coerceIn(1, source.height)
    }
    val left = (source.width - cropWidth) / 2
    val top = (source.height - cropHeight) / 2
    val cropped = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)

    val brightness = Math.pow(2.0, exposure.toDouble()).toFloat()
    val color = ColorMatrix().apply {
        setSaturation(saturation)
        postConcat(ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
    val adjusted = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
    Canvas(adjusted).drawBitmap(cropped, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(color)
    })
    if (rotation % 360 == 0) return adjusted
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(adjusted, 0, 0, adjusted.width, adjusted.height, matrix, true)
}

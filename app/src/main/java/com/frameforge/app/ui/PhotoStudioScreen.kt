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
import androidx.compose.foundation.horizontalScroll
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
import com.frameforge.app.data.PhotoEditSnapshot
import kotlinx.coroutines.flow.flowOf
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
    var sourceUriText by remember { mutableStateOf<String?>(null) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var highlights by remember { mutableFloatStateOf(0f) }
    var shadows by remember { mutableFloatStateOf(0f) }
    var sharpness by remember { mutableFloatStateOf(0f) }
    var overlayText by remember { mutableStateOf("") }
    var rotation by remember { mutableIntStateOf(0) }
    var cropRatio by remember { mutableStateOf("Original") }
    var cropHorizontalPosition by remember { mutableFloatStateOf(0f) }
    var cropVerticalPosition by remember { mutableFloatStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Choose a photo to start editing.") }
    var exportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val photoHistoryFlow = remember(projectId, sourceUriText, editHistoryRepository) {
        if (projectId != null && sourceUriText != null && editHistoryRepository != null) {
            editHistoryRepository.photoHistory(projectId, sourceUriText)
        } else {
            flowOf(emptyList())
        }
    }
    val photoHistory by photoHistoryFlow.collectAsState(initial = emptyList())
    val canUndoPhoto = projectId != null && sourceUriText != null &&
        editHistoryRepository != null && photoHistory.count { it.isApplied } > 1
    val canRedoPhoto = projectId != null && sourceUriText != null &&
        editHistoryRepository != null && photoHistory.any { !it.isApplied }

    fun currentPhotoSnapshot(): PhotoEditSnapshot? {
        val uri = sourceUriText ?: return null
        return PhotoEditSnapshot(
            sourceUri = uri,
            exposure = exposure,
            saturation = saturation,
            contrast = contrast,
            warmth = warmth,
            highlights = highlights,
            shadows = shadows,
            sharpness = sharpness,
            overlayText = overlayText,
            rotationDegrees = rotation,
            cropRatio = cropRatio,
            cropHorizontalPosition = cropHorizontalPosition,
            cropVerticalPosition = cropVerticalPosition
        )
    }

    fun applyPhotoSnapshot(snapshot: PhotoEditSnapshot) {
        if (snapshot.sourceUri != sourceUriText) return
        exposure = snapshot.exposure
        saturation = snapshot.saturation
        contrast = snapshot.contrast
        warmth = snapshot.warmth
        highlights = snapshot.highlights
        shadows = snapshot.shadows
        sharpness = snapshot.sharpness
        overlayText = snapshot.overlayText
        rotation = snapshot.rotationDegrees
        cropRatio = snapshot.cropRatio
        cropHorizontalPosition = snapshot.cropHorizontalPosition
        cropVerticalPosition = snapshot.cropVerticalPosition
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            sourceUriText = uri.toString()
            sourceBitmap = null
            previewBitmap = null
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
                highlights = 0f
                shadows = 0f
                sharpness = 0f
                overlayText = ""
                rotation = 0
                cropRatio = "Original"
                cropHorizontalPosition = 0f
                cropVerticalPosition = 0f
                showOriginal = false
                status = if (decoded == null) "This image could not be opened." else "Original loaded. Adjust the controls; the source file stays unchanged."
                isProcessing = false
                if (decoded != null && projectId != null) {
                    val restored = editHistoryRepository?.loadPhotoSnapshot(projectId, uri.toString())
                    if (restored != null) {
                        applyPhotoSnapshot(restored)
                    } else {
                        currentPhotoSnapshot()?.let { editHistoryRepository?.recordPhotoSnapshot(projectId, it) }
                    }
                }
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

    LaunchedEffect(projectId, sourceUriText, sourceBitmap, exposure, saturation, contrast, warmth, highlights, shadows, sharpness, overlayText, rotation, cropRatio, cropHorizontalPosition, cropVerticalPosition) {
        if (projectId != null && sourceUriText != null && sourceBitmap != null) {
            delay(450)
            currentPhotoSnapshot()?.let { editHistoryRepository?.recordPhotoSnapshot(projectId, it) }
        }
    }

    LaunchedEffect(sourceBitmap, exposure, saturation, contrast, warmth, highlights, shadows, sharpness, overlayText, rotation, cropRatio, cropHorizontalPosition, cropVerticalPosition) {
        val source = sourceBitmap ?: run { previewBitmap = null; return@LaunchedEffect }
        isProcessing = true
        delay(120)
        val result = withContext(Dispatchers.Default) {
            runCatching { renderPhotoEdit(source, exposure, saturation, contrast, warmth, highlights, shadows, sharpness, overlayText, rotation, cropRatio, cropHorizontalPosition, cropVerticalPosition) }.getOrNull()
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
                if (projectId != null && editHistoryRepository != null) {
                    TextButton(
                        onClick = {
                            val sourceUri = sourceUriText
                            if (sourceUri != null) scope.launch {
                                editHistoryRepository.undoPhoto(projectId, sourceUri)?.let(::applyPhotoSnapshot)
                            }
                        },
                        enabled = canUndoPhoto
                    ) { Icon(Icons.Default.Undo, null); Text("Undo") }
                    TextButton(
                        onClick = {
                            val sourceUri = sourceUriText
                            if (sourceUri != null) scope.launch {
                                editHistoryRepository.redoPhoto(projectId, sourceUri)?.let(::applyPhotoSnapshot)
                            }
                        },
                        enabled = canRedoPhoto
                    ) { Icon(Icons.Default.Redo, null); Text("Redo") }
                }
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
                    Text("Filter Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Normal" to { exposure = 0f; saturation = 1f; contrast = 1f; warmth = 0f; highlights = 0f; shadows = 0f; sharpness = 0f },
                            "Vivid" to { exposure = 0.2f; saturation = 1.4f; contrast = 1.15f; warmth = 0.1f; highlights = -0.1f; shadows = 0.2f; sharpness = 0.25f },
                            "B&W" to { exposure = 0.1f; saturation = 0f; contrast = 1.25f; warmth = 0f; highlights = -0.2f; shadows = 0.15f; sharpness = 0.3f },
                            "Vintage" to { exposure = 0.1f; saturation = 0.75f; contrast = 0.9f; warmth = 0.4f; highlights = -0.25f; shadows = 0.2f; sharpness = 0.1f },
                            "Cool" to { exposure = 0f; saturation = 1.1f; contrast = 1.05f; warmth = -0.35f; highlights = -0.1f; shadows = 0.1f; sharpness = 0.15f },
                            "Warm" to { exposure = 0.1f; saturation = 1.15f; contrast = 1.05f; warmth = 0.4f; highlights = -0.15f; shadows = 0.15f; sharpness = 0.1f },
                            "Moody" to { exposure = -0.3f; saturation = 0.85f; contrast = 1.35f; warmth = -0.1f; highlights = -0.35f; shadows = -0.1f; sharpness = 0.25f }
                        ).forEach { (name, applyPreset) ->
                            FilledTonalButton(onClick = applyPreset) { Text(name, style = MaterialTheme.typography.labelSmall) }
                        }
                    }

                    Text("Fine Adjustments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Exposure  •  ${"%.1f".format(exposure)} EV", fontWeight = FontWeight.Medium)
                    Slider(value = exposure, onValueChange = { exposure = it }, valueRange = -2f..2f)
                    Text("Saturation  •  ${"%.1f".format(saturation)}×", fontWeight = FontWeight.Medium)
                    Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f)
                    Text("Contrast  •  ${"%.1f".format(contrast)}×", fontWeight = FontWeight.Medium)
                    Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.5f)
                    Text("Warmth  •  ${"%.1f".format(warmth)}", fontWeight = FontWeight.Medium)
                    Slider(value = warmth, onValueChange = { warmth = it }, valueRange = -1f..1f)
                    Text("Highlights  •  ${"%.1f".format(highlights)}", fontWeight = FontWeight.Medium)
                    Slider(value = highlights, onValueChange = { highlights = it }, valueRange = -1f..1f)
                    Text("Shadows  •  ${"%.1f".format(shadows)}", fontWeight = FontWeight.Medium)
                    Slider(value = shadows, onValueChange = { shadows = it }, valueRange = -1f..1f)
                    Text("Sharpness  •  ${"%.1f".format(sharpness)}", fontWeight = FontWeight.Medium)
                    Slider(value = sharpness, onValueChange = { sharpness = it }, valueRange = 0f..1f)
                    OutlinedTextField(
                        value = overlayText,
                        onValueChange = { overlayText = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text overlay (optional)") },
                        singleLine = true
                    )
                    Text("Crop ratio", fontWeight = FontWeight.Medium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Original", "1:1", "4:5", "9:16", "16:9", "3:2").forEach { ratio ->
                            FilterChip(selected = cropRatio == ratio, onClick = { cropRatio = ratio }, label = { Text(ratio) })
                        }
                    }
                    if (cropRatio != "Original" || rotation % 180 != 0) {
                        Text("Crop position", fontWeight = FontWeight.Medium)
                        Text("Horizontal  •  ${(cropHorizontalPosition * 100f).toInt()}%", color = Muted)
                        Slider(
                            value = cropHorizontalPosition,
                            onValueChange = { cropHorizontalPosition = it },
                            valueRange = -1f..1f
                        )
                        Text("Vertical  •  ${(cropVerticalPosition * 100f).toInt()}%", color = Muted)
                        Slider(
                            value = cropVerticalPosition,
                            onValueChange = { cropVerticalPosition = it },
                            valueRange = -1f..1f
                        )
                    }
                    Text("Rotation", fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { rotation = (rotation + 270) % 360 }) {
                            Icon(Icons.Default.RotateLeft, null); Text("Left")
                        }
                        OutlinedButton(onClick = { rotation = (rotation + 90) % 360 }) {
                            Icon(Icons.Default.RotateRight, null); Text("Right")
                        }
                        TextButton(onClick = {
                            exposure = 0f
                            saturation = 1f
                            contrast = 1f
                            warmth = 0f
                            highlights = 0f
                            shadows = 0f
                            sharpness = 0f
                            overlayText = ""
                            rotation = 0
                            cropRatio = "Original"
                            cropHorizontalPosition = 0f
                            cropVerticalPosition = 0f
                        }) {
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
        Text("Export creates a new JPEG copy. The imported original is never overwritten. For phone memory safety, editing is capped to 2048 px on the longest edge.",
            color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun decodePhotoForEditing(context: android.content.Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
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
    highlights: Float,
    shadows: Float,
    sharpness: Float,
    overlayText: String,
    rotation: Int,
    cropRatio: String,
    cropHorizontalPosition: Float,
    cropVerticalPosition: Float
): Bitmap {
    val targetRatio = when (cropRatio) {
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        "9:16" -> 9f / 16f
        "16:9" -> 16f / 9f
        "3:2" -> 3f / 2f
        else -> source.width.toFloat() / source.height.toFloat()
    }
    val crop = calculatePhotoCropRect(
        sourceWidth = source.width,
        sourceHeight = source.height,
        targetRatio = targetRatio,
        rotationDegrees = rotation,
        horizontalPosition = cropHorizontalPosition,
        verticalPosition = cropVerticalPosition
    )
    val cropped = Bitmap.createBitmap(source, crop.left, crop.top, crop.width, crop.height)

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
    if (highlights != 0f || shadows != 0f || sharpness != 0f) {
        val pixels = IntArray(adjusted.width * adjusted.height)
        adjusted.getPixels(pixels, 0, adjusted.width, 0, 0, adjusted.width, adjusted.height)
        val adjustedPixels = adjustPhotoPixels(
            pixels = pixels,
            width = adjusted.width,
            height = adjusted.height,
            highlights = highlights,
            shadows = shadows,
            sharpness = sharpness
        )
        adjusted.setPixels(adjustedPixels, 0, adjusted.width, 0, 0, adjusted.width, adjusted.height)
    }
    if (cropped !== source && !cropped.isRecycled) cropped.recycle()
    val rotated = if (rotation % 360 == 0) adjusted else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(adjusted, 0, 0, adjusted.width, adjusted.height, matrix, true).also {
            if (!adjusted.isRecycled) adjusted.recycle()
        }
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
    if (rotated !== source && !rotated.isRecycled) rotated.recycle()
    return output
}

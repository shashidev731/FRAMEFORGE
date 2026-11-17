package com.frameforge.app.ui

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

@Composable
fun ProjectTimelinePanel(projectId: String, media: List<MediaEntity>, repository: TimelineRepository) {
    val clips by repository.clips(projectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var addMenu by remember { mutableStateOf(false) }
    val available = media.filter { item -> clips.none { it.mediaUri == item.uri } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Project timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Track 1 • ${clips.size} clip(s)", color = Muted, style = MaterialTheme.typography.bodySmall)
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
                                scope.launch { repository.add(projectId, item.uri, item.displayName, item.mimeType) }
                            }
                        )
                    }
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
                    itemsIndexed(clips, key = { _, clip -> clip.clipId }) { index, clip ->
                        TimelineClipRow(
                            clip = clip, index = index, canMoveUp = index > 0, canMoveDown = index < clips.lastIndex,
                            onMoveUp = { scope.launch { repository.moveEarlier(clip.clipId) } },
                            onMoveDown = { scope.launch { repository.moveLater(clip.clipId) } },
                            onRemove = { scope.launch { repository.remove(clip.clipId) } }
                        )
                    }
                }
            }
        }
        Text(
            "Clips are saved per project and can be reordered or removed without changing original files. Playback, trimming, split, multi-track compositing and timeline export are not connected yet.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TimelineClipRow(
    clip: TimelineClipEntity, index: Int, canMoveUp: Boolean, canMoveDown: Boolean,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit, onRemove: () -> Unit
) {
    Surface(shape = RoundedCornerShape(12.dp), color = Canvas, border = BorderStroke(1.dp, Line)) {
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
                Text(clip.mimeType, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Default.ArrowUpward, "Move earlier") }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Default.ArrowDownward, "Move later") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove from timeline", tint = Muted) }
        }
    }
}

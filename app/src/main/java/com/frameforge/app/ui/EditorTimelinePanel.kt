package com.frameforge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameforge.app.data.EditHistoryRepository
import com.frameforge.app.data.EditOperationEntity
import com.frameforge.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun EditorTimelinePanel(projectId: String, repository: EditHistoryRepository) {
    val operations by repository.operations(projectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var selectedTool by remember { mutableStateOf("Trim") }
    var parameter by remember { mutableStateOf("Start 0.0s • End 10.0s") }
    val applied = operations.count { it.isApplied }
    val canUndo = applied > 0
    val canRedo = operations.any { !it.isApplied }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Editing timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add an edit instruction", fontWeight = FontWeight.SemiBold)
                Text("Choose a non-destructive operation to add to this project's saved edit history.", color = Muted,
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Trim", "Split", "Crop").forEach { tool ->
                        FilterChip(selected = selectedTool == tool, onClick = {
                            selectedTool = tool
                            parameter = when (tool) {
                                "Trim" -> "Start 0.0s • End 10.0s"
                                "Split" -> "At 5.0s"
                                else -> "Aspect 9:16 • Center"
                            }
                        }, label = { Text(tool) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Exposure", "Rotate", "Text").forEach { tool ->
                        FilterChip(selected = selectedTool == tool, onClick = {
                            selectedTool = tool
                            parameter = when (tool) {
                                "Exposure" -> "+0.0 EV"
                                "Rotate" -> "90°"
                                else -> "New title"
                            }
                        }, label = { Text(tool) })
                    }
                }
                OutlinedTextField(
                    value = parameter,
                    onValueChange = { parameter = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Instruction parameters") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        scope.launch {
                            repository.record(projectId, selectedTool.uppercase(), parameter.trim())
                        }
                    },
                    enabled = parameter.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add to edit history")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { repository.undo(projectId) } },
                        enabled = canUndo, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Undo, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Undo")
                    }
                    OutlinedButton(onClick = { scope.launch { repository.redo(projectId) } },
                        enabled = canRedo, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Redo, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Redo")
                    }
                }
                Text("$applied active • ${operations.size} total history steps", color = Muted,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Text("Saved edit history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (operations.isEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("No edit instructions yet. Add one above to start this project's history.", color = Muted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(operations, key = { it.operationId }) { operation ->
                    OperationRow(operation)
                }
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = AccentSoft) {
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, null, tint = Accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Honest status: these instructions and undo/redo state are saved locally. The media rendering engine, real-time preview and export pipeline are not connected yet, so adding an instruction does not change the original photo or video.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink
                )
            }
        }
    }
}

@Composable
private fun OperationRow(operation: EditOperationEntity) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = if (operation.isApplied) SurfaceWhite else Canvas,
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (operation.operationType) {
                    "TRIM" -> Icons.Default.ContentCut
                    "SPLIT" -> Icons.Default.CallSplit
                    "CROP" -> Icons.Default.Crop
                    "EXPOSURE" -> Icons.Default.Brightness6
                    "ROTATE" -> Icons.Default.RotateRight
                    else -> Icons.Default.TextFields
                },
                null,
                tint = if (operation.isApplied) Accent else Muted
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(operation.operationType, fontWeight = FontWeight.SemiBold)
                Text(operation.payload, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Text(if (operation.isApplied) "Active" else "Undone",
                color = if (operation.isApplied) Accent else Muted,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

package com.frameforge.app.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameforge.app.data.ProjectEntity
import com.frameforge.app.data.ProjectRepository
import com.frameforge.app.data.MediaEntity
import com.frameforge.app.data.MediaRepository
import androidx.compose.ui.platform.LocalContext
import com.frameforge.app.ui.theme.*
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home), Projects("Projects", Icons.Default.FolderOpen),
    Video("Video", Icons.Default.Movie), Photo("Photo", Icons.Default.Photo),
    AI("AI Studio", Icons.Default.AutoAwesome), Library("Library", Icons.Default.Collections),
    Settings("Settings", Icons.Default.Settings)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameforgeRoot(repository: ProjectRepository, mediaRepository: MediaRepository) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var createDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("Video") }
    var ratio by remember { mutableStateOf("9:16") }
    var importedCount by remember { mutableIntStateOf(0) }
    val projects by repository.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val media by mediaRepository.media.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importedCount += uris.size
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = uri.lastPathSegment?.substringAfterLast('/')?.take(80)?.ifBlank { "Selected media" } ?: "Selected media"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            scope.launch { mediaRepository.add(uri.toString(), name, mime) }
        }
    }
    Scaffold(
        containerColor = Canvas,
        topBar = { TopAppBar(title = { Column {
            Text("FRAMEFORGE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text("Create without limits", color = Muted, style = MaterialTheme.typography.labelSmall)
        } }, actions = { IconButton(onClick = { tab = Tab.Settings }) { Icon(Icons.Default.Settings, "Settings") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)) },
        bottomBar = { NavigationBar(containerColor = SurfaceWhite) {
            listOf(Tab.Home, Tab.Projects, Tab.Video, Tab.Photo, Tab.AI, Tab.Library).forEach { item ->
                NavigationBarItem(selected = tab == item, onClick = { tab = item },
                    icon = { Icon(item.icon, item.label) }, label = { Text(item.label, maxLines = 1) })
            }
        } },
        floatingActionButton = { if (tab == Tab.Home || tab == Tab.Projects) FloatingActionButton(
            onClick = { createDialog = true }, containerColor = Accent, contentColor = SurfaceWhite
        ) { Icon(Icons.Default.Add, "Create project") } }
    ) { inset ->
        when (tab) {
            Tab.Home -> HomeScreen(projects, Modifier.padding(inset), { createDialog = true }, { tab = Tab.Video }, { tab = Tab.Photo }, { tab = Tab.AI })
            Tab.Projects -> ProjectsScreen(projects, repository, Modifier.padding(inset), { createDialog = true })
            Tab.Video -> StudioScreen("Video Studio", "Your video workspace", Icons.Default.Movie, Modifier.padding(inset),
                "Import video clips to begin. The editing engine is scheduled for Phase 2.", { mediaPicker.launch(arrayOf("video/*")) })
            Tab.Photo -> StudioScreen("Photo Studio", "Your photo workspace", Icons.Default.Photo, Modifier.padding(inset),
                "Import photos to begin. Non-destructive photo tools are scheduled for Phase 2.", { mediaPicker.launch(arrayOf("image/*")) })
            Tab.AI -> StudioScreen("AI Studio", "AI edits always require your approval", Icons.Default.AutoAwesome, Modifier.padding(inset),
                "AI tools are not active yet. No media is uploaded or modified in this foundation build.", null)
            Tab.Library -> MediaLibraryScreen(media, Modifier.padding(inset), { mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*")) }, { uri -> scope.launch { mediaRepository.delete(uri) } })
            Tab.Settings -> StudioScreen("Settings", "Workspace, editing, storage, AI and privacy", Icons.Default.Settings, Modifier.padding(inset),
                "Local-first foundation. Cloud sync and advanced preferences are planned for later phases.", null)
        }
    }
    if (createDialog) {
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("Create a project") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(projectName, { projectName = it }, label = { Text("Project name") }, singleLine = true)
                Text("Project type", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = projectType == "Video", onClick = { projectType = "Video" }, label = { Text("Video") })
                    FilterChip(selected = projectType == "Photo", onClick = { projectType = "Photo" }, label = { Text("Photo") })
                }
                Text("Aspect ratio", fontWeight = FontWeight.SemiBold)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(ratio); Icon(Icons.Default.ArrowDropDown, null) }
                    DropdownMenu(expanded, { expanded = false }) {
                        listOf("9:16", "16:9", "1:1", "4:5", "Custom").forEach { choice ->
                            DropdownMenuItem(text = { Text(choice) }, onClick = { ratio = choice; expanded = false })
                        }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = {
                scope.launch { repository.create(projectName, projectType, ratio) }
                projectName = ""; createDialog = false
            }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancel") } }
        )
    }
}
@Composable
private fun HomeScreen(projects: List<ProjectEntity>, modifier: Modifier, create: () -> Unit, openVideo: () -> Unit, openPhoto: () -> Unit, openAi: () -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(shape = RoundedCornerShape(26.dp), color = Ink) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("YOUR CREATIVE SPACE", color = AccentSoft, style = MaterialTheme.typography.labelMedium)
                Text("Make every frame\nfeel intentional.", color = SurfaceWhite,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Photo and video editing, together.", color = SurfaceWhite.copy(alpha = .72f))
                Button(onClick = create, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("New project")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recent projects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${projects.size} projects", color = Muted)
        }
        if (projects.isEmpty()) EmptyState("Your next great edit starts here", "Create a project to see it here.", create)
        else projects.take(4).forEach { ProjectCard(it, onClick = {}) }
        Text("Quick studios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTile("Video", Icons.Default.Movie, Modifier.weight(1f), openVideo)
            QuickTile("Photo", Icons.Default.Photo, Modifier.weight(1f), openPhoto)
            QuickTile("AI", Icons.Default.AutoAwesome, Modifier.weight(1f), openAi)
        }
    }
}
@Composable
private fun ProjectsScreen(projects: List<ProjectEntity>, repository: ProjectRepository, modifier: Modifier, create: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ProjectEntity?>(null) }
    var renaming by remember { mutableStateOf<ProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your projects", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
            placeholder = { Text("Search projects") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { sortByName = !sortByName }) {
                Icon(Icons.Default.Sort, null); Spacer(Modifier.width(6.dp))
                Text(if (sortByName) "Sorted by name" else "Sorted by recent")
            }
            Text("${projects.size} total", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        val filtered = projects.filter { it.name.contains(query, ignoreCase = true) }
            .let { if (sortByName) it.sortedBy { project -> project.name.lowercase() } else it }
        if (filtered.isEmpty()) EmptyState("No matching projects", "Create a photo or video project to get started.", create)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { project ->
                ProjectCard(project, onClick = {},
                    onDelete = { deleting = project },
                    onRename = { renaming = project; renameText = project.name },
                    onDuplicate = { scope.launch { repository.duplicate(project) } })
            }
        }
    }
    deleting?.let { project ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete project?") },
            text = { Text("Only the project record is removed. Original media files are never deleted.") },
            confirmButton = { TextButton(onClick = { scope.launch { repository.delete(project.id) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    renaming?.let { project ->
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename project") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Project name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                scope.launch { repository.rename(project.id, renameText) }
                renaming = null
            }, enabled = renameText.trim().isNotEmpty()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
}
@Composable
private fun ProjectCard(project: ProjectEntity, onClick: () -> Unit, onDelete: (() -> Unit)? = null, onRename: (() -> Unit)? = null, onDuplicate: (() -> Unit)? = null) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(52.dp), shape = RoundedCornerShape(14.dp), color = AccentSoft) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (project.type == "Photo") Icons.Default.Photo else Icons.Default.Movie, null, tint = Accent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.SemiBold)
                Text("${project.type} • ${project.aspectRatio}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (onRename != null) IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename project", tint = Muted) }
            if (onDuplicate != null) IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, "Duplicate project", tint = Muted) }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete project", tint = Muted) }
            if (onDelete == null && onRename == null && onDuplicate == null) Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}
@Composable
private fun StudioScreen(title: String, subtitle: String, icon: ImageVector, modifier: Modifier,
                         details: String, action: (() -> Unit)?) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(Modifier.size(64.dp), shape = RoundedCornerShape(20.dp), color = AccentSoft) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Accent, modifier = Modifier.size(30.dp)) }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted)
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Foundation workspace", fontWeight = FontWeight.SemiBold)
                Text(details, color = Muted)
                if (action != null) OutlinedButton(onClick = action) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Choose media")
                }
            }
        }
    }
}
@Composable
private fun EmptyState(title: String, subtitle: String, create: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.FolderOpen, null, tint = Accent, modifier = Modifier.size(32.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted)
            TextButton(onClick = create) { Text("Create project") }
        }
    }
}
@Composable
private fun QuickTile(label: String, icon: ImageVector, modifier: Modifier, open: () -> Unit) {
    Surface(onClick = open, modifier = modifier, shape = RoundedCornerShape(16.dp), color = SurfaceWhite,
        border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Accent)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}


@Composable
private fun MediaLibraryScreen(
    media: List<MediaEntity>,
    modifier: Modifier,
    importMedia: () -> Unit,
    removeMedia: (String) -> Unit
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Media Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your selected media stays on your device. Original files are not copied, changed or deleted.", color = Muted)
        Button(onClick = importMedia) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Import photos, videos or audio")
        }
        if (media.isEmpty()) {
            EmptyState("Your library is empty", "Import media to keep a persistent reference in this library.", importMedia)
        } else {
            Text("${media.size} saved item(s)", color = Muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(media, key = { it.uri }) { item ->
                    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    item.mimeType.startsWith("video/") -> Icons.Default.Movie
                                    item.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
                                    else -> Icons.Default.Image
                                }, null, tint = Accent
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(item.mimeType, color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { removeMedia(item.uri) }) {
                                Icon(Icons.Default.DeleteOutline, "Remove library reference")
                            }
                        }
                    }
                }
            }
        }
    }
}

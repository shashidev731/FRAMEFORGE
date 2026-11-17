package com.frameforge.app.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
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
import com.frameforge.app.data.EditHistoryRepository
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.provider.OpenableColumns
import android.net.Uri
import android.widget.ImageView
import android.widget.VideoView
import android.widget.MediaController
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
fun FrameforgeRoot(repository: ProjectRepository, mediaRepository: MediaRepository, editHistoryRepository: EditHistoryRepository) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var selectedProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var studioProjectId by remember { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("Video") }
    var ratio by remember { mutableStateOf("9:16") }
    var customRatioText by remember { mutableStateOf("3:2") }
    var importedCount by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val preferences = remember(context) { contextPreferences(context) }
    var workspaceMode by remember { mutableStateOf(preferences.getString("workspace_mode", "Simple") ?: "Simple") }
    var showOnboarding by remember { mutableStateOf(!preferences.getBoolean("onboarding_complete", false)) }
    val projects by repository.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val media by mediaRepository.media.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importedCount += uris.size
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.take(120)?.ifBlank { null }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.take(80)?.ifBlank { "Selected media" }
                ?: "Selected media"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            scope.launch { mediaRepository.add(uri.toString(), name, mime) }
        }
    }
    Scaffold(
        containerColor = Canvas,
        topBar = { TopAppBar(navigationIcon = {
            if (selectedProject != null) IconButton(onClick = { selectedProject = null }) { Icon(Icons.Default.ArrowBack, "Back to projects") }
            else if (studioProjectId != null) IconButton(onClick = {
                selectedProject = projects.firstOrNull { it.id == studioProjectId }
                studioProjectId = null
            }) { Icon(Icons.Default.ArrowBack, "Back to project") }
        }, title = { Column {
            Text(selectedProject?.name ?: studioProjectId?.let { id -> projects.firstOrNull { it.id == id }?.name } ?: "FRAMEFORGE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(when { selectedProject != null -> "Project workspace"; studioProjectId != null -> tab.label + " • Project editor"; else -> "Create without limits" }, color = Muted, style = MaterialTheme.typography.labelSmall)
        } }, actions = { if (selectedProject == null && studioProjectId == null) IconButton(onClick = { tab = Tab.Settings }) { Icon(Icons.Default.Settings, "Settings") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)) },
        bottomBar = { if (selectedProject == null && studioProjectId == null) NavigationBar(containerColor = SurfaceWhite) {
            listOf(Tab.Home, Tab.Projects, Tab.Video, Tab.Photo, Tab.AI, Tab.Library).forEach { item ->
                NavigationBarItem(selected = tab == item, onClick = { studioProjectId = null; tab = item },
                    icon = { Icon(item.icon, item.label) }, label = { Text(item.label, maxLines = 1) })
            }
        } },
        floatingActionButton = { if (selectedProject == null && studioProjectId == null && (tab == Tab.Home || tab == Tab.Projects)) FloatingActionButton(
            onClick = { createDialog = true }, containerColor = Accent, contentColor = SurfaceWhite
        ) { Icon(Icons.Default.Add, "Create project") } }
    ) { inset ->
        if (selectedProject != null) {
            val project = projects.firstOrNull { it.id == selectedProject?.id } ?: selectedProject!!
            ProjectWorkspaceScreen(project, Modifier.padding(inset), editHistoryRepository, onAspectRatioChange = { newRatio ->
                scope.launch { repository.updateAspectRatio(project.id, newRatio) }
                selectedProject = project.copy(aspectRatio = newRatio, updatedAt = System.currentTimeMillis())
            }, onOpenStudio = { destination -> studioProjectId = project.id; selectedProject = null; tab = destination })
        } else when (tab) {
            Tab.Home -> HomeScreen(projects, Modifier.padding(inset), { createDialog = true }, { studioProjectId = null; tab = Tab.Video }, { studioProjectId = null; tab = Tab.Photo }, { studioProjectId = null; tab = Tab.AI }, { selectedProject = it })
            Tab.Projects -> ProjectsScreen(projects, repository, Modifier.padding(inset), { createDialog = true }, { selectedProject = it })
            Tab.Video -> VideoStudioScreen(Modifier.padding(inset), studioProjectId, editHistoryRepository)
            Tab.Photo -> PhotoStudioScreen(Modifier.padding(inset), studioProjectId, editHistoryRepository)
            Tab.AI -> StudioScreen("AI Studio", "AI edits always require your approval", Icons.Default.AutoAwesome, Modifier.padding(inset),
                "AI tools are not active yet. No media is uploaded or modified in this foundation build.", null)
            Tab.Library -> MediaLibraryScreen(media, Modifier.padding(inset), { mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*")) }, { uri -> scope.launch { mediaRepository.delete(uri) } })
            Tab.Settings -> SettingsScreen(
                workspaceMode = workspaceMode,
                onWorkspaceModeChange = { mode ->
                    workspaceMode = mode
                    preferences.edit().putString("workspace_mode", mode).apply()
                },
                modifier = Modifier.padding(inset)
            )
        }
    }
    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {
                preferences.edit().putBoolean("onboarding_complete", true).apply()
                showOnboarding = false
            },
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = Accent) },
            title = { Text("Welcome to FRAMEFORGE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your creative workspace for photo and video editing.")
                    Text("Choose how you want to work. You can change this any time in Settings.", color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = workspaceMode == "Simple", onClick = { workspaceMode = "Simple" }, label = { Text("Simple") })
                        FilterChip(selected = workspaceMode == "Professional", onClick = { workspaceMode = "Professional" }, label = { Text("Professional") })
                    }
                    Text(
                        if (workspaceMode == "Simple") "Simple mode keeps the workspace approachable, with essential tools first."
                        else "Professional mode is intended for detailed, multi-track workflows as those tools become available.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    preferences.edit().putString("workspace_mode", workspaceMode)
                        .putBoolean("onboarding_complete", true).apply()
                    showOnboarding = false
                }) { Text("Get started") }
            },
            dismissButton = {
                TextButton(onClick = {
                    preferences.edit().putBoolean("onboarding_complete", true).apply()
                    showOnboarding = false
                }) { Text("Skip") }
            }
        )
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
                if (ratio == "Custom") {
                    OutlinedTextField(
                        value = customRatioText,
                        onValueChange = { customRatioText = it },
                        label = { Text("Custom ratio (width:height)") },
                        placeholder = { Text("3:2") },
                        singleLine = true
                    )
                }
            } },
            confirmButton = { TextButton(onClick = {
                val selectedRatio = if (ratio == "Custom") customRatioText.trim() else ratio
                scope.launch { repository.create(projectName, projectType, selectedRatio) }
                projectName = ""; createDialog = false
            }, enabled = ratio != "Custom" || isValidAspectRatio(customRatioText)) { Text("Create") } },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancel") } }
        )
    }
}
@Composable
private fun HomeScreen(projects: List<ProjectEntity>, modifier: Modifier, create: () -> Unit, openVideo: () -> Unit, openPhoto: () -> Unit, openAi: () -> Unit, openProject: (ProjectEntity) -> Unit) {
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
        else projects.take(4).forEach { project -> ProjectCard(project, onClick = { openProject(project) }) }
        Text("Quick studios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTile("Video", Icons.Default.Movie, Modifier.weight(1f), openVideo)
            QuickTile("Photo", Icons.Default.Photo, Modifier.weight(1f), openPhoto)
            QuickTile("AI", Icons.Default.AutoAwesome, Modifier.weight(1f), openAi)
        }
    }
}
@Composable
private fun ProjectsScreen(projects: List<ProjectEntity>, repository: ProjectRepository, modifier: Modifier, create: () -> Unit, openProject: (ProjectEntity) -> Unit) {
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
                ProjectCard(project, onClick = { openProject(project) },
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
private fun EmptyState(title: String, subtitle: String, create: () -> Unit, actionLabel: String = "Create project") {
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.FolderOpen, null, tint = Accent, modifier = Modifier.size(32.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted)
            TextButton(onClick = create) { Text(actionLabel) }
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
private fun ProjectWorkspaceScreen(
    project: ProjectEntity,
    modifier: Modifier,
    editHistoryRepository: EditHistoryRepository,
    onAspectRatioChange: (String) -> Unit,
    onOpenStudio: (Tab) -> Unit
) {
    var ratioMenu by remember { mutableStateOf(false) }
    var showCustomRatioDialog by remember { mutableStateOf(false) }
    var customRatioValue by remember { mutableStateOf("3:2") }
    Column(modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = Ink) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PROJECT WORKSPACE", color = AccentSoft, style = MaterialTheme.typography.labelMedium)
                Text(project.name, color = SurfaceWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${project.type} project • Updated ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(project.updatedAt))}",
                    color = SurfaceWhite.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Canvas settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Aspect ratio", fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { ratioMenu = true }) {
                        Text(project.aspectRatio)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = ratioMenu, onDismissRequest = { ratioMenu = false }) {
                        listOf("9:16", "16:9", "1:1", "4:5", "Custom").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                ratioMenu = false
                                if (option == "Custom") {
                                    customRatioValue = project.aspectRatio.takeIf { isValidAspectRatio(it) } ?: "3:2"
                                    showCustomRatioDialog = true
                                } else onAspectRatioChange(option)
                            })
                        }
                    }
                }
                Text("Changing this setting updates the project canvas preference. It does not crop or alter your original media.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Continue editing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onOpenStudio(if (project.type == "Photo") Tab.Photo else Tab.Video) }, modifier = Modifier.weight(1f)) {
                Icon(if (project.type == "Photo") Icons.Default.Photo else Icons.Default.Movie, null)
                Spacer(Modifier.width(6.dp))
                Text(if (project.type == "Photo") "Photo Studio" else "Video Studio")
            }
            OutlinedButton(onClick = { onOpenStudio(Tab.AI) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text("AI Studio")
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Editing engine status", fontWeight = FontWeight.SemiBold)
                Text("Basic photo adjustments and MP4 trim export are active in their studios. A unified multitrack timeline, advanced effects and shared render engine are still in development.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        EditorTimelinePanel(project.id, editHistoryRepository)
    }
    if (showCustomRatioDialog) {
        AlertDialog(
            onDismissRequest = { showCustomRatioDialog = false },
            title = { Text("Custom canvas ratio") },
            text = {
                OutlinedTextField(
                    value = customRatioValue,
                    onValueChange = { customRatioValue = it },
                    label = { Text("Width:height (e.g. 3:2)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAspectRatioChange(customRatioValue.trim())
                        showCustomRatioDialog = false
                    },
                    enabled = isValidAspectRatio(customRatioValue)
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showCustomRatioDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MediaLibraryScreen(
    media: List<MediaEntity>,
    modifier: Modifier,
    importMedia: () -> Unit,
    removeMedia: (String) -> Unit
) {
    var previewItem by remember { mutableStateOf<MediaEntity?>(null) }
    var mediaQuery by remember { mutableStateOf("") }
    var mediaFilter by remember { mutableStateOf("All") }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Media Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your selected media stays on your device. Original files are not copied, changed or deleted.", color = Muted)
        OutlinedTextField(
            value = mediaQuery,
            onValueChange = { mediaQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your media") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Photos", "Videos", "Audio").forEach { filter ->
                FilterChip(selected = mediaFilter == filter, onClick = { mediaFilter = filter }, label = { Text(filter) })
            }
        }
        Button(onClick = importMedia) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Import photos, videos or audio")
        }
        val filteredMedia = media.filter { item ->
            val matchesQuery = item.displayName.contains(mediaQuery, ignoreCase = true)
            val matchesType = when (mediaFilter) {
                "Photos" -> item.mimeType.startsWith("image/")
                "Videos" -> item.mimeType.startsWith("video/")
                "Audio" -> item.mimeType.startsWith("audio/")
                else -> true
            }
            matchesQuery && matchesType
        }.sortedBy { it.displayName.lowercase() }
        if (media.isEmpty()) {
            EmptyState("Your library is empty", "Import media to keep a persistent reference in this library.", importMedia, "Import media")
        } else if (filteredMedia.isEmpty()) {
            EmptyState("No matching media", "Try another search or media type.", importMedia, "Import media")
        } else {
            Text("${filteredMedia.size} of ${media.size} saved item(s)", color = Muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredMedia, key = { it.uri }) { item ->
                    Surface(onClick = { previewItem = item }, shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
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
    previewItem?.let { item ->
        MediaPreviewDialog(item = item, onDismiss = { previewItem = null })
    }
}

@Composable
private fun MediaPreviewDialog(item: MediaEntity, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = SurfaceWhite) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close preview") }
                }
                when {
                    item.mimeType.startsWith("image/") -> key(item.uri) { AndroidView(
                        factory = { viewContext ->
                            ImageView(viewContext).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageURI(Uri.parse(item.uri))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp)
                    ) }
                    item.mimeType.startsWith("video/") -> key(item.uri) { AndroidView(
                        factory = { viewContext ->
                            val videoView = VideoView(viewContext)
                            val controller = MediaController(viewContext)
                            controller.setAnchorView(videoView)
                            videoView.setMediaController(controller)
                            videoView.setVideoURI(Uri.parse(item.uri))
                            videoView.setOnPreparedListener { player ->
                                player.isLooping = false
                                videoView.start()
                            }
                            videoView
                        },
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        onRelease = { it.stopPlayback() }
                    ) }
                    item.mimeType.startsWith("audio/") -> AudioPreview(item.uri)
                    else -> Text("Preview is not available for this file type.", color = Muted)
                }
                Text(item.mimeType, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AudioPreview(uri: String) {
    val context = LocalContext.current
    var ready by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) { android.media.MediaPlayer() }
    DisposableEffect(player, uri) {
        try {
            player.setDataSource(context, Uri.parse(uri))
            player.setOnPreparedListener { ready = true }
            player.setOnCompletionListener { playing = false }
            player.prepareAsync()
        } catch (_: Exception) {
            ready = false
        }
        onDispose {
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.AudioFile, null, tint = Accent, modifier = Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Text(if (ready) "Audio ready" else "Preparing audio…", fontWeight = FontWeight.SemiBold)
            Text("Local playback • original file unchanged", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            if (playing) {
                player.pause()
                playing = false
            } else {
                player.start()
                playing = true
            }
        }, enabled = ready) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text(if (playing) "Pause" else "Play")
        }
    }
}

private fun contextPreferences(context: android.content.Context) =
    context.getSharedPreferences("frameforge_preferences", android.content.Context.MODE_PRIVATE)

@Composable
private fun SettingsScreen(
    workspaceMode: String,
    onWorkspaceModeChange: (String) -> Unit,
    modifier: Modifier
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Workspace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Choose the editing workspace that suits your workflow.", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = workspaceMode == "Simple", onClick = { onWorkspaceModeChange("Simple") }, label = { Text("Simple") })
                    FilterChip(selected = workspaceMode == "Professional", onClick = { onWorkspaceModeChange("Professional") }, label = { Text("Professional") })
                }
                Text(
                    if (workspaceMode == "Simple") "Essential controls are prioritized. Advanced controls can be added as needed."
                    else "A detailed workspace for advanced editing workflows.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Storage & privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Projects and media references are stored locally. Imported originals are not modified or deleted when you remove a library reference.", color = Muted)
                Text("Cloud sync and AI processing are not enabled in this foundation build.", color = Muted)
            }
        }
        Text("FRAMEFORGE • Foundation build", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

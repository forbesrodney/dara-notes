package app.foamforandroid.ui

import app.foamforandroid.core.ioDispatcher
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.foamforandroid.MainViewModel
import app.foamforandroid.Screen
import app.foamforandroid.core.FolderNode
import app.foamforandroid.core.Note
import app.foamforandroid.core.NoteTree
import app.foamforandroid.core.SyncState
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val notes by vm.notes.collectAsState()
    val sync by vm.syncState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Note?>(null) }
    var menuFor by remember { mutableStateOf<Note?>(null) }
    var toRename by remember { mutableStateOf<Note?>(null) }
    var toMove by remember { mutableStateOf<Note?>(null) }
    var filtered by remember { mutableStateOf(notes) }
    val treeView by vm.notesTreeView
    var expanded by vm.notesExpanded

    LaunchedEffect(Unit) { vm.refresh() }
    val listState = vm.notesListState
    LaunchedEffect(notes, query) {
        val updated = withContext(ioDispatcher) { vm.repo.search(query, notes) }
        // The list keeps its place by item, so a note that moves to the top (just edited or
        // synced) would land above the visible area. If we were at the top, stay there.
        val atTop = listState.firstVisibleItemIndex == 0
        filtered = updated
        if (atTop) listState.scrollToItem(0)
    }
    val indexPath = vm.settings.indexFile
    val index = notes.firstOrNull { it.path.equals(indexPath, ignoreCase = true) }

    val desktop = vm.platform.desktop
    // Cmd+N in the desktop app.
    LaunchedEffect(vm.newNoteRequested.value) {
        if (vm.newNoteRequested.value) {
            vm.newNoteRequested.value = false
            showNew = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                actions = {
                    if (index != null) {
                        IconButton(onClick = { vm.openPath(index.path) }) {
                            Icon(Icons.Default.Home, contentDescription = "Open ${index.path}")
                        }
                    }
                    // On the desktop, Today (Cmd+D) and New note (Cmd+N) follow Home; on phones
                    // and tablets they float at the bottom, within thumb's reach.
                    if (desktop) {
                        IconButton(onClick = vm::openDailyNote) {
                            Icon(Icons.Default.DateRange, contentDescription = "Today's note")
                        }
                        IconButton(onClick = { showNew = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New note")
                        }
                    }
                    IconButton(onClick = { vm.navigate(Screen.Graph()) }) {
                        Icon(GraphIcon, contentDescription = "Graph")
                    }
                    IconButton(onClick = vm::syncNow, enabled = !sync.running) {
                        if (sync.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                    }
                    IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    Box {
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text("Tags") },
                                onClick = { more = false; vm.navigate(Screen.Tags) },
                            )
                            DropdownMenuItem(
                                text = { Text("Orphaned notes") },
                                onClick = { more = false; vm.navigate(Screen.Orphans) },
                            )
                            DropdownMenuItem(
                                text = { Text("Placeholders") },
                                onClick = { more = false; vm.navigate(Screen.Placeholders) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (desktop) return@Scaffold
            // Two matching buttons: the same height, and as wide as the wider of the two.
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListActionButton(Icons.Default.DateRange, "Today", vm::openDailyNote)
                ListActionButton(Icons.Default.Add, "New note") { showNew = true }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SyncStatusLine(sync)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search notes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.setNotesTreeView(!treeView) }) {
                    if (treeView) Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show as list")
                    else Icon(FolderIcon, contentDescription = "Show by folder")
                }
            }

            // One note in either view; long-press opens Rename / Move / Delete.
            val noteItem: @Composable (Note, Dp, String) -> Unit = { note, indent, details ->
                Box {
                    ListItem(
                        headlineContent = { Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                details + " · " + relativeTime(note.modified),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { vm.openPath(note.path) },
                                onLongClick = { menuFor = note },
                            )
                            .padding(start = indent),
                    )
                    DropdownMenu(
                        expanded = menuFor == note,
                        onDismissRequest = { menuFor = null },
                        offset = DpOffset(16.dp, 0.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuFor = null; toRename = note },
                        )
                        DropdownMenuItem(
                            text = { Text("Move to folder") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                            onClick = { menuFor = null; toMove = note },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuFor = null; toDelete = note },
                        )
                    }
                }
            }
            val empty: @Composable () -> Unit = {
                Text(
                    if (query.isBlank()) "No notes yet. Tap “New note” or the calendar for today's note."
                    else "No matches",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PullToRefreshBox(
                isRefreshing = sync.running,
                onRefresh = vm::syncNow,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!treeView) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
                        items(filtered, key = { it.path }) { note ->
                            noteItem(note, 0.dp, note.path)
                            HorizontalDivider()
                        }
                        if (filtered.isEmpty()) item { empty() }
                    }
                } else {
                    // While searching, every folder with a match is open.
                    val searching = query.isNotBlank()
                    val rows = remember(filtered, expanded, searching) {
                        treeRows(NoteTree.build(filtered)) { searching || it in expanded }
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = vm.notesTreeListState, contentPadding = PaddingValues(bottom = 160.dp)) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is TreeRow.Folder -> FolderLine(row) {
                                    if (!searching) {
                                        val p = row.folder.path
                                        expanded = if (p in expanded) expanded - p else expanded + p
                                    }
                                }
                                // Notes line up with the folder icons at the same level.
                                is TreeRow.File -> noteItem(row.note, 20.dp + TREE_INDENT * row.depth, row.note.path.substringAfterLast('/'))
                            }
                        }
                        if (rows.isEmpty()) item { empty() }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewNoteDialog(onDismiss = { showNew = false }, onCreate = { title ->
            showNew = false
            vm.createNote(title)
        })
    }
    toRename?.let { note ->
        RenameNoteDialog(vm, note.path, onDismiss = { toRename = null }) { newPath ->
            toRename = null
            vm.relocateNote(note.path, newPath)
        }
    }
    toMove?.let { note ->
        MoveNoteDialog(vm, note.path, onDismiss = { toMove = null }) { newPath ->
            toMove = null
            vm.relocateNote(note.path, newPath)
        }
    }
    toDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete note?") },
            text = { Text("${note.path} will be deleted and the deletion synced.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteNote(note.path); toDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SyncStatusLine(sync: SyncState) {
    val text = when {
        sync.running -> "Syncing…"
        sync.lastError != null -> "Sync error: ${sync.lastHint ?: sync.lastError}"
        sync.conflicts.isNotEmpty() -> "Conflicts to resolve: ${sync.conflicts.joinToString()}"
        sync.lastSyncAt > 0 -> "Synced ${relativeTime(sync.lastSyncAt)}"
        else -> null
    } ?: return
    val isProblem = sync.lastError != null || sync.conflicts.isNotEmpty()
    val icon: (@Composable () -> Unit)? = if (isProblem) {
        { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
    } else null
    ListItem(
        leadingContent = icon,
        headlineContent = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun NewNoteDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "It will be linked from the index automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

private val TREE_INDENT = 20.dp

private sealed interface TreeRow {
    val key: String
    data class Folder(val folder: FolderNode, val depth: Int, val open: Boolean) : TreeRow {
        override val key get() = "folder:" + folder.path
    }
    data class File(val note: Note, val depth: Int) : TreeRow {
        override val key get() = "note:" + note.path
    }
}

/** Flattens the folder tree into rows: folders first, then notes, descending into open folders. */
private fun treeRows(root: FolderNode, isOpen: (String) -> Boolean): List<TreeRow> = buildList {
    fun add(folder: FolderNode, depth: Int) {
        for (sub in folder.folders) {
            val open = isOpen(sub.path)
            add(TreeRow.Folder(sub, depth, open))
            if (open) add(sub, depth + 1)
        }
        folder.notes.forEach { add(TreeRow.File(it, depth)) }
    }
    add(root, 0)
}

@Composable
private fun FolderLine(row: TreeRow.Folder, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 8.dp + TREE_INDENT * row.depth, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.open) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (row.open) "Collapse" else "Expand",
        )
        Spacer(Modifier.width(4.dp))
        Icon(FolderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            row.folder.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.folder.noteCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One of the note list's floating buttons. They share a width (the column's), so the icon and
 * label sit at the start, lining up from one button to the next.
 */
@Composable
private fun ListActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(label)
        Spacer(Modifier.weight(1f))
    }
}

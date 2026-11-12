package app.daranotes.ui

import app.daranotes.core.ioDispatcher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.daranotes.MainViewModel
import app.daranotes.core.Note
import app.daranotes.core.TagNode
import app.daranotes.core.Tags
import kotlinx.coroutines.withContext

private sealed interface TagRow {
    val key: String
    data class Tag(val node: TagNode, val depth: Int, val open: Boolean) : TagRow {
        override val key get() = "tag:" + node.tag.lowercase()
    }
    data class NoteRow(val note: Note, val depth: Int, val parent: String) : TagRow {
        override val key get() = "note:$parent:${note.path}"
    }
}

/** Foam's Tag Explorer: tags as a tree (`dev/git` under `dev`), each with its notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val notes by vm.notes.collectAsState()
    var tree by remember { mutableStateOf<List<TagNode>?>(null) }
    // Kept in the ViewModel so opening a note and coming back keeps the tree as it was.
    var expanded by vm.tagsExpanded
    var query by vm.tagsQuery

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(notes) { tree = withContext(ioDispatcher) { vm.repo.tags() } }

    val byPath = remember(notes) { notes.associateBy { it.path } }
    val shown = remember(tree, query) { tree?.let { Tags.filter(it, query) } }
    val searching = query.isNotBlank()
    val rows = remember(shown, expanded, searching, byPath) {
        buildList {
            fun add(n: TagNode, depth: Int) {
                val open = searching || n.tag.lowercase() in expanded
                add(TagRow.Tag(n, depth, open))
                if (!open) return
                n.children.forEach { add(it, depth + 1) }
                n.notes.mapNotNull { byPath[it] }.sortedBy { it.title.lowercase() }
                    .forEach { add(TagRow.NoteRow(it, depth + 1, n.tag.lowercase())) }
            }
            shown.orEmpty().forEach { add(it, 0) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Tip("Back") {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = { Text(tree?.let { "Tags (${countTags(it)})" } ?: "Tags") },
                actions = {
                    if (expanded.isNotEmpty() && !searching) {
                        TextButton(onClick = { expanded = emptySet() }) { Text("Collapse") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                },
                placeholder = { Text("Filter tags") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when {
                shown == null -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                shown.isEmpty() -> Text(
                    if (searching) "No tags match \"$query\"."
                    else "No tags yet. Add tags in front matter (tags: [idea, dev/git]) or inline as #idea.",
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), state = vm.tagsListState) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is TagRow.Tag -> TagLine(row) {
                                val k = row.node.tag.lowercase()
                                if (!searching) expanded = if (k in expanded) expanded - k else expanded + k
                            }
                            is TagRow.NoteRow -> NoteLine(row) { vm.openPath(row.note.path) }
                        }
                    }
                }
            }
        }
    }
}

private fun countTags(tree: List<TagNode>): Int = tree.sumOf { 1 + countTags(it.children) }

@Composable
private fun TagLine(row: TagRow.Tag, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 8.dp + 20.dp * row.depth, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.open) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (row.open) "Collapse" else "Expand",
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "#" + row.node.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.node.noteCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteLine(row: TagRow.NoteRow, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 8.dp + 20.dp * row.depth + 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(row.note.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            row.note.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

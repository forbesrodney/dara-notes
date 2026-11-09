package app.foamforandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import app.foamforandroid.MainViewModel
import app.foamforandroid.core.FoamConfig
import app.foamforandroid.core.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Foam's Orphans panel: notes with no links to or from other notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrphansScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val notes by vm.notes.collectAsState()
    var orphans by remember { mutableStateOf<List<Note>?>(null) }
    var excluded by remember { mutableStateOf(emptyList<String>()) }
    val indexPath = vm.settings.indexFile

    LaunchedEffect(Unit) { vm.refresh() }
    // Recomputed whenever notes change (edits, syncs, linking one from the index).
    LaunchedEffect(notes) {
        withContext(Dispatchers.IO) {
            excluded = FoamConfig(vm.repo.root).orphansExclude
            orphans = vm.repo.orphans()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(orphans?.let { "Orphaned notes (${it.size})" } ?: "Orphaned notes") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Notes with no links to or from any other note. Tags don't count as links." +
                    if (excluded.isEmpty()) "" else "\nNot shown (foam.orphans.exclude): ${excluded.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            val list = orphans
            when {
                list == null -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                list.isEmpty() -> Text(
                    "No orphaned notes. Every note links to, or is linked from, another note.",
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.path }) { note ->
                        ListItem(
                            headlineContent = { Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    note.path,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = if (note.path.equals(indexPath, ignoreCase = true)) null else {
                                {
                                    TextButton(onClick = { vm.addToIndex(note.path) }) { Text("Add to index") }
                                }
                            },
                            modifier = Modifier.clickable { vm.openPath(note.path) },
                        )
                    }
                }
            }
        }
    }
}

package app.foamforandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.foamforandroid.core.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Foam's Placeholders panel: wikilinks to notes that don't exist, and where they're used. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholdersScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val notes by vm.notes.collectAsState()
    var missing by remember { mutableStateOf<List<NoteRepository.MissingNote>?>(null) }
    var excluded by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) { vm.refresh() }
    // Recomputed whenever notes change, so a note created from here drops off the list.
    LaunchedEffect(notes) {
        withContext(Dispatchers.IO) {
            excluded = FoamConfig(vm.repo.root).placeholdersExclude
            missing = vm.repo.placeholders()
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
                title = { Text(missing?.let { "Placeholders (${it.size})" } ?: "Placeholders") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Wikilinks to notes that don't exist yet. Create the note, or open a note that links to it." +
                    if (excluded.isEmpty()) "" else "\nLinks not counted from (foam.placeholders.exclude): ${excluded.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            val list = missing
            when {
                list == null -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                list.isEmpty() -> Text(
                    "No placeholders. Every wikilink points to an existing file.",
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.target.lowercase() }) { p ->
                        Column(Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = {
                                    Text("[[${p.target}]]", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    val n = p.linkedFrom.size
                                    Text("Linked from $n note${if (n == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                                },
                                trailingContent = {
                                    TextButton(onClick = { vm.openWikilink(p.target) }) { Text("Create") }
                                },
                            )
                            for (note in p.linkedFrom) {
                                Text(
                                    "${note.title}  ·  ${note.path}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.openPath(note.path) }
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                )
                            }
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

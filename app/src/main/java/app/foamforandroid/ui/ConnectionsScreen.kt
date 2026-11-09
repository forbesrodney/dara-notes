package app.foamforandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.foamforandroid.MainViewModel
import app.foamforandroid.Screen
import app.foamforandroid.core.Backlink
import app.foamforandroid.core.NoteLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Material "link" icon (not in material-icons-core). */
val LinkIcon: ImageVector by lazy {
    ImageVector.Builder("Link", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7" +
                "c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1" +
                "s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * Foam's Connections panel for one note: every link in it and what each points to, and
 * the notes that link to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(vm: MainViewModel, screen: Screen.Connections, snackbar: SnackbarHostState) {
    val notes by vm.notes.collectAsState()
    var data by remember { mutableStateOf<Pair<List<NoteLink>, List<Backlink>>?>(null) }
    var showOut by remember { mutableStateOf(true) }
    val path = screen.path
    val byPath = remember(notes) { notes.associateBy { it.path } }
    val title = byPath[path]?.title ?: path.substringAfterLast('/').removeSuffix(".md")
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(notes, path) { data = withContext(Dispatchers.IO) { vm.repo.connections(path) } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Connections")
                        Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val (links, backlinks) = data ?: (null to null)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                FilterChip(
                    selected = showOut,
                    onClick = { showOut = true },
                    label = { Text("Links out" + (links?.let { " (${it.size})" } ?: "")) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !showOut,
                    onClick = { showOut = false },
                    label = { Text("Links in" + (backlinks?.let { " (${it.size})" } ?: "")) },
                )
            }
            HorizontalDivider()
            when {
                links == null || backlinks == null -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                showOut && links.isEmpty() -> Text("This note has no links.", Modifier.padding(16.dp))
                !showOut && backlinks.isEmpty() -> Text("No other note links here.", Modifier.padding(16.dp))
                showOut -> LazyColumn(Modifier.fillMaxSize()) {
                    items(links) { link ->
                        OutgoingLink(
                            link,
                            titleOf = { p -> byPath[p]?.title },
                            onOpen = {
                                when (link.status) {
                                    NoteLink.Status.NOTE -> link.resolved?.let { vm.openPath(it) }
                                    NoteLink.Status.EXTERNAL ->
                                        runCatching { uri.openUri(link.target) }
                                            .onFailure { scope.launch { snackbar.showSnackbar("Can't open ${link.target}") } }
                                    else -> {}
                                }
                            },
                            onCreate = { vm.openWikilink(link.target) },
                        )
                        HorizontalDivider()
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(backlinks) { b ->
                        Column(
                            Modifier.fillMaxWidth().clickable { vm.openPath(b.source) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(byPath[b.source]?.title ?: b.source, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${b.source} · line ${b.line}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                b.context,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun OutgoingLink(link: NoteLink, titleOf: (String) -> String?, onOpen: () -> Unit, onCreate: () -> Unit) {
    val opens = link.status == NoteLink.Status.NOTE || link.status == NoteLink.Status.EXTERNAL
    val heading = link.heading?.let { " › $it" } ?: ""
    val (where, color) = when (link.status) {
        NoteLink.Status.NOTE ->
            "${titleOf(link.resolved!!) ?: link.resolved}$heading · ${link.resolved}" to MaterialTheme.colorScheme.primary
        NoteLink.Status.FILE -> "File: ${link.resolved}" to MaterialTheme.colorScheme.onSurface
        NoteLink.Status.MISSING ->
            (if (link.kind == NoteLink.Kind.WIKILINK || link.kind == NoteLink.Kind.EMBED) "Missing note: ${link.target}"
            else "Not found: ${link.target}") to MaterialTheme.colorScheme.error
        NoteLink.Status.EXTERNAL -> link.target to MaterialTheme.colorScheme.primary
        NoteLink.Status.SAME_NOTE -> "This note$heading" to MaterialTheme.colorScheme.onSurface
    }
    val kind = when (link.kind) {
        NoteLink.Kind.WIKILINK -> "Wikilink"
        NoteLink.Kind.EMBED -> "Embed"
        NoteLink.Kind.LINK -> "Link"
        NoteLink.Kind.IMAGE -> "Image"
        NoteLink.Kind.URL -> "URL"
    }
    Row(
        Modifier.fillMaxWidth().let { if (opens) it.clickable(onClick = onOpen) else it }
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$kind · line ${link.line}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                link.written,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "→ $where",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (link.status == NoteLink.Status.MISSING && (link.kind == NoteLink.Kind.WIKILINK || link.kind == NoteLink.Kind.EMBED)) {
            TextButton(onClick = onCreate) { Text("Create") }
        }
    }
}

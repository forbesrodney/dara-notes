package app.foamforandroid.ui

import app.foamforandroid.core.ioDispatcher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.foamforandroid.MainViewModel
import app.foamforandroid.Screen
import app.foamforandroid.core.Graph
import app.foamforandroid.core.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json as JsonParser
import kotlinx.coroutines.withContext

/** Material "account tree" icon (not in material-icons-core), used for the graph. */
val GraphIcon: ImageVector by lazy {
    ImageVector.Builder("Graph", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes("M22,11V3h-7v3H9V3H2v8h7V8h2v10h4v3h7v-8h-7v3h-2V8h2v3z"),
        fill = SolidColor(Color.Black),
    ).build()
}

private data class Selected(val id: String, val type: String, val label: String, val path: String?, val degree: Int)

/** Parses the page's selection JSON; null when nothing is selected. */
private fun parseSelected(json: String): Selected? {
    if (json.isEmpty()) return null
    val o: JsonObject = runCatching { JsonParser.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
    fun str(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
    return Selected(
        str("id") ?: return null, str("type") ?: "note", str("label") ?: "",
        str("path")?.takeIf { it.isNotEmpty() && it != "null" },
        (o["degree"] as? JsonPrimitive)?.intOrNull ?: 0,
    )
}

private fun css(c: Color): String {
    val argb = c.toArgb()
    return "rgba(${(argb shr 16) and 255},${(argb shr 8) and 255},${argb and 255},${(c.alpha * 1000).roundToInt() / 1000.0})"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(vm: MainViewModel, screen: Screen.Graph, snackbar: SnackbarHostState) {
    val notes by vm.notes.collectAsState()
    var graph by remember { mutableStateOf<Graph?>(null) }
    var selected by remember { mutableStateOf<Selected?>(null) }
    var showTags by remember { mutableStateOf(vm.settings.graphTags) }
    var showMissing by remember { mutableStateOf(vm.settings.graphPlaceholders) }
    val view = remember { GraphPage() }
    val scheme = MaterialTheme.colorScheme

    // Rebuild whenever the note list changes (edits, syncs, new notes).
    LaunchedEffect(notes) { graph = withContext(ioDispatcher) { vm.repo.graph() } }

    val theme = listOf(
        "fg" to scheme.onSurface, "note" to scheme.primary, "tag" to scheme.tertiary,
        "placeholder" to scheme.outline, "link" to scheme.outline.copy(alpha = 0.35f), "linkHi" to scheme.primary,
    ).joinToString(",", "{", "}") { (k, c) -> "${Json.quote(k)}:${Json.quote(css(c))}" }
    LaunchedEffect(theme) { view.call("setTheme($theme)") }

    LaunchedEffect(graph, showTags, showMissing) {
        val g = graph ?: return@LaunchedEffect
        val opts = "{\"tags\":$showTags,\"placeholders\":$showMissing}"
        view.call("setGraph(${g.toJson()}, $opts, ${vm.graphLayout ?: "null"})")
    }
    LaunchedEffect(graph != null) {
        if (graph != null && screen.focus != null) view.call("focusNode(${Json.quote(screen.focus)})")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Graph") },
                actions = { TextButton(onClick = { view.call("fit()") }) { Text("Fit") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showTags,
                    onClick = { showTags = !showTags; vm.settings.graphTags = showTags },
                    label = { Text("Tags") },
                )
                FilterChip(
                    selected = showMissing,
                    onClick = { showMissing = !showMissing; vm.settings.graphPlaceholders = showMissing },
                    label = { Text("Missing notes") },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                GraphWebView(
                    page = view,
                    background = scheme.surface,
                    onSelected = { selected = parseSelected(it) },
                    onLayout = { vm.graphLayout = it },
                    modifier = Modifier.fillMaxSize(),
                )
                if (graph == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                if (clickableOverWebViews) {
                    selected?.let { s -> SelectedCard(s, vm, Modifier.align(Alignment.BottomCenter)) }
                }
            }
            if (!clickableOverWebViews) selected?.let { s -> SelectedCard(s, vm, Modifier) }
        }
    }
}

@Composable
private fun SelectedCard(s: Selected, vm: MainViewModel, modifier: Modifier) {
    Card(modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (s.type) {
                        "tag" -> "Tag · ${s.degree} note${if (s.degree == 1) "" else "s"}"
                        "placeholder" -> "Missing note · linked from ${s.degree}"
                        else -> "${s.path} · ${s.degree} connection${if (s.degree == 1) "" else "s"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (s.type) {
                "note" -> Button(onClick = { s.path?.let { vm.openPath(it) } }) { Text("Open") }
                "placeholder" -> OutlinedButton(onClick = { vm.openWikilink(s.label) }) { Text("Create") }
                "tag" -> OutlinedButton(onClick = { vm.showTag(s.label) }) { Text("Notes") }
            }
        }
    }
}

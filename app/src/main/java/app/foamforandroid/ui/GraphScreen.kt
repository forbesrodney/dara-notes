package app.foamforandroid.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import app.foamforandroid.MainViewModel
import app.foamforandroid.Screen
import app.foamforandroid.core.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Material "account tree" icon (not in material-icons-core), used for the graph. */
val GraphIcon: ImageVector by lazy {
    ImageVector.Builder("Graph", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes("M22,11V3h-7v3H9V3H2v8h7V8h2v10h4v3h7v-8h-7v3h-2V8h2v3z"),
        fill = SolidColor(Color.Black),
    ).build()
}

private data class Selected(val id: String, val type: String, val label: String, val path: String?, val degree: Int)

/** Holds the graph WebView and pushes data to it once the page is ready. */
private class GraphView {
    var webView: WebView? = null
    var ready = false
    var pending: (() -> Unit)? = null

    fun call(script: String) {
        val wv = webView
        if (wv == null || !ready) { val prev = pending; pending = { prev?.invoke(); call(script) }; return }
        wv.evaluateJavascript(script, null)
    }
}

private fun css(c: Color): String {
    val argb = c.toArgb()
    return "rgba(${(argb shr 16) and 255},${(argb shr 8) and 255},${argb and 255},${"%.3f".format(c.alpha)})"
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GraphScreen(vm: MainViewModel, screen: Screen.Graph, snackbar: SnackbarHostState) {
    val notes by vm.notes.collectAsState()
    var graph by remember { mutableStateOf<Graph?>(null) }
    var selected by remember { mutableStateOf<Selected?>(null) }
    var showTags by remember { mutableStateOf(vm.settings.graphTags) }
    var showMissing by remember { mutableStateOf(vm.settings.graphPlaceholders) }
    val view = remember { GraphView() }
    val scheme = MaterialTheme.colorScheme

    // Rebuild whenever the note list changes (edits, syncs, new notes).
    LaunchedEffect(notes) { graph = withContext(Dispatchers.IO) { vm.repo.graph() } }

    val theme = JSONObject()
        .put("fg", css(scheme.onSurface)).put("note", css(scheme.primary)).put("tag", css(scheme.tertiary))
        .put("placeholder", css(scheme.outline)).put("link", css(scheme.outline.copy(alpha = 0.35f)))
        .put("linkHi", css(scheme.primary)).toString()
    LaunchedEffect(theme) { view.call("setTheme($theme)") }

    LaunchedEffect(graph, showTags, showMissing) {
        val g = graph ?: return@LaunchedEffect
        val opts = JSONObject().put("tags", showTags).put("placeholders", showMissing)
        view.call("setGraph(${g.toJson()}, $opts, ${vm.graphLayout ?: "null"})")
    }
    LaunchedEffect(graph != null) {
        if (graph != null && screen.focus != null) view.call("focusNode(${JSONObject.quote(screen.focus)})")
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
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val loader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                            .build()
                        val main = Handler(Looper.getMainLooper())
                        WebView(ctx).apply {
                            setBackgroundColor(scheme.surface.toArgb())
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                                    loader.shouldInterceptRequest(r.url)
                                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true
                            }
                            addJavascriptInterface(object {
                                @JavascriptInterface fun ready() {
                                    main.post {
                                        view.ready = true
                                        val p = view.pending; view.pending = null; p?.invoke()
                                    }
                                }
                                @JavascriptInterface fun selected(json: String) {
                                    main.post {
                                        selected = if (json.isEmpty()) null else JSONObject(json).let {
                                            Selected(
                                                it.getString("id"), it.getString("type"), it.getString("label"),
                                                it.optString("path").takeIf { p -> p.isNotEmpty() && p != "null" },
                                                it.optInt("degree"),
                                            )
                                        }
                                    }
                                }
                                @JavascriptInterface fun layout(json: String) { main.post { vm.graphLayout = json } }
                            }, "Android")
                            loadUrl("https://appassets.androidplatform.net/assets/graph.html")
                            view.webView = this
                        }
                    },
                    onRelease = { wv -> view.webView = null; view.ready = false; wv.destroy() },
                )
                if (graph == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                selected?.let { s -> SelectedCard(s, vm, Modifier.align(Alignment.BottomCenter)) }
            }
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

package app.daranotes.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import okio.Path

/** Callbacks from the preview page. Held in a mutable holder so the page never goes stale. */
class PreviewCallbacks {
    var resolve: (String) -> String? = { null }
    var readNote: (String) -> String = { "" }
    var openWikilink: (String) -> Unit = {}
    var openPath: (String) -> Unit = {}
    var toggleTask: (Int) -> Unit = {}
}

/** The rendered note: markdown-it with Foam's wikilinks, embeds, math and diagrams, in a web view. */
@Composable
expect fun MarkdownPreview(
    markdown: String,
    notePath: String,
    repoRoot: Path,
    callbacks: PreviewCallbacks,
    modifier: Modifier = Modifier,
)

/**
 * Talks to the graph page. Scripts sent before the page is ready are queued and run once it
 * is; the platform's [GraphWebView] calls [attach] and [onReady].
 */
class GraphPage {
    private var send: ((String) -> Unit)? = null
    private var ready = false
    private val pending = mutableListOf<String>()

    fun call(script: String) {
        val s = send
        if (s == null || !ready) pending += script else s(script)
    }

    fun attach(sender: (String) -> Unit) { send = sender }

    fun onReady() {
        ready = true
        val queued = pending.toList()
        pending.clear()
        queued.forEach { send?.invoke(it) }
    }

    fun detach() { send = null; ready = false }
}

/**
 * The force-directed graph page (graph.html) in a web view. [onSelected] gets the tapped node
 * as JSON (empty when nothing is selected); [onLayout] gets the positions to restore later.
 */
@Composable
expect fun GraphWebView(
    page: GraphPage,
    background: Color,
    onSelected: (String) -> Unit,
    onLayout: (String) -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * Whether buttons drawn over a web view get their clicks. On the desktop the web view is a
 * native component that takes the clicks, so controls go beside it instead.
 */
expect val clickableOverWebViews: Boolean

/** The platform's color scheme (Android 12+ uses the wallpaper colors). */
@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme

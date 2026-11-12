package app.daranotes.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.UIKitView
import app.daranotes.core.Json
import app.daranotes.core.RepoPaths
import okio.Path
import platform.UIKit.UIColor
import platform.WebKit.WKWebView

// iOS versions of the platform views: WKWebView pages (see WebViews.ios.kt).

private fun uiColor(c: Color): UIColor {
    val argb = c.toArgb()
    return UIColor.colorWithRed(
        ((argb shr 16) and 255) / 255.0, ((argb shr 8) and 255) / 255.0, (argb and 255) / 255.0, c.alpha.toDouble(),
    )
}

/** The preview's state: queues the latest render until the page is ready. */
private class IosPreview(var callbacks: PreviewCallbacks) : WebHost {
    var webView: WKWebView? = null
        set(value) {
            field = value
            ready = false // a new web view: wait for the page's ready() call
        }
    override var repoDir: String? = null
    private var ready = false
    private var pending: String? = null
    private var lastRendered: String? = null

    fun render(script: String) {
        pending = script
        val wv = webView ?: return
        if (!ready || script == lastRendered) return
        lastRendered = script
        wv.evaluateJavaScript(script, completionHandler = null)
    }

    override fun call(fn: String, arg: String): String = when (fn) {
        "resolve" -> callbacks.resolve(arg) ?: ""
        "readNote" -> callbacks.readNote(arg)
        else -> ""
    }

    override fun message(fn: String, arg: String) {
        when (fn) {
            "ready" -> {
                ready = true
                lastRendered = null
                pending?.let(::render)
            }
            "openWikilink" -> callbacks.openWikilink(arg)
            "openLink" -> openLink(arg)
            "toggleTask" -> arg.toDoubleOrNull()?.toInt()?.let(callbacks.toggleTask)
        }
    }

    private fun openLink(url: String) {
        val repoPrefix = "$ORIGIN/repo/"
        if (url.startsWith(repoPrefix)) {
            val root = repoDir ?: return
            val raw = url.removePrefix(repoPrefix).substringBefore('#')
            val rel = (RepoPaths.percentDecode(raw) ?: raw).trimEnd('/')
            val target = when {
                rel.endsWith(".md", ignoreCase = true) -> rel
                isFile(root, "$rel.md") -> "$rel.md"
                else -> return
            }
            if (isFile(root, target)) callbacks.openPath(target) else callbacks.openWikilink(target.removeSuffix(".md"))
            return
        }
        openExternally(url)
    }
}

@Composable
actual fun MarkdownPreview(
    markdown: String,
    notePath: String,
    repoRoot: Path,
    callbacks: PreviewCallbacks,
    modifier: Modifier,
) {
    val dark = isSystemInDarkTheme()
    val background = if (dark) Color(0xFF121417) else Color.White
    val state = remember { IosPreview(callbacks) }
    state.callbacks = callbacks
    state.repoDir = repoRoot.toString()
    val handler = remember { AppWebHandler(state) }
    val dir = notePath.substringBeforeLast('/', "")
    val script = "render(${Json.quote(markdown)}, ${Json.quote(dir)}, $dark)"
    UIKitView(
        factory = {
            appWebView(handler, "preview.html").also { state.webView = it }
        },
        modifier = modifier,
        update = { wv ->
            wv.backgroundColor = uiColor(background)
            wv.scrollView.backgroundColor = uiColor(background)
            state.render(script)
        },
        onRelease = { wv ->
            wv.releaseHandlers()
            state.webView = null
        },
    )
}

/** The graph page's messages, for [GraphWebView]. */
private class IosGraph(val page: GraphPage) : WebHost {
    override val repoDir: String? = null
    var onSelected: (String) -> Unit = {}
    var onLayout: (String) -> Unit = {}

    override fun message(fn: String, arg: String) {
        when (fn) {
            "ready" -> page.onReady()
            "selected" -> onSelected(arg)
            "layout" -> onLayout(arg)
        }
    }
}

@Composable
actual fun GraphWebView(
    page: GraphPage,
    background: Color,
    onSelected: (String) -> Unit,
    onLayout: (String) -> Unit,
    modifier: Modifier,
) {
    val selected = rememberUpdatedState(onSelected)
    val layout = rememberUpdatedState(onLayout)
    val host = remember(page) {
        IosGraph(page).apply {
            this.onSelected = { selected.value(it) }
            this.onLayout = { layout.value(it) }
        }
    }
    val handler = remember(host) { AppWebHandler(host) }
    DisposableEffect(page) { onDispose { page.detach() } }
    UIKitView(
        factory = {
            appWebView(handler, "graph.html").also { wv ->
                wv.backgroundColor = uiColor(background)
                page.attach { script -> wv.evaluateJavaScript(script, completionHandler = null) }
            }
        },
        modifier = modifier,
        onRelease = { wv -> wv.releaseHandlers() },
    )
}

/** On iOS the web view is a native view drawn over Compose, so controls go beside it. */
actual val clickableOverWebViews: Boolean = false

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme() else lightColorScheme()

actual val shortcutModifier: String? = null

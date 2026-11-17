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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.daranotes.core.Json
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import okio.Path
import java.awt.Desktop
import java.io.File
import java.net.URI

// Desktop web views: JavaFX's WebView inside the Compose window. JavaFX calls into Kotlin
// on its own thread; anything that changes the UI is handed to the Compose thread (Fx.ui).

private const val PREVIEW_PAGE = "${WebAssets.ORIGIN}/assets/preview.html"
private const val GRAPH_PAGE = "${WebAssets.ORIGIN}/assets/graph.html"

/** Creates a JFXPanel showing [page]; [onLoaded] runs on the JavaFX thread once it has loaded. */
private fun webPanel(page: String, background: Color, onCreated: (WebEngine) -> Unit, onLoaded: (WebEngine) -> Unit): JFXPanel {
    Fx.start()
    val panel = WebPanel()
    Fx.run {
        val view = WebView()
        view.isContextMenuEnabled = false
        view.pageFill = fxColor(background)
        val engine = view.engine
        engine.loadWorker.stateProperty().addListener { _, _, state ->
            if (state == Worker.State.SUCCEEDED) onLoaded(engine)
        }
        panel.scene = Scene(view, fxColor(background))
        onCreated(engine)
        engine.load(page)
    }
    return panel
}

/**
 * A web view that steps aside while a menu or dialog is open. Compose draws those on the
 * window's own canvas, which sits under the web views (they're Swing components on top of
 * it), so where a menu overlapped the preview or graph, the web view got the pointer: the
 * menu couldn't be clicked or highlighted. Swing asks each component whether it contains
 * the pointer; while a menu or dialog is open, this one says no, so the pointer goes to the
 * canvas and the menu, and a click on the web view outside it closes the menu, as elsewhere.
 */
private class WebPanel : JFXPanel() {
    override fun contains(x: Int, y: Int): Boolean = !ComposeLayers.anyOpen(this) && super.contains(x, y)
}

/**
 * Whether Compose has a menu, dialog or tooltip open in a component's window. Reads, by
 * reflection, the list Compose keeps of them (1.12: ComposeWindowPanel._composeContainer
 * .mediator.scene.layers). If that changes in a later Compose, this says no, and menus over
 * the web views go back to not taking clicks.
 */
private object ComposeLayers {
    private var failed = false

    fun anyOpen(component: java.awt.Component): Boolean {
        if (failed) return false
        return runCatching {
            var c: java.awt.Container? = component.parent
            while (c != null && c.javaClass.simpleName != "ComposeWindowPanel") c = c.parent
            c ?: return false
            val container = c.get("_composeContainer") ?: return false
            val mediator = container.get("mediator") ?: return false
            val scene = (mediator.get("scene\$delegate") as Lazy<*>).value ?: return false
            (scene.get("layers") as List<*>).isNotEmpty()
        }.getOrElse { failed = true; false }
    }

    private fun Any.get(name: String): Any? {
        var k: Class<*>? = javaClass
        while (k != null) {
            val f = runCatching { k.getDeclaredField(name) }.getOrNull()
            if (f != null) return f.apply { isAccessible = true }.get(this)
            k = k.superclass
        }
        error("No field $name in $javaClass")
    }
}

private fun fxColor(c: Color): javafx.scene.paint.Color {
    val argb = c.toArgb()
    return javafx.scene.paint.Color.rgb((argb shr 16) and 255, (argb shr 8) and 255, argb and 255, c.alpha.toDouble())
}

/** What preview.js calls (`window.Android`). Public, so JavaScript can see its methods. */
class PreviewBridge internal constructor(private val state: DesktopPreview) {
    fun resolve(target: String): String = state.callbacks.resolve(target) ?: ""
    fun readNote(path: String): String = state.callbacks.readNote(path)
    fun openWikilink(target: String) = Fx.ui { state.callbacks.openWikilink(target) }
    fun openLink(url: String) = Fx.ui { state.openLink(url) }
    fun toggleTask(line: Any?) {
        val n = (line as? Number)?.toInt() ?: line?.toString()?.toDoubleOrNull()?.toInt() ?: return
        Fx.ui { state.callbacks.toggleTask(n) }
    }
    fun ready() {}
}

internal class DesktopPreview(var callbacks: PreviewCallbacks) {
    // JavaFX only holds objects given to JavaScript weakly, so keep the bridge here.
    private val bridge = PreviewBridge(this)
    private var engine: WebEngine? = null
    private var ready = false
    private var pending: String? = null
    private var lastRendered: String? = null
    var repoDir: File? = null

    fun panel(background: Color) = webPanel(
        PREVIEW_PAGE, background,
        onCreated = {
            // A new web view (the panel can be rebuilt) has yet to load the page.
            engine = it
            ready = false
        },
        onLoaded = { e ->
            if (e !== engine) return@webPanel // about:blank, loaded on dispose
            (e.executeScript("window") as JSObject).setMember("Android", bridge)
            ready = true
            lastRendered = null
            pending?.let(::renderNow)
        },
    )

    fun render(script: String) = Fx.run {
        pending = script
        renderNow(script)
    }

    private fun renderNow(script: String) {
        val e = engine ?: return
        if (!ready || script == lastRendered) return
        // Only once the page's render() exists; otherwise it stays pending for onLoaded.
        val done = e.executeScript("typeof render === 'function' && ($script, true)") == true
        if (done) lastRendered = script
    }

    fun dispose() = Fx.run { engine?.load("about:blank"); engine = null; ready = false }

    fun openLink(url: String) {
        val repoPrefix = "${WebAssets.ORIGIN}/repo/"
        if (url.startsWith(repoPrefix)) {
            val root = repoDir ?: return
            val rel = java.net.URLDecoder.decode(url.removePrefix(repoPrefix).substringBefore('#'), "UTF-8").trimEnd('/')
            val target = when {
                rel.endsWith(".md", ignoreCase = true) -> rel
                File(root, "$rel.md").isFile -> "$rel.md"
                else -> null
            } ?: return
            if (File(root, target).isFile) callbacks.openPath(target) else callbacks.openWikilink(target.removeSuffix(".md"))
            return
        }
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme in setOf("http", "https", "mailto") && Desktop.isDesktopSupported()) {
            runCatching { Desktop.getDesktop().browse(URI(url)) }
        }
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
    val state = remember { DesktopPreview(callbacks) }
    state.callbacks = callbacks
    state.repoDir = repoRoot.toFile()
    WebAssets.repoRoot = repoRoot.toFile()
    val dir = notePath.substringBeforeLast('/', "")
    val script = "render(${Json.quote(markdown)}, ${Json.quote(dir)}, $dark)"
    DisposableEffect(state) { onDispose { state.dispose() } }
    SwingPanel(
        modifier = modifier,
        background = background,
        factory = { state.panel(background) },
        update = { state.render(script) },
    )
}

/** What graph.html calls (`window.Android`). Public, so JavaScript can see its methods. */
class GraphBridge internal constructor(
    private val selected: () -> (String) -> Unit,
    private val layout: () -> (String) -> Unit,
) {
    fun selected(json: String) = Fx.ui { selected()(json) }
    fun layout(json: String) = Fx.ui { layout()(json) }
    fun ready() {}
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
    val bridge = remember { GraphBridge({ selected.value }, { layout.value }) }
    val holder = remember { arrayOfNulls<WebEngine>(1) }
    DisposableEffect(page) {
        onDispose {
            page.detach()
            Fx.run { holder[0]?.load("about:blank"); holder[0] = null }
        }
    }
    SwingPanel(
        modifier = modifier,
        background = background,
        factory = {
            webPanel(
                GRAPH_PAGE, background,
                onCreated = { e ->
                    holder[0] = e
                    Fx.ui { page.attach { script -> Fx.run { holder[0]?.executeScript(script) } } }
                },
                onLoaded = { e ->
                    (e.executeScript("window") as JSObject).setMember("Android", bridge)
                    Fx.ui { page.onReady() }
                },
            )
        },
    )
}

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme() else lightColorScheme()

actual val clickableOverWebViews: Boolean = false

actual val shortcutModifier: String? =
    if (System.getProperty("os.name").orEmpty().startsWith("Mac")) "⌘" else "Ctrl+"

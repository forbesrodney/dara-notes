package app.foamforandroid.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File
import okio.Path

private const val HOST = "appassets.androidplatform.net"
private const val PAGE = "https://$HOST/assets/preview.html"

private class PreviewState(val callbacks: PreviewCallbacks) {
    var webView: WebView? = null
    var ready = false
    var pending: String? = null
    var lastRendered: String? = null

    fun render(script: String) {
        pending = script
        val wv = webView ?: return
        if (!ready || script == lastRendered) return
        lastRendered = script
        wv.evaluateJavascript(script, null)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MarkdownPreview(
    markdown: String,
    notePath: String,
    repoRoot: Path,
    callbacks: PreviewCallbacks,
    modifier: Modifier,
) {
    val repoDir = repoRoot.toFile()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val state = remember { PreviewState(callbacks) }
    val dir = notePath.substringBeforeLast('/', "")
    val script = "render(${JSONObject.quote(markdown)}, ${JSONObject.quote(dir)}, $dark)"

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val loader = WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .addPathHandler("/repo/", WebViewAssetLoader.InternalStoragePathHandler(ctx, repoDir))
                .build()
            val main = Handler(Looper.getMainLooper())
            // Debug builds: inspect the preview from chrome://inspect on a computer.
            if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
            WebView(ctx).apply {
                setBackgroundColor(if (dark) 0xFF121417.toInt() else 0xFFFFFFFF.toInt())
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (url == PAGE) return false
                        handleLink(ctx, url, repoDir, state.callbacks)
                        return true
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface fun ready() {
                        main.post {
                            state.ready = true
                            state.lastRendered = null
                            state.pending?.let(state::render)
                        }
                    }
                    @JavascriptInterface fun resolve(target: String): String = state.callbacks.resolve(target) ?: ""
                    @JavascriptInterface fun readNote(path: String): String = state.callbacks.readNote(path)
                    @JavascriptInterface fun openWikilink(target: String) {
                        main.post { state.callbacks.openWikilink(target) }
                    }
                    @JavascriptInterface fun openLink(url: String) {
                        main.post { handleLink(ctx, url, repoDir, state.callbacks) }
                    }
                    @JavascriptInterface fun toggleTask(line: Int) {
                        main.post { state.callbacks.toggleTask(line) }
                    }
                }, "Android")
                loadUrl(PAGE)
                state.webView = this
                state.ready = false // a new web view: wait for the page's ready() call
            }
        },
        update = { wv ->
            wv.setBackgroundColor(if (dark) 0xFF121417.toInt() else 0xFFFFFFFF.toInt())
            state.render(script)
        },
        onRelease = { wv ->
            state.webView = null
            wv.destroy()
        },
    )
}

private fun handleLink(context: Context, url: String, repoRoot: File, callbacks: PreviewCallbacks) {
    val uri = Uri.parse(url)
    if (uri.host == HOST && uri.path?.startsWith("/repo/") == true) {
        val rel = uri.path!!.removePrefix("/repo/").trimEnd('/')
        val target = when {
            rel.endsWith(".md", ignoreCase = true) -> rel
            File(repoRoot, "$rel.md").isFile -> "$rel.md"
            else -> null
        }
        if (target != null) {
            if (File(repoRoot, target).isFile) callbacks.openPath(target)
            else callbacks.openWikilink(target.removeSuffix(".md"))
        }
        return
    }
    if (uri.scheme in setOf("http", "https", "mailto", "tel", "geo")) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

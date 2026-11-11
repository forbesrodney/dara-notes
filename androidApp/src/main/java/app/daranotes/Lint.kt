package app.daranotes

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import app.daranotes.core.LintConfig
import app.daranotes.core.LintResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs the bundled markdownlint (assets/vendor/markdownlint.min.js) in a background WebView.
 * One instance for the whole app; calls are serialized on the main thread.
 */
object Linter {
    private const val PAGE = "https://appassets.androidplatform.net/assets/lint.html"
    private var webView: WebView? = null
    private var ready = CompletableDeferred<Unit>()

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensure(context: Context): WebView {
        webView?.let { return it }
        val app = context.applicationContext
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(app))
            .build()
        return WebView(app).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView, url: String) {
                    ready.complete(Unit)
                }
            }
            loadUrl(PAGE)
            webView = this
        }
    }

    private suspend fun eval(context: Context, script: String): String = withContext(Dispatchers.Main) {
        val wv = ensure(context)
        ready.await()
        suspendCancellableCoroutine { cont ->
            wv.evaluateJavascript(script) { json ->
                // evaluateJavascript hands back the JS value JSON-encoded; ours are strings.
                val value = runCatching { JSONTokener(json).nextValue() as? String }.getOrNull()
                if (value != null) cont.resume(value)
                else cont.resumeWithException(IllegalStateException("markdownlint failed: $json"))
            }
        }
    }

    suspend fun lint(context: Context, text: String, config: LintConfig): LintResult =
        LintResult.parse(eval(context, "lintNote(${JSONObject.quote(text)}, ${config.toJson()})"), config)

    suspend fun fixAll(context: Context, text: String, config: LintConfig): String =
        eval(context, "fixNote(${JSONObject.quote(text)}, ${config.toJson()})")
}

package app.foamforandroid

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LintIssue(
    /** 1-based line number. */
    val line: Int,
    /** e.g. "MD009/no-trailing-spaces". */
    val rule: String,
    val description: String,
    val detail: String,
    val context: String,
    /** 1-based column of the problem within the line, or 0 for the whole line. */
    val column: Int,
    val length: Int,
    val fixable: Boolean,
)

data class LintResult(val issues: List<LintIssue>, val configFile: String?, val configError: String?)

/** markdownlint config found in the repository, in the order the VS Code extension uses. */
data class LintConfig(val file: String?, val raw: String, val format: String, val kind: String) {
    fun toJson(): String = JSONObject()
        .put("raw", raw).put("format", format).put("kind", kind).toString()

    companion object {
        private val candidates = listOf(
            Triple(".markdownlint-cli2.jsonc", "json", "cli2"),
            Triple(".markdownlint-cli2.yaml", "yaml", "cli2"),
            Triple(".markdownlint.jsonc", "json", "markdownlint"),
            Triple(".markdownlint.json", "json", "markdownlint"),
            Triple(".markdownlint.yaml", "yaml", "markdownlint"),
            Triple(".markdownlint.yml", "yaml", "markdownlint"),
        )

        fun find(root: File): LintConfig {
            for ((name, format, kind) in candidates) {
                val f = File(root, name)
                if (f.isFile) return LintConfig(name, f.readText(), format, kind)
            }
            val vscode = File(root, ".vscode/settings.json")
            if (vscode.isFile && vscode.readText().contains("\"markdownlint.config\"")) {
                return LintConfig(".vscode/settings.json", vscode.readText(), "json", "vscode")
            }
            return LintConfig(null, "", "json", "markdownlint")
        }
    }
}

/**
 * Runs the bundled markdownlint (assets/vendor/markdownlint.min.js) in a background WebView.
 * One instance for the whole app; calls are serialised on the main thread.
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

    suspend fun lint(context: Context, text: String, config: LintConfig): LintResult {
        val out = eval(context, "lintNote(${JSONObject.quote(text)}, ${config.toJson()})")
        val obj = JSONObject(out)
        val arr: JSONArray = obj.getJSONArray("issues")
        val issues = (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            LintIssue(
                line = e.getInt("line"),
                rule = e.getString("rule"),
                description = e.getString("description"),
                detail = e.optString("detail"),
                context = e.optString("context"),
                column = e.optInt("column"),
                length = e.optInt("length"),
                fixable = e.optBoolean("fixable"),
            )
        }
        return LintResult(issues.sortedWith(compareBy({ it.line }, { it.column })), config.file, obj.optString("configError").takeIf { it.isNotEmpty() && it != "null" })
    }

    suspend fun fixAll(context: Context, text: String, config: LintConfig): String =
        eval(context, "fixNote(${JSONObject.quote(text)}, ${config.toJson()})")
}

package app.foamforandroid.ui

import app.foamforandroid.core.Json
import app.foamforandroid.core.LintConfig
import app.foamforandroid.core.LintResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.WebKit.WKWebView
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** markdownlint on iOS: lint.html in a hidden WKWebView. */
object IosLinter {
    private var webView: WKWebView? = null
    private val ready = CompletableDeferred<Unit>()

    private val host = object : WebHost {
        override val repoDir: String? = null
        override fun message(fn: String, arg: String) {}
    }
    private val handler by lazy { FoamWebHandler(host) }

    private suspend fun eval(script: String): String = withContext(Dispatchers.Main) {
        val wv = webView ?: foamWebView(handler, "lint.html").also { v ->
            webView = v
            // lint.html has no ready() call; poll until its functions exist.
            v.waitForFunction("lintNote")
        }
        ready.await()
        suspendCancellableCoroutine { cont ->
            wv.evaluateJavaScript(script) { result, error ->
                val text = result as? String
                if (text != null) cont.resume(text)
                else cont.resumeWithException(IllegalStateException("markdownlint failed: ${error?.localizedDescription}"))
            }
        }
    }

    private fun WKWebView.waitForFunction(name: String, triesLeft: Int = 100) {
        evaluateJavaScript("typeof $name === 'function'") { result, _ ->
            if (result == true || (result as? Number)?.toInt() == 1) ready.complete(Unit)
            else if (triesLeft > 0) {
                platform.darwin.dispatch_after(
                    platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, 100_000_000L),
                    platform.darwin.dispatch_get_main_queue(),
                ) { waitForFunction(name, triesLeft - 1) }
            } else ready.completeExceptionally(IllegalStateException("Couldn't load markdownlint"))
        }
    }

    suspend fun lint(text: String, config: LintConfig): LintResult =
        LintResult.parse(eval("lintNote(${Json.quote(text)}, ${config.toJson()})"), config)

    suspend fun fixAll(text: String, config: LintConfig): String =
        eval("fixNote(${Json.quote(text)}, ${config.toJson()})")
}

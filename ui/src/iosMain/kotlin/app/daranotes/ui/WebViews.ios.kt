package app.daranotes.ui

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorFileDoesNotExist
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIApplication
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKURLSchemeHandlerProtocol
import platform.WebKit.WKURLSchemeTaskProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * The web pages on iOS, in WKWebView. They are served under a private `dara:` scheme, as on
 * the desktop: `dara://app/assets/...` from the app bundle's web folder, `dara://app/repo/...`
 * from the notes repository. The pages call `window.Android` as on Android; [BRIDGE_JS] defines
 * it, answering the calls that need a reply (resolve, readNote) with a synchronous request to
 * `dara://app/bridge/...`, and posting the rest as messages.
 */
internal const val ORIGIN = "dara://app"

private val BRIDGE_JS = """
(function () {
  function get(fn, arg) {
    try {
      var x = new XMLHttpRequest();
      x.open('GET', 'dara://app/bridge/' + fn + '?arg=' + encodeURIComponent(arg), false);
      x.send();
      return x.status === 200 ? x.responseText : '';
    } catch (e) { return ''; }
  }
  function post(fn, arg) {
    window.webkit.messageHandlers.dara.postMessage({ fn: fn, arg: arg === undefined ? '' : String(arg) });
  }
  window.Android = {
    resolve: function (t) { return get('resolve', t); },
    readNote: function (p) { return get('readNote', p); },
    openWikilink: function (t) { post('openWikilink', t); },
    openLink: function (u) { post('openLink', u); },
    toggleTask: function (n) { post('toggleTask', n); },
    selected: function (j) { post('selected', j); },
    layout: function (j) { post('layout', j); },
    ready: function () { post('ready'); }
  };
})();
""".trimIndent()

private val types = mapOf(
    "html" to "text/html", "js" to "text/javascript", "css" to "text/css", "json" to "application/json",
    "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
    "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp", "avif" to "image/avif",
    "woff2" to "font/woff2", "woff" to "font/woff", "ttf" to "font/ttf",
)

/** Answers the page's calls: data for `/bridge/` requests, and its posted messages. */
internal interface WebHost {
    /** The notes repository folder, for `/repo/` URLs. */
    val repoDir: String?

    /** A synchronous call from the page (resolve, readNote); returns the reply. */
    fun call(fn: String, arg: String): String = ""

    /** A message posted by the page (openLink, toggleTask, ready, ...). */
    fun message(fn: String, arg: String)
}

/** Serves the dara: scheme and receives the page's messages for one web view. */
@OptIn(BetaInteropApi::class)
internal class AppWebHandler(var host: WebHost) :
    NSObject(), WKURLSchemeHandlerProtocol, WKScriptMessageHandlerProtocol, WKNavigationDelegateProtocol {

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, startURLSchemeTask: WKURLSchemeTaskProtocol) {
        val url = startURLSchemeTask.request.URL ?: return
        val served = serve(url)
        if (served == null) {
            startURLSchemeTask.didFailWithError(NSError.errorWithDomain(NSURLErrorDomain, NSURLErrorFileDoesNotExist, null))
            return
        }
        val (data, type) = served
        val response = NSHTTPURLResponse(
            uRL = url, statusCode = 200, HTTPVersion = "HTTP/1.1",
            headerFields = mapOf<Any?, Any?>("Content-Type" to type, "Cache-Control" to "no-store"),
        )
        startURLSchemeTask.didReceiveResponse(response)
        startURLSchemeTask.didReceiveData(data)
        startURLSchemeTask.didFinish()
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, stopURLSchemeTask: WKURLSchemeTaskProtocol) {}

    private fun serve(url: NSURL): Pair<NSData, String>? {
        val path = url.path ?: return null
        fun text(s: String) = NSString.create(string = s).dataUsingEncoding(NSUTF8StringEncoding)
        return when {
            path.startsWith("/bridge/") -> {
                val arg = (NSURLComponents(uRL = url, resolvingAgainstBaseURL = false).queryItems
                    ?.filterIsInstance<NSURLQueryItem>()?.firstOrNull { it.name == "arg" }?.value) ?: ""
                text(host.call(path.removePrefix("/bridge/"), arg))?.let { it to "text/plain; charset=utf-8" }
            }
            path.startsWith("/assets/") -> {
                val rel = path.removePrefix("/assets/")
                if (".." in rel.split('/')) return null
                val file = (NSBundle.mainBundle.resourcePath ?: return null) + "/web/" + rel
                NSData.dataWithContentsOfFile(file)?.let { it to typeOf(rel) }
            }
            path.startsWith("/repo/") -> {
                val root = host.repoDir ?: return null
                val rel = path.removePrefix("/repo/")
                if (".." in rel.split('/')) return null
                NSData.dataWithContentsOfFile("$root/$rel")?.let { it to typeOf(rel) }
            }
            else -> null
        }
    }

    private fun typeOf(path: String) = types[path.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
        host.message(body["fn"] as? String ?: return, body["arg"] as? String ?: "")
    }

    /** Keeps the page in place: links are handled by the page's bridge calls instead. */
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL
        val scheme = url?.scheme
        if (scheme == "dara" || scheme == "about") {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            url?.absoluteString?.let { host.message("openLink", it) }
        }
    }
}

/** A WKWebView set up for the dara: pages, showing [page] (e.g. "preview.html"). */
internal fun appWebView(handler: AppWebHandler, page: String): WKWebView {
    val config = WKWebViewConfiguration()
    config.setURLSchemeHandler(handler, forURLScheme = "dara")
    config.userContentController.addScriptMessageHandler(handler, name = "dara")
    config.userContentController.addUserScript(
        WKUserScript(
            source = BRIDGE_JS,
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
            forMainFrameOnly = true,
        ),
    )
    val view = WKWebView(frame = CGRectZero.readValue(), configuration = config)
    view.navigationDelegate = handler
    view.setOpaque(false)
    view.loadRequest(NSURLRequest.requestWithURL(NSURL.URLWithString("$ORIGIN/assets/$page")!!))
    return view
}

/** Releases what [appWebView] set up (the message handler is otherwise kept alive). */
internal fun WKWebView.releaseHandlers() {
    configuration.userContentController.removeScriptMessageHandlerForName("dara")
    navigationDelegate = null
    stopLoading()
}

/** Opens a web or mail link outside the app. */
internal fun openExternally(url: String) {
    val scheme = url.substringBefore(':', "").lowercase()
    if (scheme !in setOf("http", "https", "mailto")) return
    val u = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(u, options = emptyMap<Any?, Any?>(), completionHandler = null)
}

/** True if [path] (relative to [root]) is a file. */
internal fun isFile(root: String, path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath("$root/$path")

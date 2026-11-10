package app.foamforandroid.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

@SuppressLint("SetJavaScriptEnabled")
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
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .build()
            val main = Handler(Looper.getMainLooper())
            WebView(ctx).apply {
                setBackgroundColor(background.toArgb())
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(r.url)
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true
                }
                addJavascriptInterface(object {
                    @JavascriptInterface fun ready() { main.post { page.onReady() } }
                    @JavascriptInterface fun selected(json: String) { main.post { selected.value(json) } }
                    @JavascriptInterface fun layout(json: String) { main.post { layout.value(json) } }
                }, "Android")
                page.attach { script -> evaluateJavascript(script, null) }
                loadUrl("https://appassets.androidplatform.net/assets/graph.html")
            }
        },
        onRelease = { wv -> page.detach(); wv.destroy() },
    )
}

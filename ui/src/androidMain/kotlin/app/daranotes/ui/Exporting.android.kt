package app.daranotes.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.print.PdfFileWriter
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import app.daranotes.core.Export
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val HOST = "appassets.androidplatform.net"
private const val PAGE = "https://$HOST/assets/preview.html"

@Composable
actual fun rememberExportActions(): List<ExportAction> {
    val context = LocalContext.current
    return remember(context) {
        listOf(
            ExportAction("Share Markdown file") { note ->
                val file = exportFile(context, Export.fileName(note.path, "md"))
                withContext(Dispatchers.IO) { file.writeText(note.markdown) }
                shareFile(context, file, "text/markdown")
                null
            },
            ExportAction("Share as PDF") { note -> sharePdf(context, note) },
            ExportAction("Share as text") { note ->
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, note.path.substringAfterLast('/').removeSuffix(".md"))
                    .putExtra(Intent.EXTRA_TEXT, Export.plainText(note.markdown))
                startChooser(context, intent)
                null
            },
        )
    }
}

/** A file in the cache's exports folder (shared through the FileProvider), emptied first. */
private fun exportFile(context: Context, name: String): File {
    val dir = File(context.cacheDir, "exports")
    dir.deleteRecursively()
    dir.mkdirs()
    return File(dir, name)
}

private fun shareFile(context: Context, file: File, type: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType(type)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // Lets the share sheet read the file too, for its preview.
    intent.clipData = ClipData.newRawUri(file.name, uri)
    startChooser(context, intent)
}

private fun startChooser(context: Context, intent: Intent) {
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: ActivityNotFoundException) {
    }
}

/** US-style countries print on Letter, the rest on A4. */
private fun paper(): PrintAttributes.MediaSize =
    if (Locale.getDefault().country in setOf("US", "CA", "MX", "PH", "CL", "CO", "VE")) PrintAttributes.MediaSize.NA_LETTER
    else PrintAttributes.MediaSize.ISO_A4

/**
 * Draws the note in an off-screen web view, prints it to a PDF file and shares that. If the
 * system won't let the app write the PDF itself, opens the print screen, which can save one.
 */
@SuppressLint("SetJavaScriptEnabled")
private suspend fun sharePdf(context: Context, note: ExportNote): String? {
    val name = Export.fileName(note.path, "pdf")
    val file = exportFile(context, name)
    val main = Handler(Looper.getMainLooper())
    val webView = suspendCancellableCoroutine<WebView> { cont ->
        val loader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/repo/", WebViewAssetLoader.InternalStoragePathHandler(context, note.repoRoot.toFile()))
            .build()
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.url.toString() != PAGE
        }
        wv.addJavascriptInterface(object {
            @JavascriptInterface fun ready() {
                main.post { wv.evaluateJavascript(exportScript(note, JSONObject::quote), null) }
            }
            @JavascriptInterface fun exported() {
                main.post { if (cont.isActive) cont.resume(wv) }
            }
            @JavascriptInterface fun resolve(target: String): String = note.callbacks.resolve(target) ?: ""
            @JavascriptInterface fun readNote(path: String): String = note.callbacks.readNote(path)
        }, "Android")
        cont.invokeOnCancellation { main.post { wv.destroy() } }
        wv.loadUrl(PAGE)
    }
    val adapter = webView.createPrintDocumentAdapter(name.removeSuffix(".pdf"))
    val attributes = PrintAttributes.Builder()
        .setMediaSize(paper())
        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()
    val written = try {
        suspendCancellableCoroutine<Unit> { cont ->
            PdfFileWriter(attributes).write(adapter, file) { error ->
                if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
            }
        }
        true
    } catch (_: Throwable) {
        false
    }
    if (written && file.length() > 0) {
        webView.destroy()
        shareFile(context, file, "application/pdf")
        return null
    }
    // The print screen keeps using the web view's adapter; the web view goes with the app's memory.
    val printing = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return "Couldn't make the PDF"
    printing.print(name.removeSuffix(".pdf"), adapter, attributes)
    return null
}

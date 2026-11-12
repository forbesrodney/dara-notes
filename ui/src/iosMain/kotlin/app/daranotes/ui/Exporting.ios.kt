package app.daranotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import app.daranotes.core.Export
import app.daranotes.core.Json
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectInset
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSValue
import platform.Foundation.countryCode
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.setValue
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPage
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetPDFContextBounds
import platform.UIKit.UIPrintPageRenderer
import platform.UIKit.UIViewController
import platform.UIKit.valueWithCGRect
import platform.UIKit.popoverPresentationController
import platform.UIKit.viewPrintFormatter
import platform.WebKit.WKWebView
import kotlin.coroutines.resume

@Composable
actual fun rememberExportActions(): List<ExportAction> {
    val controller = LocalUIViewController.current
    return remember(controller) {
        listOf(
            ExportAction("Share Markdown file") { note ->
                val path = exportPath(Export.fileName(note.path, "md"))
                NSString.create(string = note.markdown).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
                share(controller, listOf(NSURL.fileURLWithPath(path)), subject(note))
                null
            },
            ExportAction("Share as PDF") { note ->
                val path = exportPath(Export.fileName(note.path, "pdf"))
                if (!makePdf(controller, note, path)) return@ExportAction "Couldn't make the PDF"
                share(controller, listOf(NSURL.fileURLWithPath(path)), subject(note))
                null
            },
            ExportAction("Share as text") { note ->
                share(controller, listOf(Export.plainText(note.markdown)), subject(note))
                null
            },
        )
    }
}

private fun subject(note: ExportNote) = note.path.substringAfterLast('/').removeSuffix(".md")

/** A path in the temporary exports folder, emptied first. */
private fun exportPath(name: String): String {
    val dir = NSTemporaryDirectory().trimEnd('/') + "/exports"
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(dir, error = null)
    fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return "$dir/$name"
}

/** The share sheet, from the app's view controller (as a popover on an iPad). */
@OptIn(ExperimentalForeignApi::class)
private fun share(controller: UIViewController, items: List<Any>, subject: String) {
    var top = controller
    while (true) top = top.presentedViewController ?: break
    val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
    sheet.setValue(subject, forKey = "subject") // the email's subject
    sheet.popoverPresentationController?.let { pop ->
        pop.sourceView = top.view
        top.view.bounds.useContents { pop.sourceRect = CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0) }
    }
    top.presentViewController(sheet, animated = true, completion = null)
}

/** The note's page, drawn off-screen; calls back once the page is drawn. */
private class ExportHost(val note: ExportNote, val onExported: () -> Unit) : WebHost {
    override val repoDir: String = note.repoRoot.toString()
    var webView: WKWebView? = null

    override fun call(fn: String, arg: String): String = when (fn) {
        "resolve" -> note.callbacks.resolve(arg) ?: ""
        "readNote" -> note.callbacks.readNote(arg)
        else -> ""
    }

    override fun message(fn: String, arg: String) {
        when (fn) {
            "ready" -> webView?.evaluateJavaScript(exportScript(note, Json::quote), completionHandler = null)
            "exported" -> onExported()
        }
    }
}

/**
 * Draws the note in an off-screen web view and writes it to a PDF at [path], in pages
 * (Letter in the US and nearby, A4 elsewhere) with margins. Returns whether it worked.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun makePdf(controller: UIViewController, note: ExportNote, path: String): Boolean {
    val letter = NSLocale.currentLocale.countryCode in setOf("US", "CA", "MX", "PH", "CL", "CO", "VE")
    val width = if (letter) 612.0 else 595.0
    val height = if (letter) 792.0 else 842.0
    var host: ExportHost? = null
    val webView = suspendCancellableCoroutine<WKWebView> { cont ->
        lateinit var wv: WKWebView
        val h = ExportHost(note) { if (cont.isActive) cont.resume(wv) }
        host = h
        wv = appWebView(AppWebHandler(h), "preview.html")
        h.webView = wv
        // Laid out at the printed width, behind the app's view (web views draw only in a window).
        wv.setFrame(CGRectMake(0.0, 0.0, width - 80.0, height))
        wv.setAlpha(0.0)
        controller.view.insertSubview(wv, atIndex = 0)
        cont.invokeOnCancellation { wv.releaseHandlers(); wv.removeFromSuperview() }
    }
    try {
        val renderer = UIPrintPageRenderer()
        renderer.addPrintFormatter(webView.viewPrintFormatter(), startingAtPageAtIndex = 0)
        val paper = CGRectMake(0.0, 0.0, width, height)
        renderer.setValue(NSValue.valueWithCGRect(paper), forKey = "paperRect")
        renderer.setValue(NSValue.valueWithCGRect(CGRectInset(paper, 40.0, 46.0)), forKey = "printableRect")
        val data = NSMutableData()
        UIGraphicsBeginPDFContextToData(data, paper, null)
        val pages = renderer.numberOfPages()
        for (i in 0 until pages) {
            UIGraphicsBeginPDFPage()
            renderer.drawPageAtIndex(i, inRect = UIGraphicsGetPDFContextBounds())
        }
        UIGraphicsEndPDFContext()
        return pages > 0 && data.writeToFile(path, atomically = true)
    } finally {
        webView.releaseHandlers()
        webView.removeFromSuperview()
        host = null
    }
}

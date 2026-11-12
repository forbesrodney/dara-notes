package app.daranotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.daranotes.core.Export
import app.daranotes.core.Json
import javafx.concurrent.Worker
import javafx.print.PrinterJob
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import netscape.javascript.JSObject
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.URLEncoder
import kotlin.coroutines.resume

@Composable
actual fun rememberExportActions(): List<ExportAction> = remember {
    buildList {
        add(ExportAction("Save Markdown file…") { note ->
            val file = chooseSaveFile(Export.fileName(note.path, "md")) ?: return@ExportAction null
            withContext(Dispatchers.IO) { file.writeText(note.markdown) }
            "Saved ${file.name}"
        })
        add(ExportAction("Print or save as PDF…") { note -> printNote(note) })
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            add(ExportAction("Email as text") { note ->
                val subject = note.path.substringAfterLast('/').removeSuffix(".md")
                Desktop.getDesktop().mail(URI("mailto:?subject=${encode(subject)}&body=${encode(Export.plainText(note.markdown))}"))
                null
            })
        }
        add(ExportAction("Copy as text") { note ->
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(Export.plainText(note.markdown)), null)
            "Copied"
        })
    }
}

/** Percent-encodes for a mailto: URL (spaces as %20, not +). */
private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")

/** The system's Save dialog; null if cancelled. */
private fun chooseSaveFile(name: String): File? {
    val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE)
    dialog.file = name
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return File(dialog.directory, file)
}

/** What preview.js calls while exporting (`window.Android`). Public, so JavaScript can see it. */
class ExportBridge internal constructor(private val note: ExportNote, private val done: () -> Unit) {
    fun resolve(target: String): String = note.callbacks.resolve(target) ?: ""
    fun readNote(path: String): String = note.callbacks.readNote(path)
    fun exported() = done()
    fun ready() {}
}

/**
 * Draws the note in an off-screen web view and prints it, through the system's Print dialog;
 * on a Mac its PDF menu saves the PDF or sends it in Mail.
 */
private suspend fun printNote(note: ExportNote): String? {
    WebAssets.repoRoot = note.repoRoot.toFile()
    // Kept here: JavaFX only holds objects given to JavaScript weakly.
    var bridge: ExportBridge? = null
    val engine = suspendCancellableCoroutine<WebEngine> { cont ->
        Fx.run {
            val view = WebView()
            val e = view.engine
            Scene(view, 700.0, 900.0)
            bridge = ExportBridge(note) { Fx.run { if (cont.isActive) cont.resume(e) } }
            e.loadWorker.stateProperty().addListener { _, _, state ->
                if (state == Worker.State.SUCCEEDED && e.location.endsWith("preview.html")) {
                    (e.executeScript("window") as JSObject).setMember("Android", bridge)
                    e.executeScript(exportScript(note, Json::quote))
                }
            }
            e.load("${WebAssets.ORIGIN}/assets/preview.html")
        }
    }
    val message = suspendCancellableCoroutine<String?> { cont ->
        Fx.run {
            val result = runCatching {
                val job = PrinterJob.createPrinterJob()
                    ?: return@runCatching "Printing needs a printer set up (in System Settings), even to save a PDF"
                job.jobSettings.jobName = Export.fileName(note.path, "pdf").removeSuffix(".pdf")
                if (job.showPrintDialog(null)) {
                    engine.print(job)
                    job.endJob()
                } else job.cancelJob()
                null
            }.getOrElse { "Couldn't print: ${it.message}" }
            engine.load("about:blank")
            cont.resume(result)
        }
    }
    bridge = null
    return message
}

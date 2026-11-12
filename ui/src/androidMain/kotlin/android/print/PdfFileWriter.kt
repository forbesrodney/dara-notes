package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Writes what a [PrintDocumentAdapter] prints (a web view's page) to a PDF file, without the
 * print screen. It lives in this package because the adapter's callbacks can only be made
 * here; if the system refuses, callers fall back to the print screen ("Save as PDF").
 */
class PdfFileWriter(private val attributes: PrintAttributes) {
    fun write(adapter: PrintDocumentAdapter, file: File, done: (Throwable?) -> Unit) {
        adapter.onStart()
        adapter.onLayout(null, attributes, null, object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                val out = try {
                    ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE,
                    )
                } catch (e: Exception) {
                    adapter.onFinish(); done(e); return
                }
                adapter.onWrite(arrayOf(PageRange.ALL_PAGES), out, CancellationSignal(), object : PrintDocumentAdapter.WriteResultCallback() {
                    override fun onWriteFinished(pages: Array<out PageRange>) {
                        out.close(); adapter.onFinish(); done(null)
                    }
                    override fun onWriteFailed(error: CharSequence?) {
                        out.close(); adapter.onFinish(); done(IllegalStateException(error?.toString() ?: "The PDF couldn't be written"))
                    }
                    override fun onWriteCancelled() {
                        out.close(); adapter.onFinish(); done(IllegalStateException("Cancelled"))
                    }
                })
            }
            override fun onLayoutFailed(error: CharSequence?) {
                adapter.onFinish(); done(IllegalStateException(error?.toString() ?: "The PDF couldn't be laid out"))
            }
            override fun onLayoutCancelled() {
                adapter.onFinish(); done(IllegalStateException("Cancelled"))
            }
        }, null)
    }
}

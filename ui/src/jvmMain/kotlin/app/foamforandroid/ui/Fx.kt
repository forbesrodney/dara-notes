package app.foamforandroid.ui

import javafx.application.Platform
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.swing.SwingUtilities

/** The JavaFX toolkit, which runs the desktop web views on its own thread. */
object Fx {
    @Volatile private var started = false

    /** Starts JavaFX if it isn't running. Call early: it must keep running between web views. */
    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            try {
                Platform.startup {}
            } catch (_: IllegalStateException) {
                // Already started (a JFXPanel starts it too).
            }
            // Keep JavaFX alive when the last web view closes, so the next one can open.
            Platform.setImplicitExit(false)
            started = true
        }
    }

    /** Runs [block] on the JavaFX thread. */
    fun run(block: () -> Unit) {
        start()
        if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
    }

    /** Runs [block] on the JavaFX thread and returns its result. */
    suspend fun <T> call(block: () -> T): T {
        start()
        return suspendCancellableCoroutine { cont -> Platform.runLater { cont.resumeWith(runCatching(block)) } }
    }

    /** Runs [block] on the Swing/Compose UI thread. */
    fun ui(block: () -> Unit) = SwingUtilities.invokeLater(block)
}

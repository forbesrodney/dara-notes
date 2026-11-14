package app.daranotes.ui

import javafx.application.Platform
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
            initScenegraph()
            started = true
        }
    }

    /**
     * Loads JavaFX's scene graph and web view classes on the JavaFX thread, and waits.
     * Otherwise the first web view can deadlock the app: the JavaFX thread, building the
     * WebView, starts initializing javafx.scene.Node while the Swing thread, adding the
     * JFXPanel to the window, starts initializing NodeHelper, and each class's
     * initialization waits for the other's.
     */
    private fun initScenegraph() {
        if (Platform.isFxApplicationThread()) return
        val done = CountDownLatch(1)
        Platform.runLater {
            try {
                for (name in listOf("javafx.scene.Node", "javafx.scene.Scene", "javafx.scene.web.WebView")) {
                    Class.forName(name, true, Fx::class.java.classLoader)
                }
            } catch (_: Throwable) {
                // Not fatal here: the classes then load later, as before.
            } finally {
                done.countDown()
            }
        }
        done.await(10, TimeUnit.SECONDS)
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

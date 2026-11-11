package app.daranotes.desktop

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowEvent
import javax.swing.JWindow
import javax.swing.Timer
import kotlin.math.abs

/**
 * Works around dialogs opening blank on macOS. With compose.layers.type=WINDOW, each menu
 * and dialog is a borderless window of its own (JWindow). A dialog's window is drawn but not
 * shown until it gets some input, so the dialog looked empty, with only the dimming behind
 * it. Showing the window again just after it opens makes macOS display it.
 *
 * Only dialogs need this, and re-showing a menu makes it flicker, so it's limited to windows
 * centered on the window that opened them, as Compose places dialogs (menus open beside
 * what was clicked).
 */
fun showLayerWindowsOnOpen() {
    if ("mac" !in System.getProperty("os.name").lowercase()) return
    Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
        val window = (event as WindowEvent).window
        if (event.id == WindowEvent.WINDOW_OPENED && window is JWindow && window.isCenteredOnOwner()) {
            Timer(50) {
                if (window.isDisplayable && window.isVisible) {
                    window.isVisible = false
                    window.isVisible = true
                    window.toFront()
                }
            }.apply { isRepeats = false }.start()
        }
    }, AWTEvent.WINDOW_EVENT_MASK)
}

private fun Window.isCenteredOnOwner(): Boolean {
    val parent = owner?.takeIf { it.isShowing } ?: return false
    val inside = parent.bounds.apply {
        // The content area, below the title bar: Compose centers dialogs in that.
        val top = parent.insets.top
        y += top
        height -= top
    }
    return abs(bounds.centerX - inside.centerX) <= 8 && abs(bounds.centerY - inside.centerY) <= 8
}

package app.daranotes.desktop

import java.awt.AWTEvent
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import javax.swing.JWindow
import javax.swing.SwingUtilities
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
 *
 * Also keeps a menu's hover highlight under the pointer (see [refreshHoverOnMove]).
 */
fun showLayerWindowsOnOpen() {
    if ("mac" !in System.getProperty("os.name").lowercase()) return
    Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
        val window = (event as WindowEvent).window
        if (event.id == WindowEvent.WINDOW_OPENED && window is JWindow) window.refreshHoverOnMove()
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

/**
 * A menu or dialog can open in one place and then move once its size is known. The item that
 * was under the pointer at first stayed highlighted, since nothing told the menu the pointer
 * was now over another item, or outside it. After each move, tell it where the pointer is.
 */
private fun JWindow.refreshHoverOnMove() {
    // One check, a moment after the last move: not while the window is still being set up
    // (its first frame not yet drawn), and not once per step of a move.
    val check = Timer(60) { refreshHover() }.apply { isRepeats = false }
    // A dialog's window covers the whole app window (for the dimming), and the dialog moves
    // inside it as it's laid out, so the window itself doesn't move: check once it has settled.
    for (delay in listOf(150, 350)) Timer(delay) { refreshHover() }.apply { isRepeats = false }.start()
    addComponentListener(object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) = check.restart()
        override fun componentResized(e: ComponentEvent) = check.restart()
    })
}

private fun JWindow.refreshHover() {
    if (!isDisplayable || !isShowing) return
    val screen = MouseInfo.getPointerInfo()?.location ?: return
    val inWindow = Point(screen).also { SwingUtilities.convertPointFromScreen(it, this) }
    val inside = inWindow.x in 0 until width && inWindow.y in 0 until height
    // The component that draws the menu: the one under the pointer, or (if the pointer is
    // outside) the one in the middle, to be told the pointer has left.
    val at = if (inside) inWindow else Point(width / 2, height / 2)
    val target = SwingUtilities.getDeepestComponentAt(this, at.x, at.y) ?: return
    val local = SwingUtilities.convertPoint(this, inWindow, target)
    val id = if (inside) MouseEvent.MOUSE_MOVED else MouseEvent.MOUSE_EXITED
    target.dispatchEvent(
        MouseEvent(target, id, System.currentTimeMillis(), 0, local.x, local.y, screen.x, screen.y, 0, false, MouseEvent.NOBUTTON),
    )
}

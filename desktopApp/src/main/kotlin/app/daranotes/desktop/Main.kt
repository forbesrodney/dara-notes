package app.daranotes.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.daranotes.MainViewModel
import app.daranotes.Screen
import app.daranotes.core.GitSync
import app.daranotes.ui.App
import app.daranotes.ui.Fx
import java.awt.AWTEvent
import java.awt.Desktop
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import java.awt.event.WindowAdapter
import java.awt.Taskbar
import java.awt.event.WindowEvent
import javax.imageio.ImageIO

fun main() {
    // Menus and dialogs are drawn on the window's own canvas (Compose's default), under the
    // web views, which step aside while one is open (WebPanel in PlatformViews.jvm.kt). Giving
    // each a window of its own (compose.layers.type=WINDOW) also works, but Compose moves and
    // resizes a menu's window as the menu draws, and the menu jumped.
    System.setProperty("compose.interop.blending", "true")
    // On a Mac, the menu bar goes at the top of the screen, where Help is expected.
    if (isMac) System.setProperty("apple.laf.useScreenMenuBar", "true")
    // On a Mac, Java2D's Metal renderer garbles the web views on a 1x (non-Retina) screen, such
    // as an external display: JavaFX draws the page correctly, but copying it into the window
    // clips the letters ("Dell" drawn as "Cell"). Its OpenGL renderer copies it intact. Compose
    // draws the rest of the window itself, so this only changes how the web views get on
    // screen. (-Dsun.java2d.metal=true overrides it.)
    if (isMac && System.getProperty("sun.java2d.metal") == null) System.setProperty("sun.java2d.metal", "false")
    // On a Mac, JavaFX draws the web views in software rather than on the GPU (ES2). A JFXPanel
    // copies each frame back into the Swing window anyway, so the GPU gains little. This was
    // first done for the garbling above, which it didn't fix; that was most likely the window
    // ending up on an external display. (-Dprism.order overrides it.)
    if (isMac && System.getProperty("prism.order") == null) System.setProperty("prism.order", "sw")
    GitSync.clientName = clientName()
    // Start AWT before JavaFX. On a Mac, whichever starts first owns the app's NSApplication;
    // if JavaFX does, AWT runs embedded: the app menu is empty (no Quit) and the menu bar
    // lands inside the window instead of at the top of the screen.
    java.awt.Toolkit.getDefaultToolkit()
    Fx.start()

    // The app icon, for the window and (when run from Gradle) the Dock.
    val icon = object {}.javaClass.getResourceAsStream("/app-icon.png")?.use(ImageIO::read)
    if (icon != null && Taskbar.isTaskbarSupported() &&
        Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)
    ) {
        Taskbar.getTaskbar().iconImage = icon
    }

    val platform = DesktopPlatform()
    val vm = MainViewModel(platform)
    platform.scheduleSync()

    // Quitting (closing the window, or Cmd+Q on a Mac) first pushes edits not yet synced, so
    // they don't wait for the next launch and risk a merge conflict meanwhile. The window is
    // hidden while that runs.
    val quitting = mutableStateOf(false)
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
        Desktop.getDesktop().setQuitHandler { _, response ->
            response.cancelQuit()
            quitting.value = true
        }
    }

    application {
        if (quitting.value) {
            LaunchedEffect(Unit) {
                vm.syncBeforeLeaving(timeoutMillis = 20_000)
                exitApplication()
            }
        }
        Window(
            onCloseRequest = { quitting.value = true },
            visible = !quitting.value,
            title = appName(),
            icon = icon?.let { BitmapPainter(it.toComposeImageBitmap()) },
            state = rememberWindowState(width = 1000.dp, height = 760.dp),
            onPreviewKeyEvent = { e -> appShortcut(e, vm) },
        ) {
            // Pull changes made elsewhere whenever the window comes to the front, as the
            // Android app does when it opens.
            DisposableEffect(Unit) {
                val listener = object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent) = vm.onForeground()
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            // A mouse's back button goes Back. Watched for the whole window at the AWT level,
            // so it works over the preview and graph (Swing panels Compose doesn't see).
            DisposableEffect(Unit) {
                val toolkit = java.awt.Toolkit.getDefaultToolkit()
                val listener = AWTEventListener { e ->
                    if (e is MouseEvent && e.id == MouseEvent.MOUSE_PRESSED && e.button == backButton &&
                        SwingUtilities.getWindowAncestor(e.component).let { it == window || e.component == window }
                    ) {
                        vm.shortcutBack()
                    }
                }
                toolkit.addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
                onDispose { toolkit.removeAWTEventListener(listener) }
            }
            // Only on a Mac: elsewhere a menu bar would sit inside the window, holding just
            // Help, which the note list's menu already has.
            if (isMac) {
                MenuBar {
                    Menu("Help") {
                        Item(
                            "Dara Notes Help",
                            shortcut = KeyShortcut(Key.Slash, meta = true, shift = true),
                            onClick = { if (vm.current !is Screen.Help) vm.navigate(Screen.Help()) },
                        )
                    }
                }
            }
            App(vm)
        }
    }
}

private val isMac = "mac" in System.getProperty("os.name").lowercase()

/**
 * A mouse's back button, as Java numbers it: 4 on Macs and Windows; 6 on Linux, where X11's
 * buttons 4 and 5 are the scroll wheel and Java numbers the ones after them two lower, so
 * X11's back button (8) is 6.
 */
private val backButton = if ("linux" in System.getProperty("os.name").lowercase()) 6 else 4

/**
 * App-wide shortcuts, wherever the focus is: Cmd+N (Ctrl+N on Windows and Linux) for a new
 * note, Cmd+D (Ctrl+D) for today's daily note, and Back: Cmd+[ on a Mac, Alt+Left elsewhere,
 * as in browsers and file managers. True if [e] was one.
 */
private fun appShortcut(e: KeyEvent, vm: MainViewModel): Boolean {
    val back = if (isMac) {
        e.key == Key.LeftBracket && e.isMetaPressed && !e.isCtrlPressed && !e.isAltPressed && !e.isShiftPressed
    } else {
        e.key == Key.DirectionLeft && e.isAltPressed && !e.isCtrlPressed && !e.isMetaPressed && !e.isShiftPressed
    }
    if (back) {
        if (e.type == KeyEventType.KeyDown) vm.shortcutBack()
        return true
    }
    val command = if (isMac) e.isMetaPressed && !e.isCtrlPressed else e.isCtrlPressed && !e.isMetaPressed
    if (!command || e.isShiftPressed || e.isAltPressed) return false
    val action: () -> Unit = when (e.key) {
        Key.N -> vm::shortcutNewNote
        Key.D -> vm::shortcutToday
        else -> return false
    }
    if (e.type == KeyEventType.KeyDown) action()
    return true
}


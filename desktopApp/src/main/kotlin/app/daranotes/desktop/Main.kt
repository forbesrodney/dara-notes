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
import java.awt.Desktop
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
    // On a Mac, JavaFX's GPU pipeline (ES2) garbles the web views' text after the app has run
    // a while: letters lose pieces ("Dell" drawn as "Cell") until a restart, as its cached
    // glyphs go bad. The software pipeline doesn't, and costs little here: a JFXPanel copies
    // each frame back from the GPU into the Swing window anyway. (-Dprism.order overrides it.)
    if (isMac && System.getProperty("prism.order") == null) System.setProperty("prism.order", "sw")
    GitSync.clientName = clientName()
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
 * App-wide shortcuts, wherever the focus is: Cmd+N (Ctrl+N on Windows and Linux) for a new
 * note and Cmd+D (Ctrl+D) for today's daily note. True if [e] was one.
 */
private fun appShortcut(e: KeyEvent, vm: MainViewModel): Boolean {
    val command = if (isMac) e.isMetaPressed && !e.isCtrlPressed else e.isCtrlPressed && !e.isMetaPressed
    if (!command || e.isShiftPressed || e.isAltPressed) return false
    val action: () -> Unit = when (e.key) {
        Key.N -> vm::requestNewNote
        Key.D -> vm::openDailyNote
        else -> return false
    }
    if (e.type == KeyEventType.KeyDown) action()
    return true
}


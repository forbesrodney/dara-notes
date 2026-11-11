package app.foamforandroid.desktop

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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.foamforandroid.MainViewModel
import app.foamforandroid.core.GitSync
import app.foamforandroid.ui.App
import app.foamforandroid.ui.Fx
import java.awt.Desktop
import java.awt.event.WindowAdapter
import java.awt.Taskbar
import java.awt.event.WindowEvent
import javax.imageio.ImageIO

fun main() {
    // The web views are native (Swing/JavaFX) components. Menus and dialogs drawn inside the
    // window would show over them but lose their clicks to them, so give each its own window.
    System.setProperty("compose.layers.type", "WINDOW")
    showLayerWindowsOnOpen()
    System.setProperty("compose.interop.blending", "true")
    GitSync.clientName = clientName()
    Fx.start()
    migrateAppDataDir()

    // The app icon, for the window and (when run from Gradle) the Dock.
    val icon = object {}.javaClass.getResourceAsStream("/foam-icon.png")?.use(ImageIO::read)
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


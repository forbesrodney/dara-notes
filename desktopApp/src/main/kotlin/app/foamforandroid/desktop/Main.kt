package app.foamforandroid.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.foamforandroid.MainViewModel
import app.foamforandroid.core.GitSync
import app.foamforandroid.ui.App
import app.foamforandroid.ui.Fx
import java.awt.event.WindowAdapter
import java.awt.Taskbar
import java.awt.event.WindowEvent
import javax.imageio.ImageIO

fun main() {
    // The web views are native (Swing/JavaFX) components. Menus and dialogs drawn inside the
    // window would show over them but lose their clicks to them, so give each its own window.
    System.setProperty("compose.layers.type", "WINDOW")
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

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = appName(),
            icon = icon?.let { BitmapPainter(it.toComposeImageBitmap()) },
            state = rememberWindowState(width = 1000.dp, height = 760.dp),
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

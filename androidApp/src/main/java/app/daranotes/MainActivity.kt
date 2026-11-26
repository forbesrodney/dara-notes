package app.daranotes

import android.app.Application
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.daranotes.ui.App

class DaraNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // JGit reads ~/.gitconfig; give it a writable home inside app storage.
        System.setProperty("user.home", filesDir.absolutePath)
        SyncScheduler.schedulePeriodic(this, AndroidSettings(this))
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(AndroidPlatform(application)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    /**
     * With a hardware keyboard, Ctrl+N for a new note and Ctrl+D for today's note, from any
     * screen, as in the desktop app. Seen here before any view, so they work wherever the
     * focus is. Meta (Cmd) works too where Android doesn't claim it for itself.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val command = event.isCtrlPressed != event.isMetaPressed && !event.isShiftPressed && !event.isAltPressed
        val action: (() -> Unit)? = when {
            !command -> null
            event.keyCode == KeyEvent.KEYCODE_N -> vm::shortcutNewNote
            event.keyCode == KeyEvent.KEYCODE_D -> vm::shortcutToday
            else -> null
        }
        if (action == null) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) action()
        return true
    }

    /** Lists the shortcuts in Android's keyboard shortcuts screen (Meta+/). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data.add(
            KeyboardShortcutGroup(
                "Dara Notes",
                listOf(
                    KeyboardShortcutInfo("New note", KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("Today's note", KeyEvent.KEYCODE_D, KeyEvent.META_CTRL_ON),
                ),
            ),
        )
    }
}

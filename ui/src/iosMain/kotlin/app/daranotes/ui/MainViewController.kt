package app.daranotes.ui

import androidx.compose.ui.window.ComposeUIViewController
import app.daranotes.BackgroundSync
import app.daranotes.IosPlatform
import app.daranotes.MainViewModel
import app.daranotes.core.GitSync
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UIKit.UIViewController

private val platform: IosPlatform by lazy {
    GitSync.clientName = "iOS"
    IosPlatform()
}

private val vm: MainViewModel by lazy { MainViewModel(platform) }

/** The whole app as a UIKit view controller, for the Swift entry point (iosApp/). */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App(vm) }

/** Call at launch (before it finishes): registers background sync and schedules the first. */
@Suppress("unused")
fun startBackgroundSync() {
    BackgroundSync.register { platform }
    BackgroundSync.schedule(platform.settings)
}

/** Call when the app comes to the foreground: pulls changes made elsewhere. */
@Suppress("unused")
fun onForeground() = vm.onForeground()

/**
 * Call when the app goes to the background: pushes edits not yet synced. iOS suspends the
 * app soon after, so this asks for a little background time (about 30 seconds) to finish.
 */
@Suppress("unused")
fun onBackground() {
    val app = UIApplication.sharedApplication
    var task = UIBackgroundTaskInvalid
    task = app.beginBackgroundTaskWithName("Sync") {
        // Out of time: iOS is about to suspend the app.
        app.endBackgroundTask(task)
        task = UIBackgroundTaskInvalid
    }
    MainScope().launch {
        vm.syncBeforeLeaving(timeoutMillis = 25_000)
        if (task != UIBackgroundTaskInvalid) {
            app.endBackgroundTask(task)
            task = UIBackgroundTaskInvalid
        }
    }
}

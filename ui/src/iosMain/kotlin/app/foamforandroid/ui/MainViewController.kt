package app.foamforandroid.ui

import androidx.compose.ui.window.ComposeUIViewController
import app.foamforandroid.BackgroundSync
import app.foamforandroid.IosPlatform
import app.foamforandroid.MainViewModel
import app.foamforandroid.core.GitSync
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

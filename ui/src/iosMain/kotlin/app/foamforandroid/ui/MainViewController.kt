package app.foamforandroid.ui

import androidx.compose.ui.window.ComposeUIViewController
import app.foamforandroid.IosPlatform
import app.foamforandroid.MainViewModel
import app.foamforandroid.core.GitSync
import platform.UIKit.UIViewController

private val vm: MainViewModel by lazy {
    GitSync.clientName = "iOS"
    MainViewModel(IosPlatform())
}

/** The whole app as a UIKit view controller, for the Swift entry point (iosApp/). */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App(vm) }

/** Call when the app comes to the foreground: pulls changes made elsewhere. */
@Suppress("unused")
fun onForeground() = vm.onForeground()

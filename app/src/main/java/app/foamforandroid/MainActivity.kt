package app.foamforandroid

import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.foamforandroid.ui.EditorScreen
import app.foamforandroid.ui.GraphScreen
import app.foamforandroid.ui.NotesScreen
import app.foamforandroid.ui.OrphansScreen
import app.foamforandroid.ui.SettingsScreen

class FoamForAndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // JGit reads ~/.gitconfig; give it a writable home inside app storage.
        System.setProperty("user.home", filesDir.absolutePath)
        SyncScheduler.schedulePeriodic(this, AndroidSettings(this))
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
                BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
                when (val screen = vm.current) {
                    Screen.Notes -> NotesScreen(vm, snackbar)
                    Screen.Settings -> SettingsScreen(vm, snackbar)
                    is Screen.Graph -> GraphScreen(vm, screen, snackbar)
                    Screen.Orphans -> OrphansScreen(vm, snackbar)
                    // key(): each note gets its own editor state, so leaving note A for
                    // note B can never flush B's text into A.
                    is Screen.Editor -> key(screen.path) { EditorScreen(vm, screen, snackbar) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

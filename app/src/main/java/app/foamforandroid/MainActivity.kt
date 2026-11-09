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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.foamforandroid.ui.ConnectionsScreen
import app.foamforandroid.ui.EditorScreen
import app.foamforandroid.ui.GraphScreen
import app.foamforandroid.ui.NotesScreen
import app.foamforandroid.ui.OrphansScreen
import app.foamforandroid.ui.PlaceholdersScreen
import app.foamforandroid.ui.SettingsScreen
import app.foamforandroid.ui.TagsScreen

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
                // During some rotations (e.g. to upside down) the window can be tiny for a
                // frame. Material list rows throw if laid out narrower than their padding, so
                // never lay the app out below a usable size; the excess is just clipped.
                Box(Modifier.fillMaxSize().atLeastSize(MIN_LAYOUT, MIN_LAYOUT)) {
                when (val screen = vm.current) {
                    Screen.Notes -> NotesScreen(vm, snackbar)
                    Screen.Settings -> SettingsScreen(vm, snackbar)
                    is Screen.Graph -> GraphScreen(vm, screen, snackbar)
                    Screen.Orphans -> OrphansScreen(vm, snackbar)
                    Screen.Placeholders -> PlaceholdersScreen(vm, snackbar)
                    Screen.Tags -> TagsScreen(vm, snackbar)
                    is Screen.Connections -> ConnectionsScreen(vm, screen, snackbar)
                    // key(): each note gets its own editor state, so leaving note A for
                    // note B can never flush B's text into A.
                    is Screen.Editor -> key(screen.path) { EditorScreen(vm, screen, snackbar) }
                }
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

/** Smaller than any real Android window (the system minimum is about 220dp). */
private val MIN_LAYOUT = 200.dp

/**
 * Measures the content at least [minWidth] x [minHeight] even when this element is given less
 * room, clipping what doesn't fit, instead of handing children impossibly small constraints.
 */
private fun Modifier.atLeastSize(minWidth: Dp, minHeight: Dp): Modifier = clipToBounds().layout { measurable, c ->
    val w = if (c.hasBoundedWidth) maxOf(c.maxWidth, minWidth.roundToPx()) else c.maxWidth
    val h = if (c.hasBoundedHeight) maxOf(c.maxHeight, minHeight.roundToPx()) else c.maxHeight
    val placeable = measurable.measure(
        Constraints(minWidth = minOf(c.minWidth, w), maxWidth = w, minHeight = minOf(c.minHeight, h), maxHeight = h),
    )
    layout(c.constrainWidth(placeable.width), c.constrainHeight(placeable.height)) { placeable.place(0, 0) }
}

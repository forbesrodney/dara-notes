package app.foamforandroid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import app.foamforandroid.MainViewModel
import app.foamforandroid.Screen

/** The whole app: theme, snackbar, back navigation and the current screen. */
@Composable
fun App(vm: MainViewModel) {
    AppTheme {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
        BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
        // During some rotations (e.g. to upside down) the window can be tiny for a frame.
        // Material list rows throw if laid out narrower than their padding, so never lay the
        // app out below a usable size; the excess is just clipped.
        Box(Modifier.fillMaxSize().atLeastSize(MIN_LAYOUT, MIN_LAYOUT)) {
            when (val screen = vm.current) {
                Screen.Notes -> NotesScreen(vm, snackbar)
                Screen.Settings -> SettingsScreen(vm, snackbar)
                is Screen.Graph -> GraphScreen(vm, screen, snackbar)
                Screen.Orphans -> OrphansScreen(vm, snackbar)
                Screen.Placeholders -> PlaceholdersScreen(vm, snackbar)
                Screen.Tags -> TagsScreen(vm, snackbar)
                is Screen.Connections -> ConnectionsScreen(vm, screen, snackbar)
                // key(): each note gets its own editor state, so leaving note A for note B can
                // never flush B's text into A.
                is Screen.Editor -> key(screen.path) { EditorScreen(vm, screen, snackbar) }
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = platformColorScheme(isSystemInDarkTheme()), content = content)
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

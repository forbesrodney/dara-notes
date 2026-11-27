package app.daranotes.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Keeps a hardware keyboard's arrow keys moving the cursor in a text field. On Android,
 * Compose treats the arrows of a keyboard that also reports itself as a D-pad (many Bluetooth
 * keyboards do) as moves to another part of the screen; with nowhere to move, they move the
 * cursor as they should. Left and Right always; Up and Down too for a multi-line field, while
 * single-line fields leave them for moving between fields. The desktop and iOS never move
 * focus this way, so it changes nothing there.
 */
fun Modifier.arrowsMoveCursor(multiLine: Boolean = false): Modifier = focusProperties {
    left = FocusRequester.Cancel
    right = FocusRequester.Cancel
    if (multiLine) {
        up = FocusRequester.Cancel
        down = FocusRequester.Cancel
    }
}

package app.daranotes.ui

import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

/**
 * A tooltip for [content] (a toolbar button): shown when the pointer rests on it, or on a
 * long press on a touch screen.
 */
@Composable
fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/**
 * [text] with its keyboard shortcut, like "Bold (⌘B)", where the platform shows shortcuts
 * (the desktop app). [key] is the key with Cmd (Mac) or Ctrl (Windows, Linux).
 */
fun withShortcut(text: String, key: String): String = shortcutModifier?.let { "$text ($it$key)" } ?: text

/** "⌘" on a Mac, "Ctrl+" on Windows and Linux; null where shortcuts aren't shown. */
expect val shortcutModifier: String?

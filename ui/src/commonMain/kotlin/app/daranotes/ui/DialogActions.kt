package app.daranotes.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction

// Dialogs that ask for a name or a folder (New note, Rename, Move, template names) take their
// main action on Enter, from the text field, so the field keeps focus while it's typed in.

/**
 * A dialog's main action, which Enter also takes: a filled button, so it stands out from
 * Cancel by its shape, and looks active as soon as it can be used.
 */
@Composable
fun DefaultActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) { Text(text) }
}

/**
 * Takes [action] when Enter is pressed on a hardware keyboard (a desktop's, or one paired
 * with a phone or tablet), if [enabled]. Enter is used up either way, so it does nothing else.
 */
fun Modifier.onEnter(enabled: Boolean, action: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.key != Key.Enter && e.key != Key.NumPadEnter) return@onPreviewKeyEvent false
    if (e.type == KeyEventType.KeyDown && enabled) action()
    true
}

/** The on-screen keyboard's Done key, for the same action as Enter. */
val DoneKeyboard = KeyboardOptions(imeAction = ImeAction.Done)

/** Takes [action] from the on-screen keyboard's Done key, if [enabled]. */
fun doneAction(enabled: Boolean, action: () -> Unit) = KeyboardActions(onDone = { if (enabled) action() })

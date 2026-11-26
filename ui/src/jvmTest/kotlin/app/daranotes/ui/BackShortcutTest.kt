package app.daranotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Back shortcut and mouse button send Back as App does (a DirectNavigationEventInput on
 * the window's dispatcher). An open dialog or menu must take it, not the screen under it.
 */
@OptIn(ExperimentalTestApi::class)
class BackShortcutTest {
    @Test fun openDialogsAndMenusTakeBackFirst() = runComposeUiTest {
        val handled = mutableListOf<String>()
        val dialog = mutableStateOf(false)
        val menu = mutableStateOf(false)
        val input = DirectNavigationEventInput()
        setContent {
            val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher.addInput(input)
                onDispose { dispatcher.removeInput(input) }
            }
            BackHandler(true) { handled += "screen" }
            Box {
                if (dialog.value) {
                    AlertDialog(
                        onDismissRequest = { handled += "dialog"; dialog.value = false },
                        confirmButton = { TextButton({}) { Text("OK") } },
                        text = { Text("Dialog") },
                    )
                }
                DropdownMenu(menu.value, onDismissRequest = { handled += "menu"; menu.value = false }) {
                    DropdownMenuItem(text = { Text("Item") }, onClick = {})
                }
            }
        }
        fun back() { waitForIdle(); input.backCompleted(); waitForIdle() }

        back()
        dialog.value = true
        back()
        menu.value = true
        back()
        assertEquals(listOf("screen", "dialog", "menu"), handled)
    }
}

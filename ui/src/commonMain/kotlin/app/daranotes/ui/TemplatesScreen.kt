package app.daranotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.daranotes.MainViewModel
import app.daranotes.Screen
import app.daranotes.core.FoamConfig
import app.daranotes.core.TemplateListing
import app.daranotes.core.ioDispatcher
import kotlinx.coroutines.withContext

/**
 * The templates in .foam/templates: Foam's new-note and daily-note templates first (with
 * Create when one doesn't exist yet), then the ones *Create note from template* offers.
 * Tap one to edit it.
 */
@Composable
fun TemplatesScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val sync by vm.syncState.collectAsState()
    var templates by remember { mutableStateOf<List<TemplateListing>?>(null) }
    var reload by remember { mutableStateOf(0) }
    // Opening this screen, a sync and every change made here reload the list.
    LaunchedEffect(sync.generation, reload) {
        templates = withContext(ioDispatcher) { vm.repo.templateList() }
    }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<TemplateListing?>(null) }
    var renaming by remember { mutableStateOf<TemplateListing?>(null) }
    var deleting by remember { mutableStateOf<TemplateListing?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Tip("Back") {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = { Text("Templates") },
                actions = {
                    Tip("Help with templates") {
                        IconButton(onClick = { vm.navigate(Screen.Help("templates")) }) {
                            Icon(HelpIcon, contentDescription = "Help with templates")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New template") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = templates ?: return@Scaffold
        val (defaults, others) = list.partition { it.defaultFor != null }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionLabel("Used automatically") }
            items(defaults, key = { it.path }) { t ->
                val role = if (t.defaultFor == FoamConfig.DAILY_NOTE_TEMPLATE) "Used for daily notes" else "Used for every new note"
                ListItem(
                    headlineContent = { Text(t.name) },
                    supportingContent = {
                        Text(if (t.exists) role else "$role. Not created yet: notes start with a heading.")
                    },
                    trailingContent = if (t.exists) null else {
                        { TextButton(onClick = { vm.createDefaultTemplate(t.defaultFor!!) }) { Text("Create") } }
                    },
                    modifier = if (t.exists) Modifier.clickable { vm.openPath(t.path) } else Modifier,
                )
            }
            item {
                HorizontalDivider()
                SectionLabel("For Create note from template")
            }
            if (others.isEmpty()) {
                item {
                    Text(
                        "None yet. Tap New template to make one.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(others, key = { it.path }) { t ->
                ListItem(
                        headlineContent = { Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                t.description ?: t.path.removePrefix("${FoamConfig.TEMPLATES_DIR}/"),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            // The menu opens from its button.
                            Box {
                                Tip("More options") {
                                    IconButton(onClick = { menuFor = t }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options for ${t.name}")
                                    }
                                }
                                DropdownMenu(expanded = menuFor == t, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(
                                        text = { Text("Rename file") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = { menuFor = null; renaming = t },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                                        onClick = { menuFor = null; deleting = t },
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { vm.openPath(t.path) },
                    )
            }
            // Room so the last row clears the New template button.
            item { Box(Modifier.padding(bottom = 88.dp)) }
        }
    }

    if (creating) {
        NameDialog(
            heading = "New template",
            label = "Name",
            hint = "Shown in Create note from template. You can change it, and add a description, in the template.",
            initial = "",
            confirm = "Create",
            onDismiss = { creating = false },
        ) { name ->
            creating = false
            vm.createTemplate(name)
        }
    }
    renaming?.let { t ->
        NameDialog(
            heading = "Rename template file",
            label = "File name",
            hint = "The name in Create note from template comes from the template's name setting, not the file.",
            initial = t.path.substringAfterLast('/').removeSuffix(".md"),
            confirm = "Rename",
            onDismiss = { renaming = null },
        ) { name ->
            renaming = null
            vm.renameTemplate(t.path, name) { reload++ }
        }
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete template?") },
            text = { Text("${t.path.substringAfterLast('/')} will be deleted. Notes made from it stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.deleteTemplate(t.path) { reload++ }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Asks for a name: a single text field, focused, with [confirm] enabled once it isn't blank. */
@Composable
private fun NameDialog(
    heading: String,
    label: String,
    hint: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val submit = { onConfirm(text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = DoneKeyboard,
                    keyboardActions = doneAction(text.isNotBlank(), submit),
                    modifier = Modifier.focusRequester(focus).onEnter(text.isNotBlank(), submit).arrowsMoveCursor(),
                )
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            DefaultActionButton(confirm, text.isNotBlank(), submit)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

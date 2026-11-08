package app.foamnotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.foamnotes.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Material "folder" icon (not in material-icons-core). */
private val FolderIcon: ImageVector by lazy {
    ImageVector.Builder("Folder", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes("M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"),
        fill = SolidColor(Color.Black),
    ).build()
}

private fun folderOf(path: String) = path.substringBeforeLast('/', "")
private fun nameOf(path: String) = path.substringAfterLast('/').removeSuffix(".md")
private fun join(folder: String, name: String) =
    (if (folder.isBlank()) "" else folder.trim().trim('/') + "/") + name.trim() + ".md"

/** Asks for a new file name for [path] (same folder). */
@Composable
fun RenameNoteDialog(vm: MainViewModel, path: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val name = nameOf(path)
    var field by remember { mutableStateOf(TextFieldValue(name, TextRange(0, name.length))) }
    val newPath = join(folderOf(path), field.text)
    val error = when {
        field.text.contains('/') -> "Use Move to change the folder"
        field.text.trim() == name -> null
        else -> vm.repo.relocateError(path, newPath)
    }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename note") },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text("File name") },
                    suffix = { Text(".md") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(error ?: "Links to this note are updated in every note")
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newPath) },
                enabled = error == null && field.text.isNotBlank() && field.text.trim() != name,
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Asks for a destination folder for [path], offering the repository's existing folders. */
@Composable
fun MoveNoteDialog(vm: MainViewModel, path: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val current = folderOf(path)
    var folder by remember { mutableStateOf(current) }
    var folders by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) { folders = withContext(Dispatchers.IO) { vm.repo.folders() } }
    val newPath = join(folder, nameOf(path))
    val unchanged = folder.trim().trim('/') == current
    val error = if (unchanged) null else vm.repo.relocateError(path, newPath)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${nameOf(path)}.md") },
        text = {
            Column {
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Folder") },
                    placeholder = { Text("Repository root") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Type a new folder name to create it") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    item {
                        FolderRow("Repository root", folder.isBlank(), root = true) { folder = "" }
                    }
                    items(folders) { f ->
                        FolderRow(f, folder.trim().trim('/') == f) { folder = f }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newPath) }, enabled = error == null && !unchanged) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FolderRow(label: String, selected: Boolean, root: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = {
            Icon(if (root) Icons.Default.Home else FolderIcon, contentDescription = null)
        },
        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else ListItemDefaults.colors(),
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 0.dp),
    )
    HorizontalDivider()
}

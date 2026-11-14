package app.daranotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.daranotes.core.FrontMatterTags

/**
 * A note's front matter tags: tap a tag's ✕ to remove it, or type one (with suggestions
 * from the tags already in use) to add it. Each change goes into the note at once.
 * [inline] are the note's `#tags` in the text, shown but edited in the note itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsDialog(
    tags: List<String>,
    inline: List<String>,
    known: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val tag = FrontMatterTags.normalize(query)
    val already = tag != null && tags.any { it.equals(tag, ignoreCase = true) }
    val suggestions = remember(query, tags, known) {
        val q = query.trim().removePrefix("#")
        known.filter { k -> tags.none { it.equals(k, ignoreCase = true) } && (q.isEmpty() || k.contains(q, ignoreCase = true)) }
            .sortedBy { if (it.startsWith(q, ignoreCase = true)) 0 else 1 }
            .take(12)
    }
    fun add(t: String) {
        onAdd(t)
        query = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (tags.isEmpty()) {
                    Text(
                        "No tags yet. Adding one adds the note's front matter if needed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in tags) {
                            InputChip(
                                selected = false,
                                onClick = { onRemove(t) },
                                label = { Text(t) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove $t", Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
                if (inline.isNotEmpty()) {
                    Text(
                        "In the text: " + inline.joinToString(" ") { "#$it" } + ". Edit those in the note.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Add a tag") },
                        singleLine = true,
                        isError = query.isNotBlank() && tag == null,
                        supportingText = {
                            when {
                                query.isNotBlank() && tag == null -> Text("Letters, numbers, -, _ and / only, no spaces")
                                already -> Text("Already tagged")
                                else -> Text("Use / to nest: dev/git")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (tag != null && !already) add(tag) }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    TextButton(onClick = { tag?.let(::add) }, enabled = tag != null && !already) { Text("Add") }
                }
                if (suggestions.isNotEmpty()) {
                    Text(
                        if (query.isBlank()) "Tags in your notes" else "Matching tags",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (s in suggestions) {
                            SuggestionChip(onClick = { add(s) }, label = { Text(s) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

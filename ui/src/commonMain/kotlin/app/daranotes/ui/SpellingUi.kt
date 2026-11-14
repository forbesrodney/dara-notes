package app.daranotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.daranotes.MainViewModel
import app.daranotes.core.DictionaryInfo
import app.daranotes.core.DictionaryManifest
import app.daranotes.core.SpellIssue
import app.daranotes.core.ioDispatcher
import kotlinx.coroutines.withContext

/** Material "spellcheck" icon. */
val SpellcheckIcon: ImageVector by lazy {
    menuIcon(
        "Spellcheck",
        "M12.45,16h2.09L9.43,3H7.57L2.46,16h2.09l1.12,-3h5.64l1.14,3zM6.43,11L8.5,5.48 10.57,11L6.43,11z" +
            "M21.59,11.59l-8.09,8.09L9.83,16l-1.41,1.41 5.09,5.09L23,13l-1.41,-1.41z",
    )
}

/** The downloaded languages, under Settings > Spelling, with the button to change them. */
@Composable
fun SpellingLanguagesRow(vm: MainViewModel, onChoose: () -> Unit) {
    val languages by vm.languages.collectAsState()
    LaunchedEffect(Unit) { vm.refreshLanguages() }
    Text(
        if (languages.isEmpty()) "No languages downloaded yet: download one to check spelling."
        else "Languages: " + languages.joinToString { it.name },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(if (languages.isEmpty()) "Download a language…" else "Languages…")
    }
}

/**
 * The languages offered (from the `dictionaries` release), to download, update or remove.
 * Downloads go straight into the app's storage; nothing here waits for Save.
 */
@Composable
fun SpellingLanguagesDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val installed by vm.languages.collectAsState()
    var manifest by remember { mutableStateOf<DictionaryManifest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(setOf<String>()) }
    val uri = LocalUriHandler.current
    LaunchedEffect(attempt) {
        error = null
        withContext(ioDispatcher) { runCatching { vm.dictionaries.manifest() } }
            .onSuccess { manifest = it }
            .onFailure { error = "Couldn't get the list of languages: ${it.message ?: it::class.simpleName}" }
    }
    fun install(info: DictionaryInfo, m: DictionaryManifest) {
        busy = busy + info.code
        vm.installLanguage(info, m) { busy = busy - info.code }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spelling languages") },
        text = {
            Column {
                val m = manifest
                when {
                    m != null -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(m.languages, key = { it.code }) { info ->
                            val have = installed.firstOrNull { it.code == info.code }
                            LanguageRow(
                                name = info.name,
                                detail = "${megabytes(info.size)} · ${grouped(info.words)} words",
                                license = info.license,
                                onLicense = { runCatching { uri.openUri(vm.dictionaries.licenseUrl(info)) } },
                                busy = info.code in busy,
                            ) {
                                when {
                                    have == null -> TextButton(onClick = { install(info, m) }) { Text("Download") }
                                    else -> {
                                        if (have.sha256 != info.sha256) TextButton(onClick = { install(info, m) }) { Text("Update") }
                                        TextButton(onClick = { vm.removeLanguage(info.code) }) { Text("Remove") }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    error != null -> {
                        Text(error!!, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { attempt++ }) { Text("Try again") }
                        // Offline, the downloaded ones can still be removed.
                        for (have in installed) {
                            LanguageRow(have.name, "Downloaded", null, {}, busy = false) {
                                TextButton(onClick = { vm.removeLanguage(have.code) }) { Text("Remove") }
                            }
                        }
                    }
                    else -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                manifest?.extras?.let {
                    Text(
                        "${it.name} (from cSpell, ${megabytes(it.size)}) come with the first language, so the " +
                            "same technical words pass as in VS Code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun LanguageRow(
    name: String,
    detail: String,
    license: String?,
    onLicense: () -> Unit,
    busy: Boolean,
    actions: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (license != null) {
                Text(
                    "License: $license",
                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onLicense),
                )
            }
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp) else actions()
    }
}

private fun megabytes(bytes: Long): String {
    val tenths = (bytes * 10 + 524_288) / 1_048_576
    return if (tenths < 1) "under 0.1 MB" else "${tenths / 10}.${tenths % 10} MB"
}

private fun grouped(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** The chip under the toolbar with the note's spelling issues; tap for the list. */
@Composable
fun SpellingChip(count: Int, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(if (count == 1) "1 spelling issue" else "$count spelling issues") },
        leadingIcon = { Icon(SpellcheckIcon, null, Modifier.size(18.dp)) },
    )
}

/**
 * Fixes for the misspelled word at the cursor: its suggestions, and ignoring it in this note
 * or adding it to the repository's dictionary.
 */
@Composable
fun SpellingBar(
    word: String,
    suggestions: List<String>?,
    onReplace: (String) -> Unit,
    onIgnore: () -> Unit,
    onAdd: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(SpellcheckIcon, contentDescription = "Spelling", Modifier.size(18.dp))
        // The suggestions scroll; the other fixes stay in reach at the end.
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                suggestions == null -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                suggestions.isEmpty() -> Text("No suggestions for \u201c$word\u201d", style = MaterialTheme.typography.bodyMedium)
                else -> for (s in suggestions) SuggestionChip(onClick = { onReplace(s) }, label = { Text(s) })
            }
        }
        Box {
            Tip("Ignore or add \u201c$word\u201d") {
                IconButton(onClick = { more = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More spelling options")
                }
            }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(
                    text = { Text("Ignore \u201c$word\u201d in this note") },
                    onClick = { more = false; onIgnore() },
                )
                DropdownMenuItem(
                    text = { Text("Add \u201c$word\u201d to the repository's dictionary") },
                    onClick = { more = false; onAdd() },
                )
            }
        }
    }
}

/** The note's misspelled words, each once with how often it appears; tap one to go to it. */
@Composable
fun SpellingIssuesDialog(issues: List<SpellIssue>, onDismiss: () -> Unit, onJump: (SpellIssue) -> Unit) {
    val words = remember(issues) { issues.groupBy { it.word.lowercase() }.values.toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spelling") },
        text = {
            Column {
                Text(
                    "Tap a word to go to it, then pick a suggestion, ignore it in this note, or add it to the " +
                        "repository's dictionary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(words) { group ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onJump(group.first()) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(group.first().word, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (group.size > 1) {
                                Text("×${group.size}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

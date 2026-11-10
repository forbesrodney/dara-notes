package app.foamforandroid.ui

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.foamforandroid.MainViewModel
import app.foamforandroid.core.CompletionDate
import app.foamforandroid.core.DateOrder
import app.foamforandroid.core.Dates
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val s = vm.settings
    val sync by vm.syncState.collectAsState()
    var remoteUrl by remember { mutableStateOf(s.remoteUrl) }
    var branch by remember { mutableStateOf(s.branch) }
    var username by remember { mutableStateOf(s.username) }
    var token by remember { mutableStateOf(s.token) }
    var authorName by remember { mutableStateOf(s.authorName) }
    var authorEmail by remember { mutableStateOf(s.authorEmail) }
    var indexFile by remember { mutableStateOf(s.indexFile) }
    var indexDaily by remember { mutableStateOf(s.indexDailyNotes) }
    var bullet by remember { mutableStateOf(s.bullet) }
    var strike by remember { mutableStateOf(s.strikeCompleted) }
    var lintEnabled by remember { mutableStateOf(s.lintEnabled) }
    var addDate by remember { mutableStateOf(s.dateOnComplete) }
    var dateOrder by remember { mutableStateOf(s.dateOrder) }
    var dateSep by remember { mutableStateOf(s.dateSeparator) }
    var newNoteDir by remember { mutableStateOf(s.newNoteDir) }
    var dailyDir by remember { mutableStateOf(s.dailyDirOverride) }
    var autoSync by remember { mutableStateOf(s.autoSyncMinutes.toString()) }
    var syncAfterEdit by remember { mutableStateOf(s.syncAfterEdit) }
    var confirmReclone by remember { mutableStateOf(false) }

    // Tabs are pages of a pager, so you can swipe between them as well as tap.
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }

    val repoChanged = s.isConfigured && (remoteUrl.trim() != s.remoteUrl || branch.trim() != s.branch)
    val autoSyncValue = autoSync.toIntOrNull()?.let { if (it in 1..14) 15 else it } ?: 15
    // Anything different from what's saved (so Save can light up and Back can warn).
    val dirty = remoteUrl.trim() != s.remoteUrl || branch.trim().ifBlank { "main" } != s.branch ||
        username.trim() != s.username || token.trim() != s.token ||
        authorName.trim() != s.authorName || authorEmail.trim() != s.authorEmail ||
        indexFile.trim().ifBlank { "Index.md" } != s.indexFile || indexDaily != s.indexDailyNotes ||
        bullet != s.bullet || strike != s.strikeCompleted || lintEnabled != s.lintEnabled ||
        addDate != s.dateOnComplete || dateOrder != s.dateOrder || dateSep != s.dateSeparator ||
        newNoteDir.trim().trim('/') != s.newNoteDir || dailyDir.trim().trim('/') != s.dailyDirOverride ||
        autoSyncValue != s.autoSyncMinutes || syncAfterEdit != s.syncAfterEdit

    fun persist() {
        s.remoteUrl = remoteUrl.trim()
        s.branch = branch.trim().ifBlank { "main" }
        s.username = username.trim()
        s.token = token.trim()
        s.authorName = authorName.trim()
        s.authorEmail = authorEmail.trim()
        s.indexFile = indexFile.trim().ifBlank { "Index.md" }
        s.indexDailyNotes = indexDaily
        s.bullet = bullet
        s.strikeCompleted = strike
        s.lintEnabled = lintEnabled
        s.dateOnComplete = addDate
        s.dateOrder = dateOrder
        s.dateSeparator = dateSep
        s.newNoteDir = newNoteDir.trim().trim('/')
        s.dailyDirOverride = dailyDir.trim().trim('/')
        s.autoSyncMinutes = autoSyncValue
        s.syncAfterEdit = syncAfterEdit
        vm.applySyncSchedule()
    }

    fun save() {
        if (repoChanged) confirmReclone = true
        else { persist(); vm.back(); vm.syncNow() }
    }

    fun leave() { if (dirty && s.isConfigured) confirmDiscard = true else vm.back() }
    BackHandler(enabled = dirty && s.isConfigured) { confirmDiscard = true }

    val tabs = listOf("Repo", "Notes", "Editing", "Sync")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (s.isConfigured) "Settings" else "Set up repository") },
                    navigationIcon = {
                        if (vm.backStack.size > 1) {
                            IconButton(onClick = ::leave) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (s.isConfigured) {
                            TextButton(onClick = ::save, enabled = dirty && !sync.running) {
                                Text(if (repoChanged) "Save & re-clone" else "Save")
                            }
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                    tabs.forEachIndexed { i, title ->
                        // Custom content instead of Tab(text = …): the standard text slot adds 16dp
                        // of padding each side, which squeezes titles on narrow phones.
                        Tab(
                            selected = pager.currentPage == i,
                            onClick = { scope.launch { pager.animateScrollToPage(i) } },
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            verticalAlignment = Alignment.Top,
        ) { page ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (page) {
                0 -> {
                    Field("Remote URL (HTTPS)", remoteUrl, { remoteUrl = it }, hint = "https://github.com/you/notes.git — blank for a local-only repo", keyboard = KeyboardType.Uri)
                    Field("Branch", branch, { branch = it })
                    Field("Username", username, { username = it }, hint = "Your Git host username")
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Access token") },
                        supportingText = { Text("Personal access token with repo read/write. Stored encrypted.") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Field("Commit author name", authorName, { authorName = it })
                    Field("Commit author email", authorEmail, { authorEmail = it }, keyboard = KeyboardType.Email)
                    Spacer(Modifier.height(8.dp))
                    if (!s.isConfigured) {
                        Button(
                            onClick = { persist(); vm.cloneRepository {} },
                            enabled = !sync.running,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (sync.running) "Working…" else if (remoteUrl.isBlank()) "Create local repository" else "Clone repository") }
                        Text(
                            "The other tabs can be set now or later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutlinedButton(onClick = { confirmReclone = true }, enabled = !sync.running, modifier = Modifier.fillMaxWidth()) {
                            Text("Discard local copy and re-clone")
                        }
                    }
                    sync.lastError?.let {
                        Text("Last error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    sync.lastHint?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                1 -> {
                    Field("Index file", indexFile, { indexFile = it }, hint = "New notes are linked here as $bullet [[note]]")
                    Toggle("Add daily notes to the index", indexDaily) { indexDaily = it }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("List bullet", Modifier.weight(1f))
                        listOf("-" to "Dash  -", "*" to "Asterisk  *").forEach { (c, label) ->
                            FilterChip(
                                selected = bullet == c,
                                onClick = { bullet = c },
                                label = { Text(label) },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Field("Folder for new notes", newNoteDir, { newNoteDir = it }, hint = "Blank = repository root")
                    Field("Daily notes folder", dailyDir, { dailyDir = it }, hint = "Used when the daily-note template has no filepath. Blank = journal")
                    if (s.isConfigured) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { persist(); vm.rebuildIndex() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Add missing notes to ${indexFile.ifBlank { "Index.md" }}")
                        }
                    }
                }
                2 -> {
                    Section("markdownlint")
                    Toggle("Check Markdown with markdownlint", lintEnabled) { lintEnabled = it }
                    Text(
                        "Uses the repository's .markdownlint.json (or .jsonc, .yaml, .markdownlint-cli2.*), " +
                            "like the VS Code extension. Without one, all default rules apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Section("Checklists")
                    Toggle("Strike through checked items", strike) { strike = it }
                    Toggle("Add completion date to checked items", addDate) { addDate = it }
                    val (localeOrder, localeSep) = remember { CompletionDate.localeDefaults() }
                    val (order, sep) = CompletionDate.resolve(dateOrder, dateSep)
                    val today = remember { Dates.today() }
                    val sample = { o: DateOrder, sp: String -> CompletionDate.format(today, o, sp) }
                    Text("Date format", style = MaterialTheme.typography.bodyLarge)
                    ChipRow(
                        listOf(CompletionDate.LOCALE to "Locale (${sample(localeOrder, sep)})") +
                            DateOrder.entries.map { o -> o.key to o.label(sep) },
                        dateOrder,
                    ) { dateOrder = it }
                    Text("Date separator", style = MaterialTheme.typography.bodyLarge)
                    ChipRow(
                        listOf(CompletionDate.LOCALE to "Locale ( $localeSep )") +
                            CompletionDate.separators.map { it to "  $it  " },
                        dateSep,
                        enabled = order.usesSeparator,
                    ) { dateSep = it }
                    Text(
                        "Checked today: " + (if (strike) "~~Buy milk~~" else "Buy milk") +
                            (if (addDate) " " + CompletionDate.stamp(today, order, sep) else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                3 -> {
                    Field("Background sync every (minutes, 0 = off)", autoSync, { autoSync = it.filter(Char::isDigit) }, hint = "Android allows 15 minutes minimum", keyboard = KeyboardType.Number)
                    Toggle("Sync shortly after editing", syncAfterEdit) { syncAfterEdit = it }
                }
            }
        }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to the settings haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; vm.back() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false; save() }) { Text(if (repoChanged) "Save & re-clone" else "Save") }
            },
        )
    }

    if (confirmReclone) {
        AlertDialog(
            onDismissRequest = { confirmReclone = false },
            title = { Text("Re-clone repository?") },
            text = { Text("The local copy is deleted and cloned again. Changes that have not been pushed will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReclone = false
                    persist()
                    vm.cloneRepository {}
                }) { Text("Re-clone") }
            },
            dismissButton = { TextButton(onClick = { confirmReclone = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

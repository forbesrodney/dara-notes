package app.daranotes.ui

import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.daranotes.MainViewModel
import app.daranotes.Screen
import app.daranotes.core.CompletionDate
import app.daranotes.core.DateOrder
import app.daranotes.core.Dates
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * The settings. [active] is false while help opened from here is showing: the screen stays
 * composed underneath (see App), so unsaved changes survive, but leaves Back to the help.
 */
fun SettingsScreen(vm: MainViewModel, snackbar: SnackbarHostState, active: Boolean = true) {
    val s = vm.settings
    val focusManager = LocalFocusManager.current
    val sync by vm.syncState.collectAsState()
    var remoteUrl by remember { mutableStateOf(s.remoteUrl) }
    var branch by remember { mutableStateOf(s.branch) }
    var username by remember { mutableStateOf(s.username) }
    var token by remember { mutableStateOf(s.token) }
    var authorName by remember { mutableStateOf(s.authorName) }
    var authorEmail by remember { mutableStateOf(s.authorEmail) }
    var linkIndex by remember { mutableStateOf(s.linkFromIndex) }
    var indexFile by remember { mutableStateOf(s.indexFile) }
    var indexDaily by remember { mutableStateOf(s.indexDailyNotes) }
    var bullet by remember { mutableStateOf(s.bullet) }
    var strike by remember { mutableStateOf(s.strikeCompleted) }
    var lintEnabled by remember { mutableStateOf(s.lintEnabled) }
    var spellEnabled by remember { mutableStateOf(s.spellEnabled) }
    var choosingLanguages by remember { mutableStateOf(false) }
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
        linkIndex != s.linkFromIndex || indexFile.trim().ifBlank { "Index.md" } != s.indexFile || indexDaily != s.indexDailyNotes ||
        bullet != s.bullet || strike != s.strikeCompleted || lintEnabled != s.lintEnabled || spellEnabled != s.spellEnabled ||
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
        s.linkFromIndex = linkIndex
        s.indexFile = indexFile.trim().ifBlank { "Index.md" }
        s.indexDailyNotes = indexDaily
        s.bullet = bullet
        s.strikeCompleted = strike
        s.lintEnabled = lintEnabled
        s.spellEnabled = spellEnabled
        s.dateOnComplete = addDate
        s.dateOrder = dateOrder
        s.dateSeparator = dateSep
        s.newNoteDir = newNoteDir.trim().trim('/')
        s.dailyDirOverride = dailyDir.trim().trim('/')
        s.autoSyncMinutes = autoSyncValue
        s.syncAfterEdit = syncAfterEdit
        vm.applySyncSchedule()
        // Spelling switched on with no language yet: download one, rather than check nothing.
        if (spellEnabled) vm.downloadDefaultDictionary(fromSave = true)
    }

    fun save() {
        if (repoChanged) confirmReclone = true
        else { persist(); vm.back(); vm.syncNow() }
    }

    fun leave() { if (dirty && s.isConfigured) confirmDiscard = true else vm.back() }
    BackHandler(enabled = active && dirty && s.isConfigured) { confirmDiscard = true }

    // Cmd+N or Cmd+D with unsaved changes: ask first, as Back does, then do it once Settings
    // is saved or discarded. (Not after Save & re-clone: the re-clone is the thing to watch.)
    var afterLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guarded = dirty && s.isConfigured
    DisposableEffect(guarded) {
        vm.leaveGuard = if (guarded) { action -> afterLeave = action; confirmDiscard = true } else null
        onDispose { vm.leaveGuard = null }
    }
    fun leaveThen(saving: Boolean) {
        confirmDiscard = false
        val action = afterLeave
        afterLeave = null
        if (saving) save() else vm.back()
        if (action != null && !(saving && repoChanged)) action()
    }

    val tabs = listOf("Repo", "Notes", "Editing", "Sync")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (s.isConfigured) "Settings" else "Set up repository") },
                    navigationIcon = {
                        if (vm.backStack.size > 1) {
                            Tip("Back") {
                                IconButton(onClick = ::leave) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        }
                    },
                    actions = {
                        // Help is here too, for setting up before there's a note list.
                        Tip("Help") {
                            // Unfocus first, so no hidden field keeps the keyboard up.
                            IconButton(onClick = { focusManager.clearFocus(); vm.navigate(Screen.Help()) }) {
                                Icon(HelpIcon, contentDescription = "Help")
                            }
                        }
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
                    // Hidden until the eye button shows it, to check what was pasted; hidden
                    // again whenever Settings opens.
                    var showToken by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Access token") },
                        supportingText = { Text("Personal access token with repo read/write. Stored encrypted.") },
                        trailingIcon = {
                            val label = if (showToken) "Hide token" else "Show token"
                            Tip(label) {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(if (showToken) HideIcon else ShowIcon, contentDescription = label)
                                }
                            }
                        },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().arrowsMoveCursor(),
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
                    Field(
                        "Index note", indexFile, { indexFile = it },
                        hint = if (linkIndex) "Opened by the Home button; new notes are linked here as $bullet [[note]]"
                        else "Opened by the Home button",
                    )
                    Toggle("Link new notes from the index note", linkIndex) { linkIndex = it }
                    if (linkIndex) {
                        Toggle("Add daily notes to the index", indexDaily) { indexDaily = it }
                    } else {
                        Text(
                            "Off, as in Foam: notes are tied together only by the links you write.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
                    if (s.isConfigured && linkIndex) {
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

                    Section("Spelling")
                    Toggle("Check spelling", spellEnabled) { spellEnabled = it }
                    Text(
                        "Like the Code Spell Checker extension in VS Code: uses the repository's cspell.json and " +
                            "cSpell settings, and the cspell: comments in notes. Misspelled words are underlined while editing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SpellingLanguagesRow(vm, enabled = spellEnabled) { choosingLanguages = true }

                    Section("Checklists")
                    Toggle("Strike through checked items", strike) { strike = it }
                    Toggle("Add completion date to checked items", addDate) { addDate = it }
                    val (localeOrder, localeSep) = remember { CompletionDate.localeDefaults() }
                    val (order, sep) = CompletionDate.resolve(dateOrder, dateSep)
                    val today = remember { Dates.today() }
                    val sample = { o: DateOrder, sp: String -> CompletionDate.format(today, o, sp) }
                    Text("Date format", style = MaterialTheme.typography.bodyLarge, color = dimmedUnless(addDate))
                    ChipRow(
                        listOf(CompletionDate.LOCALE to "Locale (${sample(localeOrder, sep)})") +
                            DateOrder.entries.map { o -> o.key to o.label(sep) },
                        dateOrder,
                        enabled = addDate,
                    ) { dateOrder = it }
                    Text("Date separator", style = MaterialTheme.typography.bodyLarge, color = dimmedUnless(addDate))
                    ChipRow(
                        listOf(CompletionDate.LOCALE to "Locale ( $localeSep )") +
                            CompletionDate.separators.map { it to "  $it  " },
                        dateSep,
                        enabled = addDate && order.usesSeparator,
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

    if (choosingLanguages) SpellingLanguagesDialog(vm) { choosingLanguages = false }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false; afterLeave = null },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to the settings haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { leaveThen(saving = false) }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { leaveThen(saving = true) }) { Text(if (repoChanged) "Save & re-clone" else "Save") }
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

/** A label's color: as usual if [enabled], otherwise dimmed like the controls it labels. */
@Composable
fun dimmedUnless(enabled: Boolean): Color =
    if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

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
        modifier = Modifier.fillMaxWidth().arrowsMoveCursor(),
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

/** Material "visibility" icon (an open eye), to show the access token. */
private val ShowIcon: ImageVector by lazy {
    menuIcon(
        "Show",
        "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
            "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z" +
            "M12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
    )
}

/** Material "visibility_off" icon (an eye struck through), to hide the access token again. */
private val HideIcon: ImageVector by lazy {
    menuIcon(
        "Hide",
        "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75" +
            " -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
            "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3" +
            " 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
            "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03" +
            " 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2z" +
            "M11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
    )
}

package app.daranotes

import app.daranotes.core.DictionaryManifest
import app.daranotes.core.DictionaryInfo
import app.daranotes.core.InstalledDictionary
import app.daranotes.core.SpellChecker
import app.daranotes.core.Dictionaries
import app.daranotes.core.CSpellConfig
import app.daranotes.core.ioDispatcher
import androidx.compose.ui.text.intl.Locale
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.daranotes.core.Dates
import app.daranotes.core.GitErrors
import app.daranotes.core.GitSync
import app.daranotes.core.Graph
import app.daranotes.core.Note
import app.daranotes.core.NoteRepository
import app.daranotes.core.Settings
import app.daranotes.core.SyncState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Screen {
    data object Notes : Screen
    data class Editor(val path: String, val startEditing: Boolean = false) : Screen
    data object Settings : Screen
    /** The note graph, optionally centered on [focus] (a note path). */
    data class Graph(val focus: String? = null) : Screen
    /** Notes with no links to or from other notes. */
    data object Orphans : Screen
    /** Wikilinks to notes that don't exist, and the notes that use them. */
    data object Placeholders : Screen
    /** Foam's Tag Explorer. */
    data object Tags : Screen
    /** Links in the note at [path] and what they point to, plus notes linking to it. */
    data class Connections(val path: String) : Screen
    /** The templates in .foam/templates, to create, open, rename or delete. */
    data object Templates : Screen
    /** A month calendar of daily notes and the days other notes were last edited. */
    data object Calendar : Screen
    /** A help page, by name (see Help). */
    data class Help(val page: String = app.daranotes.ui.Help.INDEX) : Screen
}

class MainViewModel(val platform: AppPlatform) : ViewModel() {
    val settings: Settings = platform.settings
    val repo = NoteRepository(platform.fs, platform.repoDir, settings)
    val syncState: StateFlow<SyncState> = GitSync.state

    val backStack = mutableStateListOf<Screen>(if (settings.isConfigured) Screen.Notes else Screen.Settings)
    val current: Screen get() = backStack.last()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    /** The note list's scroll position, kept while you open notes and come back. */
    val notesListState = LazyListState()

    /** Note list layout (flat or by folder, saved), open folders and the folder view's scroll. */
    val notesTreeView = mutableStateOf(settings.notesView == "tree")
    val notesExpanded = mutableStateOf(emptySet<String>())
    val notesTreeListState = LazyListState()

    fun setNotesTreeView(tree: Boolean) {
        notesTreeView.value = tree
        settings.notesView = if (tree) "tree" else "flat"
    }

    /**
     * Calendar state: the month shown (its 1st) and the day picked, kept while you open a
     * note and come back. Null until the calendar first opens, on today.
     */
    val calendarMonth = mutableStateOf<kotlinx.datetime.LocalDate?>(null)
    val calendarDay = mutableStateOf<kotlinx.datetime.LocalDate?>(null)

    /** Tag Explorer state: open tags (lowercase), the filter text and the scroll position. */
    val tagsExpanded = mutableStateOf(emptySet<String>())
    val tagsQuery = mutableStateOf("")
    val tagsListState = LazyListState()

    /** Opens the Tag Explorer filtered to [tag]. */
    fun showTag(tag: String) {
        tagsQuery.value = tag.removePrefix("#")
        navigate(Screen.Tags)
    }

    /** Last graph positions and view (JSON from the graph page), kept while the app runs. */
    var graphLayout: String? = null

    /** One-off messages for the snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        viewModelScope.launch {
            syncState.map { it.generation }.distinctUntilChanged().collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch(ioDispatcher) { _notes.value = repo.listNotes() }
    }

    // ---- navigation ----------------------------------------------------------------------

    fun navigate(screen: Screen) { backStack.add(screen) }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun openPath(path: String, startEditing: Boolean = false) = navigate(Screen.Editor(path, startEditing))

    // ---- sync ----------------------------------------------------------------------------

    fun syncNow() {
        if (!settings.isConfigured) return
        viewModelScope.launch {
            // Success shows under the note list's title ("Synced just now"), so only a failure
            // gets a message.
            GitSync.sync(repo.root, settings)
                .onFailure { messages.emit("Sync failed: ${GitErrors.hint(it) ?: it.message}") }
        }
    }

    /** Called when the app comes to the foreground; pulls changes made elsewhere. */
    fun onForeground() {
        val last = syncState.value.lastSyncAt
        if (settings.isConfigured && !syncState.value.running && Dates.nowMillis() - last > 60_000) {
            syncNow()
        }
    }

    fun cloneRepository(onDone: () -> Unit) {
        viewModelScope.launch {
            GitSync.cloneFresh(repo.root, settings)
                .onSuccess {
                    platform.scheduleSync()
                    messages.emit("Repository ready")
                    backStack.clear()
                    backStack.add(Screen.Notes)
                    onDone()
                }
                .onFailure { messages.emit("Clone failed: ${GitErrors.hint(it) ?: it.message}") }
        }
    }

    fun applySyncSchedule() = platform.scheduleSync()

    /** The open note's unsaved text (path to text), if any; the editor sets this. */
    var unsavedEdit: (() -> Pair<String, String>?)? = null

    /**
     * For when the app goes to the background or quits (iOS, desktop): writes the editor's
     * unsaved text and, if there are edits not yet pushed, syncs now. The usual sync 30
     * seconds after an edit wouldn't run once the app is suspended or gone, and changes left
     * unpushed make merge conflicts likelier. Gives up after [timeoutMillis].
     */
    suspend fun syncBeforeLeaving(timeoutMillis: Long) {
        val edit = unsavedEdit?.invoke()
        if (edit != null) withContext(ioDispatcher) { repo.write(edit.first, edit.second) }
        val pending = platform.takePendingSync() || (edit != null && settings.syncAfterEdit)
        if (!pending || !settings.isConfigured || settings.remoteUrl.isBlank()) return
        withTimeoutOrNull(timeoutMillis) { GitSync.sync(repo.root, settings) }
    }

    private fun afterEdit() {
        refresh()
        if (settings.syncAfterEdit && settings.remoteUrl.isNotBlank()) platform.syncSoon()
    }

    // ---- notes ---------------------------------------------------------------------------

    /** Creates and opens a note; from the template at [template] (in .foam/templates) if given. */
    fun createNote(title: String, template: String? = null) {
        viewModelScope.launch {
            val path = withContext(ioDispatcher) { repo.createNoteFromTitle(title, template) }
            afterEdit()
            openPath(path, startEditing = true)
        }
    }

    /** Set to ask the note list to show its New note dialog; it clears this when it does. */
    val newNoteRequested = mutableStateOf(false)

    /** The New note dialog, from anywhere (the desktop app's Cmd+N): back to the note list first. */
    fun requestNewNote() {
        val list = backStack.indexOf(Screen.Notes)
        if (list < 0) return // not set up yet
        while (backStack.lastIndex > list) backStack.removeAt(backStack.lastIndex)
        newNoteRequested.value = true
    }

    fun openDailyNote() {
        if (!settings.isConfigured) return
        viewModelScope.launch {
            val path = withContext(ioDispatcher) { repo.dailyNote() }
            afterEdit()
            openPath(path, startEditing = true)
        }
    }

    fun openWikilink(target: String) {
        viewModelScope.launch {
            val (path, created) = withContext(ioDispatcher) {
                repo.resolve(target)?.let { it to false } ?: (repo.createFromWikilink(target) to true)
            }
            if (created) {
                afterEdit()
                messages.emit("Created $path")
            }
            if (path.endsWith(".md", ignoreCase = true)) openPath(path, startEditing = created)
            else messages.emit("Cannot open $path")
        }
    }

    fun save(path: String, text: String) {
        viewModelScope.launch(ioDispatcher) {
            repo.write(path, text)
            afterEdit()
        }
    }

    fun deleteNote(path: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) { repo.delete(path) }
            if ((current as? Screen.Editor)?.path == path) back()
            afterEdit()
            messages.emit("Deleted $path")
        }
    }

    /**
     * Renames/moves [oldPath] to [newPath] and fixes links. [pendingText] is unsaved editor
     * text for the note, written first so nothing is lost.
     */
    fun relocateNote(oldPath: String, newPath: String, pendingText: String? = null, keepEditing: Boolean = false) {
        viewModelScope.launch {
            val result = runCatching {
                GitSync.exclusive {
                    if (pendingText != null) repo.write(oldPath, pendingText)
                    repo.relocate(oldPath, newPath)
                }
            }
            result.onSuccess { r ->
                for (i in backStack.indices) {
                    val s = backStack[i]
                    if (s is Screen.Editor && s.path == oldPath) backStack[i] = Screen.Editor(r.path, keepEditing)
                }
                afterEdit()
                messages.emit(
                    if (r.links == 0) "Now ${r.path}"
                    else "Now ${r.path} · updated ${r.links} link(s) in ${r.notes} note(s)"
                )
            }.onFailure { messages.emit(it.message ?: "Couldn't move $oldPath") }
        }
    }

    // ---- templates -----------------------------------------------------------------------

    /** Creates Foam's default template [file] (new-note.md or daily-note.md) and opens it. */
    fun createDefaultTemplate(file: String) {
        viewModelScope.launch {
            val path = withContext(ioDispatcher) { repo.createDefaultTemplate(file) }
            afterEdit()
            openPath(path, startEditing = true)
        }
    }

    /** Creates a template named [name] for Create note from template, and opens it. */
    fun createTemplate(name: String) {
        viewModelScope.launch {
            val path = withContext(ioDispatcher) { repo.createTemplate(name) }
            afterEdit()
            openPath(path, startEditing = true)
        }
    }

    /** Renames the template at [path] to the file name [newName]; [onDone] gets whether it worked. */
    fun renameTemplate(path: String, newName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { runCatching { GitSync.exclusive { repo.renameTemplate(path, newName) } } }
            val target = result.getOrNull()
            if (target == null) {
                messages.emit(result.exceptionOrNull()?.message ?: "Can't rename it to that: the name is taken, or belongs to Foam's new-note or daily-note template")
            } else {
                afterEdit()
                if (target != path) messages.emit("Now ${target.substringAfterLast('/')}")
            }
            onDone()
        }
    }

    /** Deletes the template at [path], leaving its editor if it's open. */
    fun deleteTemplate(path: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(ioDispatcher) { repo.deleteTemplate(path) }
            if ((current as? Screen.Editor)?.path == path) back()
            afterEdit()
            messages.emit("Deleted ${path.substringAfterLast('/')}")
            onDone()
        }
    }

    // ---- spelling --------------------------------------------------------------------------

    /** The downloaded spelling dictionaries, kept beside the repository in the app's storage. */
    val dictionaries = Dictionaries(platform.fs, (platform.repoDir.parent ?: platform.repoDir) / "dictionaries")

    private val _speller = MutableStateFlow<SpellChecker?>(null)

    /** A spell checker with the downloaded dictionaries; null until loaded, or with none. */
    val speller: StateFlow<SpellChecker?> = _speller
    private var spellerLoaded = false

    /** Loads the dictionaries for [speller], once; again with [reload] (after a download). */
    fun loadSpeller(reload: Boolean = false) {
        if (spellerLoaded && !reload) return
        spellerLoaded = true
        viewModelScope.launch {
            _speller.value = withContext(ioDispatcher) { SpellChecker(dictionaries.load()).takeIf { !it.isEmpty } }
        }
    }

    private val _languages = MutableStateFlow<List<InstalledDictionary>>(emptyList())

    /** The downloaded languages. */
    val languages: StateFlow<List<InstalledDictionary>> = _languages

    fun refreshLanguages() {
        viewModelScope.launch { _languages.value = withContext(ioDispatcher) { dictionaries.languages() } }
    }

    /** Downloads [info] (and the extras if needed); [onDone] runs when it's finished or failed. */
    fun installLanguage(info: DictionaryInfo, manifest: DictionaryManifest, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(ioDispatcher) { runCatching { dictionaries.install(info, manifest) } }
                .onFailure { messages.emit("Couldn't download ${info.name}: ${it.message ?: it::class.simpleName}") }
            afterDictionaryChange()
            onDone()
        }
    }

    private var defaultDictionaryTried = false
    private var defaultDictionaryBusy = false

    /**
     * With spelling on but no language downloaded, downloads one for the device's language,
     * with the extras: when Settings is saved ([fromSave]), and otherwise once per run (when a
     * note opens), so a download that failed offline is tried again without nagging.
     */
    fun downloadDefaultDictionary(fromSave: Boolean = false) {
        if (!settings.spellEnabled || defaultDictionaryBusy || (defaultDictionaryTried && !fromSave)) return
        defaultDictionaryBusy = true
        defaultDictionaryTried = true
        viewModelScope.launch {
            try {
                if (withContext(ioDispatcher) { dictionaries.languages() }.isNotEmpty()) return@launch
                val locale = Locale.current
                val result = withContext(ioDispatcher) {
                    runCatching {
                        val manifest = dictionaries.manifest()
                        val info = Dictionaries.pick(manifest, locale.language, locale.region)
                            ?: error("no languages are offered")
                        val names = listOfNotNull(info.name, manifest.extras?.name).joinToString(" and ")
                        messages.emit("Downloading $names for spelling\u2026")
                        dictionaries.install(info, manifest)
                        names
                    }
                }
                result.onSuccess { messages.emit("Downloaded $it for spelling") }.onFailure {
                    messages.emit(
                        "Couldn't download a spelling dictionary (${it.message ?: it::class.simpleName}). " +
                            "Choose one under Settings > Editing > Languages.",
                    )
                }
                afterDictionaryChange()
            } finally {
                defaultDictionaryBusy = false
            }
        }
    }

    fun removeLanguage(code: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) { runCatching { dictionaries.remove(code) } }
            afterDictionaryChange()
        }
    }

    private suspend fun afterDictionaryChange() {
        _languages.value = withContext(ioDispatcher) { dictionaries.languages() }
        if (spellerLoaded) loadSpeller(reload = true)
    }

    /**
     * Adds [word] to the repository's dictionary (cspell.json, or .vscode/settings.json), so
     * VS Code accepts it too; [onDone] runs once it's written.
     */
    fun addToDictionary(word: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { GitSync.exclusive { CSpellConfig.addWord(platform.fs, repo.root, word) } }
            }
            result.onSuccess { file ->
                afterEdit()
                messages.emit("Added \u201c$word\u201d to $file")
            }.onFailure { messages.emit("Couldn't add \u201c$word\u201d: ${it.message ?: it::class.simpleName}") }
            onDone()
        }
    }

    /** Links [path] from the index file, so it's no longer an orphan. */
    fun addToIndex(path: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) { repo.addToIndex(path) }
            afterEdit()
            messages.emit("Linked from ${settings.indexFile}")
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            val n = withContext(ioDispatcher) { repo.rebuildIndex() }
            afterEdit()
            messages.emit(if (n == 0) "${settings.indexFile} is up to date" else "Added $n note(s) to ${settings.indexFile}")
        }
    }
}

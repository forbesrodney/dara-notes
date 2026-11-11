package app.daranotes

import app.daranotes.core.ioDispatcher
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

    fun createNote(title: String) {
        viewModelScope.launch {
            val path = withContext(ioDispatcher) { repo.createNoteFromTitle(title) }
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

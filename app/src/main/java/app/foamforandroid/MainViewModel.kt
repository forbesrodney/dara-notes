package app.foamforandroid

import androidx.compose.foundation.lazy.LazyListState
import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Notes : Screen
    data class Editor(val path: String, val startEditing: Boolean = false) : Screen
    data object Settings : Screen
    /** The note graph, optionally centred on [focus] (a note path). */
    data class Graph(val focus: String? = null) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    val repo = NoteRepository(app, settings)
    val syncState: StateFlow<SyncState> = GitSync.state

    val backStack = mutableStateListOf<Screen>(if (settings.isConfigured) Screen.Notes else Screen.Settings)
    val current: Screen get() = backStack.last()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    /** The note list's scroll position, kept while you open notes and come back. */
    val notesListState = LazyListState()

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
        viewModelScope.launch(Dispatchers.IO) { _notes.value = repo.listNotes() }
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
            GitSync.sync(getApplication(), settings)
                .onSuccess { messages.emit(it) }
                .onFailure { messages.emit("Sync failed: ${GitErrors.hint(it) ?: it.message}") }
        }
    }

    /** Called when the app comes to the foreground; pulls changes made elsewhere. */
    fun onForeground() {
        val last = syncState.value.lastSyncAt
        if (settings.isConfigured && !syncState.value.running && System.currentTimeMillis() - last > 60_000) {
            syncNow()
        }
    }

    fun cloneRepository(onDone: () -> Unit) {
        viewModelScope.launch {
            GitSync.cloneFresh(getApplication(), settings)
                .onSuccess {
                    SyncScheduler.schedulePeriodic(getApplication(), settings)
                    messages.emit("Repository ready")
                    backStack.clear()
                    backStack.add(Screen.Notes)
                    onDone()
                }
                .onFailure { messages.emit("Clone failed: ${GitErrors.hint(it) ?: it.message}") }
        }
    }

    fun applySyncSchedule() = SyncScheduler.schedulePeriodic(getApplication(), settings)

    private fun afterEdit() {
        refresh()
        if (settings.syncAfterEdit && settings.remoteUrl.isNotBlank()) SyncScheduler.syncSoon(getApplication())
    }

    // ---- notes ---------------------------------------------------------------------------

    fun createNote(title: String) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { repo.createNoteFromTitle(title) }
            afterEdit()
            openPath(path, startEditing = true)
        }
    }

    fun openDailyNote() {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { repo.dailyNote() }
            afterEdit()
            openPath(path, startEditing = true)
        }
    }

    fun openWikilink(target: String) {
        viewModelScope.launch {
            val (path, created) = withContext(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            repo.write(path, text)
            afterEdit()
        }
    }

    fun deleteNote(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(path) }
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

    fun rebuildIndex() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { repo.rebuildIndex() }
            afterEdit()
            messages.emit(if (n == 0) "${settings.indexFile} is up to date" else "Added $n note(s) to ${settings.indexFile}")
        }
    }
}

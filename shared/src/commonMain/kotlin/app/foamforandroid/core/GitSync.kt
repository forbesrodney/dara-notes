package app.foamforandroid.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path

data class SyncState(
    val running: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastMessage: String? = null,
    val lastError: String? = null,
    /** Plain-language explanation of [lastError], when it is a common one. */
    val lastHint: String? = null,
    /** Files that were committed with conflict markers during the last sync. */
    val conflicts: List<String> = emptyList(),
    /** Increments whenever a sync may have changed files on disk. */
    val generation: Int = 0,
)

/** What a sync did: a summary for the user and any files committed with conflict markers. */
data class SyncResult(val message: String, val conflicts: List<String> = emptyList())

/** The Git operations each platform provides (JGit on the JVM). Calls block. */
interface GitEngine {
    /** Replaces [dir] with a clone of the remote, or a new empty repository when none is set. */
    fun cloneFresh(dir: Path, s: Settings)

    /**
     * Stages everything, commits, fetches, merges (fast-forward when possible) and pushes.
     * Conflicting files are committed with conflict markers and listed in the result.
     */
    fun sync(dir: Path, s: Settings): SyncResult
}

/**
 * Git synchronization, one operation at a time. Merge conflicts never lose data: conflicting
 * files are committed with conflict markers and reported so the user can tidy them up.
 */
object GitSync {
    /** Names this app in commit messages and the default author: "Android", "macOS", ... */
    var clientName: String = "Android"

    /** The Git implementation; replaceable in tests. */
    var engine: GitEngine = platformGitEngine()

    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    /** Runs [block] while no sync is touching the working tree (e.g. rewriting many files). */
    suspend fun <T> exclusive(block: () -> T): T = withContext(ioDispatcher) { mutex.withLock { block() } }

    /** Clone the remote (or init a local repo when no remote is set), replacing any local copy. */
    suspend fun cloneFresh(dir: Path, s: Settings): Result<String> = run {
        s.cloned = false
        engine.cloneFresh(dir, s)
        s.cloned = true
        doSync(dir, s)
    }

    suspend fun sync(dir: Path, s: Settings): Result<String> {
        if (!s.isConfigured) return Result.failure(IllegalStateException("Repository not set up"))
        return run { doSync(dir, s) }
    }

    private fun doSync(dir: Path, s: Settings): String {
        val result = engine.sync(dir, s)
        _state.update { it.copy(conflicts = result.conflicts) }
        return result.message
    }

    private suspend fun run(block: () -> String): Result<String> =
        withContext(ioDispatcher) {
            mutex.withLock {
                _state.update { it.copy(running = true, lastError = null, lastHint = null) }
                val result = runCatching(block)
                _state.update {
                    it.copy(
                        running = false,
                        lastSyncAt = if (result.isSuccess) Dates.nowMillis() else it.lastSyncAt,
                        lastMessage = result.getOrNull() ?: it.lastMessage,
                        lastError = result.exceptionOrNull()?.let(::describe),
                        lastHint = result.exceptionOrNull()?.let(GitErrors::hint),
                        generation = it.generation + 1,
                    )
                }
                result
            }
        }

    private fun describe(t: Throwable): String {
        var e: Throwable = t
        val parts = mutableListOf<String>()
        while (true) {
            e.message?.let { if (it !in parts) parts += it }
            e = e.cause ?: break
        }
        return parts.joinToString(": ").ifBlank { t::class.simpleName ?: "Error" }
    }
}

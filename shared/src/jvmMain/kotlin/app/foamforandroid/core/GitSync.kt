package app.foamforandroid.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

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

/**
 * Git synchronization: stage everything, commit, fetch, merge (fast-forward when possible),
 * push. Merge conflicts never lose data: conflicting files are committed with conflict
 * markers and reported so the user can tidy them up in the editor.
 */
object GitSync {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state


    /** Runs [block] while no sync is touching the working tree (e.g. rewriting many files). */
    suspend fun <T> exclusive(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    /** Clone the remote (or init a local repo when no remote is set), replacing any local copy. */
    suspend fun cloneFresh(dir: File, s: Settings): Result<String> = run {
        dir.deleteRecursively()
        s.cloned = false
        if (s.remoteUrl.isBlank()) {
            Git.init().setDirectory(dir).setInitialBranch(s.branch).call().close()
        } else {
            Git.cloneRepository()
                .setURI(s.remoteUrl.trim())
                .setDirectory(dir)
                .setBranch(s.branch)
                .setCredentialsProvider(credentials(s))
                .setTimeout(60)
                .call()
                .close()
        }
        s.cloned = true
        doSync(dir, s)
    }

    suspend fun sync(dir: File, s: Settings): Result<String> {
        if (!s.isConfigured) return Result.failure(IllegalStateException("Repository not set up"))
        return run { doSync(dir, s) }
    }

    private suspend fun run(block: () -> String): Result<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _state.update { it.copy(running = true, lastError = null, lastHint = null) }
                val result = runCatching(block)
                _state.update {
                    it.copy(
                        running = false,
                        lastSyncAt = if (result.isSuccess) System.currentTimeMillis() else it.lastSyncAt,
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
        return parts.joinToString(": ").ifBlank { t.javaClass.simpleName }
    }

    private fun credentials(s: Settings) =
        if (s.token.isNotBlank()) UsernamePasswordCredentialsProvider(s.username.ifBlank { "git" }, s.token)
        else null

    private fun ident(s: Settings) = PersonIdent(
        s.authorName.ifBlank { "Foam Notes (Android)" },
        s.authorEmail.ifBlank { "foam-notes@android.local" },
    )

    private fun doSync(dir: File, s: Settings): String {
        if (!File(dir, ".git").exists()) {
            Git.init().setDirectory(dir).setInitialBranch(s.branch).call().close()
        }
        Git.open(dir).use { git ->
            val repo = git.repository
            val config = repo.config
            config.setString("user", null, "name", ident(s).name)
            config.setString("user", null, "email", ident(s).emailAddress)
            val hasRemote = s.remoteUrl.isNotBlank()
            if (hasRemote) {
                config.setString("remote", "origin", "url", s.remoteUrl.trim())
                config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            }
            config.save()

            val committed = commitAll(git, s)
            if (!hasRemote) {
                _state.update { it.copy(conflicts = emptyList()) }
                return if (committed > 0) "Committed $committed file(s) locally" else "No changes"
            }

            var conflicts: List<String> = emptyList()
            var pushed = false
            for (attempt in 1..2) {
                git.fetch().setRemote("origin").setCredentialsProvider(credentials(s)).setTimeout(60).call()
                val remoteRef = repo.exactRef("refs/remotes/origin/${s.branch}")
                val head = repo.resolve(Constants.HEAD)
                if (remoteRef != null) {
                    if (head == null) {
                        // Nothing local yet: adopt the remote branch.
                        val ru = repo.updateRef("refs/heads/${s.branch}")
                        ru.setNewObjectId(remoteRef.objectId)
                        ru.update()
                        repo.updateRef(Constants.HEAD).link("refs/heads/${s.branch}")
                        git.reset().setMode(ResetCommand.ResetType.HARD).call()
                    } else {
                        val merge = git.merge()
                            .include(remoteRef)
                            .setStrategy(MergeStrategy.RECURSIVE)
                            .setFastForward(MergeCommand.FastForwardMode.FF)
                            .setMessage("Merge remote changes into Android notes")
                            .call()
                        when (merge.mergeStatus) {
                            MergeResult.MergeStatus.CONFLICTING -> {
                                conflicts = merge.conflicts?.keys?.toList().orEmpty()
                                git.add().addFilepattern(".").call()
                                git.commit()
                                    .setMessage("Merge with conflicts (resolve markers in: ${conflicts.joinToString()})")
                                    .setAuthor(ident(s)).setCommitter(ident(s))
                                    .call()
                            }
                            MergeResult.MergeStatus.FAILED,
                            MergeResult.MergeStatus.ABORTED,
                            MergeResult.MergeStatus.NOT_SUPPORTED,
                            MergeResult.MergeStatus.CHECKOUT_CONFLICT ->
                                error("Merge failed (${merge.mergeStatus}): ${merge.failingPaths?.keys ?: merge.checkoutConflicts}")
                            else -> Unit
                        }
                    }
                }

                val localHead = repo.resolve(Constants.HEAD) ?: break // empty repo, nothing to push
                if (remoteRef != null && repo.resolve("refs/remotes/origin/${s.branch}") == localHead) {
                    pushed = true // already in sync
                    break
                }
                val results = git.push()
                    .setRemote("origin")
                    .setRefSpecs(RefSpec("HEAD:refs/heads/${s.branch}"))
                    .setCredentialsProvider(credentials(s))
                    .setTimeout(60)
                    .call()
                val updates = results.flatMap { it.remoteUpdates }
                val rejected = updates.filter {
                    it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE
                }
                if (rejected.isEmpty()) {
                    pushed = true
                    break
                }
                val retryable = rejected.all {
                    it.status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ||
                        it.status == RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED
                }
                if (!retryable || attempt == 2) {
                    error("Push rejected: " + rejected.joinToString { "${it.status} ${it.message ?: ""}".trim() })
                }
            }
            _state.update { it.copy(conflicts = conflicts) }
            return buildString {
                append(if (committed > 0) "Committed $committed file(s)" else "No local changes")
                if (pushed) append(", synced")
                if (conflicts.isNotEmpty()) append(" — conflicts in ${conflicts.joinToString()}")
            }
        }
    }

    /** Stages additions, modifications and deletions and commits them. Returns files changed. */
    private fun commitAll(git: Git, s: Settings): Int {
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
        val status = git.status().call()
        val changed = (status.added + status.changed + status.removed).sorted()
        if (changed.isEmpty()) return 0
        val summary = if (changed.size <= 3) changed.joinToString() else "${changed.size} files"
        git.commit()
            .setMessage("Update $summary from Android")
            .setAuthor(ident(s))
            .setCommitter(ident(s))
            .call()
        return changed.size
    }
}

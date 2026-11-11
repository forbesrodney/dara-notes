package app.foamforandroid.core

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
import okio.Path
import java.io.File

/** Git for the JVM (Android and desktop), using JGit. */
internal object JGitEngine : GitEngine {
    override fun cloneFresh(dir: Path, s: Settings) {
        val d = dir.toFile()
        d.deleteRecursively()
        if (s.remoteUrl.isBlank()) {
            Git.init().setDirectory(d).setInitialBranch(s.branch).call().close()
        } else {
            Git.cloneRepository()
                .setURI(s.remoteUrl.trim())
                .setDirectory(d)
                .setBranch(s.branch)
                .setCredentialsProvider(credentials(s))
                .setTimeout(60)
                .call()
                .close()
        }
    }

    override fun sync(dir: Path, s: Settings): SyncResult = doSync(dir.toFile(), s)

    private fun credentials(s: Settings) =
        if (s.token.isNotBlank()) UsernamePasswordCredentialsProvider(s.username.ifBlank { "git" }, s.token)
        else null

    private fun ident(s: Settings) = PersonIdent(
        s.authorName.ifBlank { "Dara Notes (${GitSync.clientName})" },
        s.authorEmail.ifBlank { "dara-notes@${GitSync.clientName.lowercase()}.local" },
    )

    private fun doSync(dir: File, s: Settings): SyncResult {
        if (!File(dir, ".git").exists()) {
            Git.init().setDirectory(dir).setInitialBranch(s.branch).call().close()
        }
        Git.open(dir).use { git ->
            val repo = git.repository
            val config = repo.config
            config.setString("user", null, "name", ident(s).name)
            config.setString("user", null, "email", ident(s).emailAddress)
            // JGit reads your global Git config too; commit.gpgsign = true there would make every
            // sync commit fail ("Signing service is not available"), as the app can't sign.
            config.setBoolean("commit", null, "gpgsign", false)
            val hasRemote = s.remoteUrl.isNotBlank()
            if (hasRemote) {
                config.setString("remote", "origin", "url", s.remoteUrl.trim())
                config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            }
            config.save()

            val committed = commitAll(git, s)
            if (!hasRemote) {
                return SyncResult(if (committed > 0) "Committed $committed file(s) locally" else "No changes")
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
                            .setMessage("Merge remote changes into ${GitSync.clientName} notes")
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
            val message = buildString {
                append(if (committed > 0) "Committed $committed file(s)" else "No local changes")
                if (pushed) append(", synced")
                if (conflicts.isNotEmpty()) append(" — conflicts in ${conflicts.joinToString()}")
            }
            return SyncResult(message, conflicts)
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
            .setMessage("Update $summary from ${GitSync.clientName}")
            .setAuthor(ident(s))
            .setCommitter(ident(s))
            .call()
        return changed.size
    }
}

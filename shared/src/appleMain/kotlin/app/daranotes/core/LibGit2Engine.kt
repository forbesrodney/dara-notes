package app.daranotes.core

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import cnames.structs.git_annotated_commit
import cnames.structs.git_commit
import cnames.structs.git_config
import cnames.structs.git_index
import cnames.structs.git_index_conflict_iterator
import cnames.structs.git_object
import cnames.structs.git_reference
import cnames.structs.git_remote
import cnames.structs.git_repository
import cnames.structs.git_status_list
import cnames.structs.git_tree
import libgit2.*
import okio.FileSystem
import okio.Path

/**
 * Git for iOS (and native macOS), using libgit2. Does the same as the JVM's JGit engine:
 * commit everything, fetch, fast-forward or merge (committing conflicts with their markers),
 * push, and retry once if the remote moved meanwhile.
 */
internal object LibGit2Engine : GitEngine {
    init {
        git_libgit2_init()
    }

    override fun cloneFresh(dir: Path, s: Settings) {
        FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false)
        FileSystem.SYSTEM.createDirectories(dir.parent ?: dir)
        memScoped {
            val repo = alloc<CPointerVar<git_repository>>()
            if (s.remoteUrl.isBlank()) {
                initRepo(repo, dir, s)
            } else {
                val auth = Auth(s)
                val ref = StableRef.create(auth)
                try {
                    val opts = alloc<git_clone_options>()
                    check(git_clone_options_init(opts.ptr, GIT_CLONE_OPTIONS_VERSION.toUInt()))
                    opts.checkout_branch = s.branch.cstr.ptr
                    setCallbacks(opts.fetch_opts.callbacks, ref.asCPointer())
                    check(git_clone(repo.ptr, s.remoteUrl.trim(), dir.toString(), opts.ptr), auth)
                } finally {
                    ref.dispose()
                }
            }
            git_repository_free(repo.value)
        }
    }

    override fun sync(dir: Path, s: Settings): SyncResult = memScoped {
        val repoVar = alloc<CPointerVar<git_repository>>()
        if (!FileSystem.SYSTEM.exists(dir / ".git")) initRepo(repoVar, dir, s)
        else check(git_repository_open(repoVar.ptr, dir.toString()))
        val repo = repoVar.value!!
        val auth = Auth(s)
        val ref = StableRef.create(auth)
        try {
            configure(repo, s)
            val committed = commitAll(repo, s)
            if (s.remoteUrl.isBlank()) {
                return@memScoped SyncResult(if (committed > 0) "Committed $committed file(s) locally" else "No changes")
            }
            val remote = alloc<CPointerVar<git_remote>>()
            check(git_remote_lookup(remote.ptr, repo, "origin"))
            try {
                var conflicts: List<String> = emptyList()
                var pushed = false
                for (attempt in 1..2) {
                    auth.attempts = 0 // each request (fetch, push) asks for the sign-in afresh
                    fetch(remote.value!!, ref.asCPointer(), auth)
                    val remoteId = oidOf(repo, "refs/remotes/origin/${s.branch}")
                    val headId = oidOf(repo, "HEAD")
                    if (remoteId != null) {
                        if (headId == null) adoptRemote(repo, remoteId, s)
                        else merge(repo, remoteId, s)?.let { conflicts = it }
                    }
                    val localHead = oidOf(repo, "HEAD") ?: break // empty repository: nothing to push
                    if (remoteId != null && oidOf(repo, "refs/remotes/origin/${s.branch}")?.same(localHead) == true) {
                        pushed = true // already in sync
                        break
                    }
                    auth.rejected = null
                    auth.attempts = 0
                    val rc = push(remote.value!!, s.branch, ref.asCPointer())
                    val rejected = auth.rejected
                    if (rc == 0 && rejected == null) {
                        pushed = true
                        break
                    }
                    val retryable = rc == GIT_ENONFASTFORWARD ||
                        rejected?.let { "fast-forward" in it || "fetch first" in it || "stale" in it } == true
                    if (!retryable || attempt == 2) {
                        throw IllegalStateException("Push rejected: " + (rejected ?: lastError(rc, auth)))
                    }
                }
                val message = buildString {
                    append(if (committed > 0) "Committed $committed file(s)" else "No local changes")
                    if (pushed) append(", synced")
                    if (conflicts.isNotEmpty()) append(" — conflicts in ${conflicts.joinToString()}")
                }
                SyncResult(message, conflicts)
            } finally {
                git_remote_free(remote.value)
            }
        } finally {
            ref.dispose()
            git_repository_free(repo)
        }
    }

    // ---- setup -------------------------------------------------------------------------

    private fun MemScope.initRepo(repo: CPointerVar<git_repository>, dir: Path, s: Settings) {
        val opts = alloc<git_repository_init_options>()
        check(git_repository_init_options_init(opts.ptr, GIT_REPOSITORY_INIT_OPTIONS_VERSION.toUInt()))
        opts.flags = GIT_REPOSITORY_INIT_MKPATH
        opts.initial_head = s.branch.cstr.ptr
        check(git_repository_init_ext(repo.ptr, dir.toString(), opts.ptr))
    }

    private fun MemScope.configure(repo: CPointer<git_repository>, s: Settings) {
        val config = alloc<CPointerVar<git_config>>()
        check(git_repository_config(config.ptr, repo))
        try {
            check(git_config_set_string(config.value, "user.name", authorName(s)))
            check(git_config_set_string(config.value, "user.email", authorEmail(s)))
        } finally {
            git_config_free(config.value)
        }
        if (s.remoteUrl.isNotBlank()) {
            val remote = alloc<CPointerVar<git_remote>>()
            if (git_remote_lookup(remote.ptr, repo, "origin") == 0) {
                git_remote_free(remote.value)
                check(git_remote_set_url(repo, "origin", s.remoteUrl.trim()))
            } else {
                check(git_remote_create(remote.ptr, repo, "origin", s.remoteUrl.trim()))
                git_remote_free(remote.value)
            }
        }
    }

    private fun authorName(s: Settings) = s.authorName.ifBlank { "Dara Notes (${GitSync.clientName})" }
    private fun authorEmail(s: Settings) = s.authorEmail.ifBlank { "dara-notes@${GitSync.clientName.lowercase()}.local" }

    private fun MemScope.signature(s: Settings): CPointer<git_signature> {
        val sig = alloc<CPointerVar<git_signature>>()
        check(git_signature_now(sig.ptr, authorName(s), authorEmail(s)))
        return sig.value!!
    }

    // ---- commit ------------------------------------------------------------------------

    /** Stages additions, modifications and deletions and commits them. Returns files changed. */
    private fun MemScope.commitAll(repo: CPointer<git_repository>, s: Settings): Int {
        val index = alloc<CPointerVar<git_index>>()
        check(git_repository_index(index.ptr, repo))
        try {
            val everything = strArray(listOf("*"))
            check(git_index_add_all(index.value, everything.ptr, GIT_INDEX_ADD_DEFAULT, null, null))
            check(git_index_update_all(index.value, everything.ptr, null, null))
            check(git_index_write(index.value))
            val changed = stagedPaths(repo)
            if (changed.isEmpty()) return 0
            val summary = if (changed.size <= 3) changed.joinToString() else "${changed.size} files"
            commit(repo, index.value!!, "Update $summary from ${GitSync.clientName}", s, extraParent = null)
            return changed.size
        } finally {
            git_index_free(index.value)
        }
    }

    /** Paths whose staged version differs from HEAD. */
    private fun MemScope.stagedPaths(repo: CPointer<git_repository>): List<String> {
        val opts = alloc<git_status_options>()
        check(git_status_options_init(opts.ptr, GIT_STATUS_OPTIONS_VERSION.toUInt()))
        opts.show = GIT_STATUS_SHOW_INDEX_ONLY
        val list = alloc<CPointerVar<git_status_list>>()
        check(git_status_list_new(list.ptr, repo, opts.ptr))
        try {
            val n = git_status_list_entrycount(list.value).toInt()
            return (0 until n).mapNotNull { i ->
                val delta = git_status_byindex(list.value, i.toULong())?.pointed?.head_to_index?.pointed
                    ?: return@mapNotNull null
                (delta.new_file.path ?: delta.old_file.path)?.toKString()
            }.sorted()
        } finally {
            git_status_list_free(list.value)
        }
    }

    /** Commits the index on HEAD (and [extraParent], for a merge). */
    private fun MemScope.commit(
        repo: CPointer<git_repository>,
        index: CPointer<git_index>,
        message: String,
        s: Settings,
        extraParent: git_oid?,
    ) {
        val treeId = alloc<git_oid>()
        check(git_index_write_tree(treeId.ptr, index))
        val tree = alloc<CPointerVar<git_tree>>()
        check(git_tree_lookup(tree.ptr, repo, treeId.ptr))
        val parents = mutableListOf<CPointer<git_commit>>()
        try {
            oidOf(repo, "HEAD")?.let { parents += lookupCommit(repo, it) }
            extraParent?.let { parents += lookupCommit(repo, it) }
            val sig = signature(s)
            val parentArray = allocArray<CPointerVar<git_commit>>(parents.size.coerceAtLeast(1))
            parents.forEachIndexed { i, c -> parentArray[i] = c }
            val id = alloc<git_oid>()
            check(
                git_commit_create(
                    id.ptr, repo, "HEAD", sig, sig, null, message, tree.value,
                    parents.size.toULong(), parentArray,
                ),
            )
            git_signature_free(sig)
        } finally {
            parents.forEach { git_commit_free(it) }
            git_tree_free(tree.value)
        }
    }

    private fun MemScope.lookupCommit(repo: CPointer<git_repository>, id: git_oid): CPointer<git_commit> {
        val c = alloc<CPointerVar<git_commit>>()
        check(git_commit_lookup(c.ptr, repo, id.ptr))
        return c.value!!
    }

    // ---- fetch / merge -----------------------------------------------------------------

    private fun MemScope.fetch(remote: CPointer<git_remote>, payload: COpaquePointer, auth: Auth) {
        val opts = alloc<git_fetch_options>()
        check(git_fetch_options_init(opts.ptr, GIT_FETCH_OPTIONS_VERSION.toUInt()))
        setCallbacks(opts.callbacks, payload)
        check(git_remote_fetch(remote, null, opts.ptr, null), auth)
    }

    /** First sync of an empty local repository: take the remote branch as it is. */
    private fun MemScope.adoptRemote(repo: CPointer<git_repository>, remoteId: git_oid, s: Settings) {
        val branch = alloc<CPointerVar<git_reference>>()
        check(git_reference_create(branch.ptr, repo, "refs/heads/${s.branch}", remoteId.ptr, 1, "Adopt remote branch"))
        git_reference_free(branch.value)
        check(git_repository_set_head(repo, "refs/heads/${s.branch}"))
        hardReset(repo, remoteId)
    }

    /** Moves the branch and working tree to [id] (used for fast-forwards; the tree is clean). */
    private fun MemScope.hardReset(repo: CPointer<git_repository>, id: git_oid) {
        val target = alloc<CPointerVar<git_object>>()
        check(git_object_lookup(target.ptr, repo, id.ptr, GIT_OBJECT_COMMIT))
        try {
            check(git_reset(repo, target.value, GIT_RESET_HARD, null))
        } finally {
            git_object_free(target.value)
        }
    }

    /**
     * Brings in the remote branch at [remoteId]: fast-forward if possible, else a merge commit.
     * Returns the conflicting files (committed with conflict markers), or null if there were none.
     */
    private fun MemScope.merge(repo: CPointer<git_repository>, remoteId: git_oid, s: Settings): List<String>? {
        val head = alloc<CPointerVar<git_annotated_commit>>()
        check(git_annotated_commit_lookup(head.ptr, repo, remoteId.ptr))
        try {
            val heads = allocArrayOf(head.value)
            val analysis = alloc<git_merge_analysis_tVar>()
            val preference = alloc<git_merge_preference_tVar>()
            check(git_merge_analysis(analysis.ptr, preference.ptr, repo, heads, 1u))
            val a = analysis.value.toInt()
            when {
                a and GIT_MERGE_ANALYSIS_UP_TO_DATE.toInt() != 0 -> return null
                a and GIT_MERGE_ANALYSIS_FASTFORWARD.toInt() != 0 -> {
                    hardReset(repo, remoteId)
                    return null
                }
            }
            val mergeOpts = alloc<git_merge_options>()
            check(git_merge_options_init(mergeOpts.ptr, GIT_MERGE_OPTIONS_VERSION.toUInt()))
            val checkoutOpts = alloc<git_checkout_options>()
            check(git_checkout_options_init(checkoutOpts.ptr, GIT_CHECKOUT_OPTIONS_VERSION.toUInt()))
            checkoutOpts.checkout_strategy = GIT_CHECKOUT_SAFE or GIT_CHECKOUT_ALLOW_CONFLICTS
            check(git_merge(repo, heads, 1u, mergeOpts.ptr, checkoutOpts.ptr))

            val index = alloc<CPointerVar<git_index>>()
            check(git_repository_index(index.ptr, repo))
            try {
                val conflicts = conflictedPaths(index.value!!)
                // Keep each conflicting file as it is now, with its conflict markers.
                val everything = strArray(listOf("*"))
                check(git_index_add_all(index.value, everything.ptr, GIT_INDEX_ADD_DEFAULT, null, null))
                check(git_index_write(index.value))
                val message = if (conflicts.isEmpty()) "Merge remote changes into ${GitSync.clientName} notes"
                else "Merge with conflicts (resolve markers in: ${conflicts.joinToString()})"
                commit(repo, index.value!!, message, s, extraParent = remoteId)
                return conflicts.ifEmpty { null }
            } finally {
                git_index_free(index.value)
                git_repository_state_cleanup(repo)
            }
        } finally {
            git_annotated_commit_free(head.value)
        }
    }

    private fun MemScope.conflictedPaths(index: CPointer<git_index>): List<String> {
        if (git_index_has_conflicts(index) == 0) return emptyList()
        val it = alloc<CPointerVar<git_index_conflict_iterator>>()
        check(git_index_conflict_iterator_new(it.ptr, index))
        val paths = mutableSetOf<String>()
        try {
            val ancestor = alloc<CPointerVar<git_index_entry>>()
            val ours = alloc<CPointerVar<git_index_entry>>()
            val theirs = alloc<CPointerVar<git_index_entry>>()
            while (git_index_conflict_next(ancestor.ptr, ours.ptr, theirs.ptr, it.value) == 0) {
                (ours.value ?: theirs.value ?: ancestor.value)?.pointed?.path?.toKString()?.let { p -> paths += p }
            }
        } finally {
            git_index_conflict_iterator_free(it.value)
        }
        return paths.sorted()
    }

    // ---- push --------------------------------------------------------------------------

    /** Pushes the branch. Returns libgit2's result; a server rejection lands in Auth.rejected. */
    private fun MemScope.push(remote: CPointer<git_remote>, branch: String, payload: COpaquePointer): Int {
        val opts = alloc<git_push_options>()
        check(git_push_options_init(opts.ptr, GIT_PUSH_OPTIONS_VERSION.toUInt()))
        setCallbacks(opts.callbacks, payload)
        val refspecs = strArray(listOf("refs/heads/$branch:refs/heads/$branch"))
        return git_remote_push(remote, refspecs.ptr, opts.ptr)
    }

    // ---- credentials and errors --------------------------------------------------------

    private fun setCallbacks(callbacks: git_remote_callbacks, payload: COpaquePointer) {
        callbacks.credentials = staticCFunction(::credentials)
        callbacks.push_update_reference = staticCFunction(::pushUpdate)
        callbacks.payload = payload
    }

    private fun MemScope.strArray(items: List<String>): git_strarray {
        val arr = alloc<git_strarray>()
        val strings = allocArray<CPointerVar<ByteVar>>(items.size)
        items.forEachIndexed { i, s -> strings[i] = s.cstr.ptr }
        arr.strings = strings
        arr.count = items.size.toULong()
        return arr
    }

    private fun MemScope.oidOf(repo: CPointer<git_repository>, name: String): git_oid? {
        val id = alloc<git_oid>()
        return if (git_reference_name_to_id(id.ptr, repo, name) == 0) id else null
    }

    private fun git_oid.same(other: git_oid): Boolean = git_oid_equal(ptr, other.ptr) != 0

    private fun lastError(rc: Int, auth: Auth? = null): String {
        val message = git_error_last()?.pointed?.message?.toKString()?.takeIf { it.isNotBlank() }
        return when {
            auth != null && auth.attempts > 1 -> "not authorized: the Git host didn't accept the sign-in"
            message != null -> message
            else -> "libgit2 error $rc"
        }
    }

    private fun check(rc: Int, auth: Auth? = null) {
        if (rc < 0) throw IllegalStateException(lastError(rc, auth))
    }
}

/** Sign-in details for the libgit2 callbacks, and what the server said about a push. */
private class Auth(s: Settings) {
    val username = s.username.ifBlank { "git" }
    val token = s.token
    /** Sign-in requests in the current network request; a second one means the token was refused. */
    var attempts = 0
    var rejected: String? = null
}

/** libgit2 asks for credentials; give the token once (asking again means it was refused). */
private fun credentials(
    out: CPointer<CPointerVar<git_credential>>?,
    url: CPointer<ByteVar>?,
    usernameFromUrl: CPointer<ByteVar>?,
    allowedTypes: UInt,
    payload: COpaquePointer?,
): Int {
    val auth = payload?.asStableRef<Auth>()?.get() ?: return GIT_PASSTHROUGH
    if (auth.token.isEmpty()) return GIT_PASSTHROUGH
    if (++auth.attempts > 1) return GIT_EAUTH
    return git_credential_userpass_plaintext_new(out, auth.username, auth.token)
}

/** Called for each ref a push updates; [status] is the server's rejection, or null. */
private fun pushUpdate(refname: CPointer<ByteVar>?, status: CPointer<ByteVar>?, payload: COpaquePointer?): Int {
    if (status != null) {
        payload?.asStableRef<Auth>()?.get()?.rejected = "${refname?.toKString()}: ${status.toKString()}"
    }
    return 0
}

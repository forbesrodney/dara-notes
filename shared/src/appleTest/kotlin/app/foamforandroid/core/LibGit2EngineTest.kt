package app.foamforandroid.core

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import cnames.structs.git_repository
import kotlinx.cinterop.CPointerVar
import libgit2.git_repository_free
import libgit2.git_repository_init
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Two "devices" syncing through a local bare repository, with libgit2. */
class LibGit2EngineTest {
    private val fs = FileSystem.SYSTEM
    private val root = (NSTemporaryDirectory() + "foam-git-" + NSUUID().UUIDString).toPath()
    private val remote = root / "remote.git"
    private val a = root / "a"
    private val b = root / "b"

    private fun settings(name: String) = object : Settings {
        override var remoteUrl = remote.toString()
        override var branch = "main"
        override var username = ""
        override var token = ""
        override var authorName = name
        override var authorEmail = "$name@example.com"
        override var linkFromIndex = false
        override var indexFile = "Index.md"
        override var indexDailyNotes = false
        override var bullet = "-"
        override var strikeCompleted = false
        override var dateOnComplete = false
        override var dateOrder = "ymd"
        override var dateSeparator = "-"
        override var lintEnabled = false
        override var notesView = "flat"
        override var graphTags = true
        override var graphPlaceholders = true
        override var newNoteDir = ""
        override var dailyDirOverride = ""
        override var autoSyncMinutes = 0
        override var syncAfterEdit = false
        override var cloned = true
    }

    private fun write(dir: Path, name: String, text: String) {
        fs.createDirectories(dir)
        fs.write(dir / name) { writeUtf8(text) }
    }

    private fun read(dir: Path, name: String) = fs.read(dir / name) { readUtf8() }

    private fun initBare() = memScoped {
        libgit2.git_libgit2_init()
        val repo = alloc<CPointerVar<git_repository>>()
        assertEquals(0, git_repository_init(repo.ptr, remote.toString(), 1u))
        git_repository_free(repo.value)
    }

    @AfterTest fun cleanUp() = fs.deleteRecursively(root, mustExist = false)

    @Test fun twoDevicesSyncMergeAndConflict() {
        initBare()
        val sa = settings("A")
        val sb = settings("B")

        // A starts the notes and pushes them.
        write(a, "Index.md", "# Index\n")
        write(a, "one.md", "one\n")
        assertEquals("Committed 2 file(s), synced", LibGit2Engine.sync(a, sa).message)

        // B clones them.
        LibGit2Engine.cloneFresh(b, sb)
        assertEquals("one\n", read(b, "one.md"))

        // Each changes a different file: B pushes, A merges and pushes, B fast-forwards.
        write(b, "two.md", "two\n")
        assertEquals("Committed 1 file(s), synced", LibGit2Engine.sync(b, sb).message)
        write(a, "one.md", "one, edited on A\n")
        assertEquals("Committed 1 file(s), synced", LibGit2Engine.sync(a, sa).message)
        assertEquals("two\n", read(a, "two.md"))
        assertEquals("No local changes, synced", LibGit2Engine.sync(b, sb).message)
        assertEquals("one, edited on A\n", read(b, "one.md"))

        // Deleting a file syncs too.
        fs.delete(b / "two.md")
        assertEquals("Committed 1 file(s), synced", LibGit2Engine.sync(b, sb).message)
        LibGit2Engine.sync(a, sa)
        assertTrue(!fs.exists(a / "two.md"))

        // Both change the same line: B gets a conflict, committed with markers, and pushes it.
        write(a, "one.md", "from A\n")
        LibGit2Engine.sync(a, sa)
        write(b, "one.md", "from B\n")
        val r = LibGit2Engine.sync(b, sb)
        assertEquals(listOf("one.md"), r.conflicts)
        val merged = read(b, "one.md")
        assertTrue("<<<<<<<" in merged && "from A" in merged && "from B" in merged, merged)
        // A then fast-forwards to B's merge and sees the markers too.
        LibGit2Engine.sync(a, sa)
        assertEquals(merged, read(a, "one.md"))
    }

    @Test fun localOnlyRepository() {
        val s = settings("Local").apply { remoteUrl = "" }
        LibGit2Engine.cloneFresh(a, s)
        write(a, "note.md", "hi\n")
        assertEquals("Committed 1 file(s) locally", LibGit2Engine.sync(a, s).message)
        assertEquals("No changes", LibGit2Engine.sync(a, s).message)
    }
}

package app.daranotes.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class TestSettings : Settings {
    override var remoteUrl = ""
    override var branch = "main"
    override var username = ""
    override var token = ""
    override var authorName = ""
    override var authorEmail = ""
    override var linkFromIndex = true
    override var indexFile = "Index.md"
    override var indexDailyNotes = false
    override var bullet = "-"
    override var strikeCompleted = false
    override var dateOnComplete = false
    override var dateOrder = "ymd"
    override var dateSeparator = "-"
    override var lintEnabled = true
    override var notesView = "flat"
    override var graphTags = true
    override var graphPlaceholders = true
    override var newNoteDir = ""
    override var dailyDirOverride = ""
    override var autoSyncMinutes = 0
    override var syncAfterEdit = false
    override var cloned = true
}

class NoteRepositoryTest {
    private val fs = FakeFileSystem()
    private val root = "/repo".toPath()
    private val repo = NoteRepository(fs, root, TestSettings())

    @Test fun createsNotesAndLinksThemFromTheIndex() {
        val path = repo.createNoteFromTitle("Shopping List")
        assertEquals("shopping-list.md", path)
        assertEquals("# Shopping List\n\n", repo.read(path))
        assertTrue("[[shopping-list]]" in repo.read("Index.md"))
        assertEquals(listOf("Shopping List", "Index"), repo.listNotes().map { it.title }.sortedDescending())
    }

    @Test fun leavesTheIndexAloneWhenLinkingIsOff() {
        val repo = NoteRepository(fs, root, TestSettings().apply { linkFromIndex = false; indexDailyNotes = true })
        repo.write("Index.md", "# Index\n\n- [[old]]\n")
        repo.write("old.md", "# Old\n")
        repo.createNoteFromTitle("Shopping List")
        repo.dailyNote()
        repo.delete("old.md")
        assertEquals("# Index\n\n- [[old]]\n", repo.read("Index.md"))
    }

    @Test fun moveFixesLinksAndRemovesTheEmptyFolder() {
        repo.write("a/note.md", "# Note\n")
        repo.write("b.md", "see [[note]] and [n](a/note.md)\n")
        val r = repo.relocate("a/note.md", "archive/renamed")
        assertEquals("archive/renamed.md", r.path)
        assertEquals("see [[renamed]] and [n](archive/renamed.md)\n", repo.read("b.md"))
        assertFalse(fs.exists(root / "a"))
        assertEquals(listOf("archive"), repo.folders())
    }

    @Test fun skipsGitAndFoamFolders() {
        repo.write(".git/HEAD", "ref")
        repo.write(".foam/templates/new-note.md", "# \$FOAM_TITLE\n")
        repo.write("x.md", "# X\n")
        assertEquals(listOf("x.md"), repo.allFiles())
        assertEquals("# Hello\n", repo.read(repo.createNoteFromTitle("Hello")))
    }

    @Test fun readsFoamSettingsWithComments() {
        repo.write(".vscode/settings.json", "{\n  // daily notes\n  \"foam.orphans.exclude\": [\"journal/**\",],\n}")
        assertEquals(listOf("journal/**"), repo.foam.orphansExclude)
    }
}

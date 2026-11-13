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
        assertEquals("# Shopping List\n\n", repo.read(path)) // no template: Foam's heading
        assertEquals("# Index\n\n- [[shopping-list]]\n", repo.read("Index.md"))
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

    @Test fun startsNewAndDailyNotesFromTheirTemplatesWhenTheyExist() {
        val settings = TestSettings().apply { linkFromIndex = false }
        val repo = NoteRepository(fs, root, settings)
        repo.write(".foam/templates/new-note.md", "# \$FOAM_TITLE\n\nFrom the template\n")
        repo.write(".foam/templates/daily-note.md", "# Day \$FOAM_TITLE\n")
        val date = kotlinx.datetime.LocalDate(2026, 11, 12)
        assertEquals("# A\n\nFrom the template\n", repo.read(repo.createNoteFromTitle("A")))
        assertEquals("# Day 2026-11-12\n", repo.read(repo.dailyNote(date)))

        repo.delete(".foam/templates/new-note.md")
        repo.delete(".foam/templates/daily-note.md")
        // Without them, Foam heads a note with its title, and a daily note with its date.
        assertEquals("# B\n\n", repo.read(repo.createNoteFromTitle("B")))
        assertEquals("# C\n\n", repo.read(repo.createFromWikilink("C")))
        assertEquals("# 2026-11-13\n", repo.read(repo.dailyNote(kotlinx.datetime.LocalDate(2026, 11, 13))))
    }

    @Test fun createsNotesFromAPickedTemplate() {
        val settings = TestSettings().apply { linkFromIndex = false }
        val repo = NoteRepository(fs, root, settings)
        // Foam's own new-note and daily-note templates aren't offered for picking; one of the
        // same name in a subfolder is.
        repo.write(".foam/templates/new-note.md", "# \$FOAM_TITLE\n")
        repo.write(".foam/templates/daily-note.md", "# \$FOAM_TITLE\n")
        repo.write(".foam/templates/work/new-note.md", "# Work: \$FOAM_TITLE\n")
        repo.write(
            ".foam/templates/work/meeting.md",
            "---\nfoam_template:\n  name: Meeting notes\n  description: Agenda and actions\n" +
                "  filepath: meetings/\$FOAM_SLUG.md\n---\n\n# \$FOAM_TITLE\n\n## Agenda\n",
        )
        repo.write(".foam/templates/idea.md", "# Idea: \$FOAM_TITLE\n")
        repo.write(".foam/templates/script.js", "// not a Markdown template")

        assertEquals(
            listOf(
                TemplateFile("idea.md", "idea", null, needsTitle = true),
                TemplateFile("work/meeting.md", "Meeting notes", "Agenda and actions", needsTitle = true),
                TemplateFile("work/new-note.md", "work/new-note", null, needsTitle = true),
            ),
            repo.foam.templates(),
        )
        val path = repo.createNoteFromTitle("Team Sync", "work/meeting.md")
        assertEquals("meetings/team-sync.md", path)
        assertEquals("# Team Sync\n\n## Agenda\n", repo.read(path))
        assertEquals("# Idea: Wings\n", repo.read(repo.createNoteFromTitle("Wings", "idea.md")))
        // A template path can't reach outside the templates folder.
        assertEquals(null, repo.foam.templateAt("../notes.md"))
    }

    @Test fun aTemplateThatNamesTheFileItselfNeedsNoTitleAndReopensItsNote() {
        val repo = NoteRepository(fs, root, TestSettings().apply { linkFromIndex = false })
        repo.write(
            ".foam/templates/journal.md",
            "---\nfoam_template:\n  filepath: journal/\$FOAM_DATE_YEAR-\$FOAM_DATE_MONTH-\$FOAM_DATE_DATE.md\n---\n\n" +
                "# \$FOAM_DATE_YEAR-\$FOAM_DATE_MONTH-\$FOAM_DATE_DATE\n",
        )
        val daily = repo.foam.templates().single()
        assertFalse(daily.needsTitle)
        val path = repo.createNoteFromTitle("", daily.path)
        assertTrue(path.startsWith("journal/") && !path.contains("-2."))
        repo.write(path, "# Edited\n")
        assertEquals(path, repo.createNoteFromTitle("", daily.path))
        assertEquals("# Edited\n", repo.read(path))

        assertTrue(Template.parse("# \${FOAM_TITLE}\n").needsTitle)
        assertTrue(Template.parse("---\nfoam_template:\n  filepath: x/\$FOAM_SLUG.md\n---\nBody\n").needsTitle)
        assertFalse(Template.parse("---\nfoam_template:\n  filepath: inbox.md\n---\nBody\n").needsTitle)
    }

    @Test fun aNewIndexStartsAsANewNoteWould() {
        val repo = NoteRepository(fs, root, TestSettings())
        repo.write(".foam/templates/new-note.md", "---\nfoam_template:\n  filepath: notes/\$FOAM_SLUG.md\n---\n\n# \$FOAM_TITLE\n")
        repo.createNoteFromTitle("Shopping List")
        // The index stays where it belongs (not the template's filepath), titled after its name.
        assertEquals("# Index\n\n- [[shopping-list]]\n", repo.read("Index.md"))

        repo.delete(".foam/templates/new-note.md")
        repo.delete("Index.md")
        repo.createNoteFromTitle("Groceries")
        assertEquals("# Index\n\n- [[groceries]]\n", repo.read("Index.md"))
    }
}

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
    override var spellEnabled = false
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

    // ---- editing templates ----------------------------------------------------------------

    @Test fun listsTheDefaultTemplatesFirstEvenBeforeTheyExist() {
        repo.write(".foam/templates/idea.md", "---\nfoam_template:\n  name: Idea\n  description: A quick idea\n---\n# \$FOAM_TITLE\n")
        repo.write(".foam/templates/daily-note.md", "# \$FOAM_DATE_YEAR\n")
        assertEquals(
            listOf(
                TemplateListing(".foam/templates/new-note.md", "new-note.md", null, "new-note.md", exists = false),
                TemplateListing(".foam/templates/daily-note.md", "daily-note.md", null, "daily-note.md", exists = true),
                TemplateListing(".foam/templates/idea.md", "Idea", "A quick idea", null, exists = true),
            ),
            repo.templateList(),
        )
    }

    @Test fun aCreatedDefaultTemplateChangesNothingUntilItsEdited() {
        val settings = TestSettings().apply { linkFromIndex = false }
        val repo = NoteRepository(fs, root, settings)
        val date = kotlinx.datetime.LocalDate(2026, 11, 13)
        val withoutNote = repo.read(repo.createNoteFromTitle("Before"))
        val withoutDaily = repo.read(repo.dailyNote(date))
        repo.delete(repo.dailyNote(date))

        assertEquals(".foam/templates/new-note.md", repo.createDefaultTemplate("new-note.md"))
        assertEquals(".foam/templates/daily-note.md", repo.createDefaultTemplate("daily-note.md"))
        assertEquals(withoutNote.replace("Before", "After"), repo.read(repo.createNoteFromTitle("After")))
        assertEquals(withoutDaily, repo.read(repo.dailyNote(date)))
        // An existing one is left as it is.
        repo.write(".foam/templates/new-note.md", "# Mine\n")
        repo.createDefaultTemplate("new-note.md")
        assertEquals("# Mine\n", repo.read(".foam/templates/new-note.md"))
    }

    @Test fun createsANamedTemplateForThePicker() {
        val path = repo.createTemplate("Meeting Notes")
        assertEquals(".foam/templates/meeting-notes.md", path)
        val listed = repo.foam.templates().single()
        assertEquals("Meeting Notes", listed.name)
        assertTrue(listed.needsTitle)
        // Made from it: the title as a heading, and the starter's commented-out filepath unused.
        val note = repo.createNoteFromTitle("Standup", "meeting-notes.md")
        assertEquals("standup.md", note)
        assertEquals("# Standup\n\n", repo.read(note))
        // A name that would make one of Foam's default templates doesn't.
        assertEquals(".foam/templates/new-note-2.md", repo.createTemplate("New Note"))
        // Quoted when YAML needs it, and still read back.
        repo.createTemplate("Plan: weekly")
        assertTrue(repo.foam.templates().any { it.name == "Plan: weekly" })
    }

    @Test fun renamesAndDeletesTemplatesWithoutTouchingNotes() {
        repo.write(".foam/templates/a.md", "# A \$FOAM_TITLE\n")
        repo.write(".foam/templates/b.md", "# B\n")
        repo.write("Index.md", "- [[a]]\n")
        assertEquals(".foam/templates/c.md", repo.renameTemplate(".foam/templates/a.md", "c"))
        assertEquals("# A \$FOAM_TITLE\n", repo.read(".foam/templates/c.md"))
        // Taken, or one of Foam's default templates: refused.
        assertEquals(null, repo.renameTemplate(".foam/templates/c.md", "b.md"))
        assertEquals(null, repo.renameTemplate(".foam/templates/c.md", "daily-note"))
        repo.deleteTemplate(".foam/templates/c.md")
        assertFalse(fs.exists(root / ".foam/templates/c.md"))
        assertEquals("- [[a]]\n", repo.read("Index.md"))
    }

    @Test fun fillsInATemplateAsAnExampleNote() {
        val settings = TestSettings().apply { newNoteDir = "inbox"; dailyDirOverride = "days" }
        val repo = NoteRepository(fs, root, settings)
        val now = kotlinx.datetime.LocalDateTime(2026, 11, 13, 9, 30)
        val plain = repo.templateSample(".foam/templates/idea.md", "# \$FOAM_TITLE\n\nFile: \$TM_FILENAME_BASE\n", now)
        assertEquals("inbox/example-note.md", plain.notePath)
        assertEquals("# Example Note\n\nFile: example-note\n", plain.markdown)
        val placed = repo.templateSample(
            ".foam/templates/meeting.md",
            "---\nfoam_template:\n  filepath: meetings/\$FOAM_DATE_YEAR/\$FOAM_SLUG.md\ntags: meeting\n---\n# \$FOAM_TITLE\n",
            now,
        )
        assertEquals("meetings/2026/example-note.md", placed.notePath)
        assertEquals("---\ntags: meeting\n---\n# Example Note\n", placed.markdown)
        val daily = repo.templateSample(".foam/templates/daily-note.md", "# \$FOAM_TITLE \$FOAM_DATE_DAY_NAME_SHORT\n", now)
        assertTrue(daily.daily)
        assertEquals("days/2026-11-13.md", daily.notePath)
        assertEquals("# 2026-11-13 ${PlatformDates.dayName(5, short = true)}\n", daily.markdown)
    }

    @Test fun everyVariableInTheInsertListIsFilledIn() {
        for (v in FoamVariables.catalog) {
            val out = FoamVariables.resolve("\$" + v.name, "T", path = "a/t.md")
            assertTrue(out.isNotEmpty() && !out.startsWith("\$"), "${v.name} isn't filled in: $out")
        }
    }

    @Test fun insertsAVariableWithBracesOnlyWhenNeeded() {
        assertEquals("# \$FOAM_TITLE" to 13, FoamVariables.insert("# ", 2, 2, "FOAM_TITLE"))
        // Followed by a capital or _, it would run into the name.
        assertEquals("\${FOAM_DATE_YEAR}_X" to 17, FoamVariables.insert("_X", 0, 0, "FOAM_DATE_YEAR"))
        assertEquals("\$FOAM_DATE_YEAR-01" to 15, FoamVariables.insert("-01", 0, 0, "FOAM_DATE_YEAR"))
        // Replaces a selection.
        assertEquals("a \$UUID c" to 7, FoamVariables.insert("a b c", 2, 3, "UUID"))
    }

    @Test fun countsWhatTheMenuShows() {
        repo.write("a.md", "# A\n\n[[b]] [[Missing]] #idea\n")
        repo.write("b.md", "---\ntags: [Idea, dev/git]\n---\n# B\n")
        repo.write("c.md", "# C\n\nalone\n")
        val o = repo.overview()
        assertEquals(repo.tagNames().size, o.tags)
        assertEquals(2, o.tags)
        assertEquals(repo.orphans().size, o.orphans)
        assertEquals(repo.placeholders().size, o.placeholders)
        assertEquals(1, o.placeholders)
        // Links out come from the text as edited; links in from the other notes.
        assertEquals(2 to 0, repo.connectionCounts("a.md", repo.read("a.md")))
        assertEquals(3 to 0, repo.connectionCounts("a.md", repo.read("a.md") + "[[c]]\n"))
        assertEquals(0 to 1, repo.connectionCounts("b.md", repo.read("b.md")))
    }
}

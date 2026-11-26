package app.daranotes.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LintSettingsTest {
    // ---- comments in the note -------------------------------------------------------------

    @Test fun ignoresARuleBelowFrontMatter() {
        val note = "---\ntags: [a]\n---\n# Title\n"
        assertEquals(
            "---\ntags: [a]\n---\n<!-- markdownlint-disable-file MD013 -->\n# Title\n",
            LintComments.ignoreInNote(note, "MD013"),
        )
        assertEquals("<!-- markdownlint-disable-file MD013 -->\n# Title", LintComments.ignoreInNote("# Title", "MD013"))
    }

    @Test fun addsRulesToTheSameComment() {
        val once = LintComments.ignoreInNote("# Title\n", "MD013")
        val twice = LintComments.ignoreInNote(once, "MD033")
        assertEquals("<!-- markdownlint-disable-file MD013 MD033 -->\n# Title\n", twice)
        assertEquals(twice, LintComments.ignoreInNote(twice, "md013"))
    }

    @Test fun aWholeNoteCommentAlreadyCoversARule() {
        val off = "<!-- markdownlint-disable-file -->\n# Title\n"
        assertEquals(off, LintComments.ignoreInNote(off, "MD013"))
        assertEquals(off, LintComments.disableInNote(off))
    }

    @Test fun disablingTheNoteReplacesARuleList() {
        assertEquals(
            "<!-- markdownlint-disable-file -->\r\n# Title\r\n",
            LintComments.disableInNote("<!-- markdownlint-disable-file MD013 MD033 -->\r\n# Title\r\n"),
        )
        assertEquals("<!-- markdownlint-disable-file -->\n# Title\n", LintComments.disableInNote("# Title\n"))
    }

    @Test fun leavesOtherCommentsAlone() {
        val note = "<!-- cspell:disable -->\n<!-- markdownlint-disable MD013 -->\n# Title\n"
        assertEquals(
            "<!-- markdownlint-disable-file MD041 -->\n$note",
            LintComments.ignoreInNote(note, "MD041"),
        )
    }

    // ---- the repository's config ----------------------------------------------------------

    private val fs = FakeFileSystem()
    private val root = "/repo".toPath().also { fs.createDirectories(it) }
    private fun write(name: String, text: String) {
        (root / name).parent?.let(fs::createDirectories)
        fs.write(root / name) { writeUtf8(text) }
    }
    private fun read(name: String) = fs.read(root / name) { readUtf8() }

    @Test fun createsAConfigWhenThereIsNone() {
        assertEquals(".markdownlint.json", LintRuleOff.apply(fs, root, "MD013/line-length"))
        assertEquals("{\n  \"MD013\": false\n}\n", read(".markdownlint.json"))
    }

    @Test fun addsTheRuleAtTheEndInTheFileStyle() {
        write(".markdownlint.json", "{\n  // Ours\n  \"default\": true,\n  \"MD033\": false\n}\n")
        LintRuleOff.apply(fs, root, "MD013/line-length")
        assertEquals("{\n  // Ours\n  \"default\": true,\n  \"MD033\": false,\n  \"MD013\": false\n}\n", read(".markdownlint.json"))
    }

    @Test fun changesAnEntryUnderEitherName() {
        write(".markdownlint.jsonc", "{\n  \"line-length\": { \"line_length\": 100 }, // wide\n  \"MD033\": true\n}\n")
        assertEquals(".markdownlint.jsonc", LintRuleOff.apply(fs, root, "MD013/line-length"))
        assertEquals("{\n  \"line-length\": false, // wide\n  \"MD033\": true\n}\n", read(".markdownlint.jsonc"))
        LintRuleOff.apply(fs, root, "MD033/no-inline-html")
        assertEquals("{\n  \"line-length\": false, // wide\n  \"MD033\": false\n}\n", read(".markdownlint.jsonc"))
    }

    @Test fun cli2AndVsCodeConfigsNestTheRules() {
        write(".markdownlint-cli2.jsonc", "{\n  \"config\": {\n    \"default\": true\n  }\n}\n")
        LintRuleOff.apply(fs, root, "MD013/line-length")
        assertEquals("{\n  \"config\": {\n    \"default\": true,\n    \"MD013\": false\n  }\n}\n", read(".markdownlint-cli2.jsonc"))

        val other = FakeFileSystem()
        val r = "/r".toPath()
        other.createDirectories(r / ".vscode")
        other.write(r / ".vscode/settings.json") { writeUtf8("{\n  \"markdownlint.config\": {},\n  \"editor.wordWrap\": \"on\"\n}\n") }
        assertEquals(".vscode/settings.json", LintRuleOff.apply(other, r, "MD013/line-length"))
        assertEquals(
            "{\n  \"markdownlint.config\": {\"MD013\": false},\n  \"editor.wordWrap\": \"on\"\n}\n",
            other.read(r / ".vscode/settings.json") { readUtf8() },
        )
    }

    @Test fun yamlConfigsGetALine() {
        write(".markdownlint.yaml", "default: true\nMD033: true # html\n")
        LintRuleOff.apply(fs, root, "MD013/line-length")
        LintRuleOff.apply(fs, root, "MD033/no-inline-html")
        assertEquals("default: true\nMD033: false\nMD013: false\n", read(".markdownlint.yaml"))
    }

    @Test fun yamlEntriesWithSettingsAreLeftToTheUser() {
        write(".markdownlint.yml", "line-length:\n  line_length: 100\n")
        assertFailsWith<IllegalStateException> { LintRuleOff.apply(fs, root, "MD013/line-length") }
        assertEquals("line-length:\n  line_length: 100\n", read(".markdownlint.yml"))
    }
}

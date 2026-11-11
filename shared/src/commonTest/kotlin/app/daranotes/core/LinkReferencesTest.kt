package app.daranotes.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkReferencesTest {
    private val files = listOf("Shopping.md", "Work/Plan.md", "Work/Big Ideas.md", "images/a.png", "Note.md")
    private val titles = mapOf("Shopping.md" to "Shopping", "Work/Plan.md" to "The Plan", "Work/Big Ideas.md" to "Big Ideas")
    private fun update(text: String, path: String = "Note.md", mode: LinkReferences.Mode = LinkReferences.Mode.WITH_EXTENSIONS) =
        LinkReferences.update(text, path, files, { titles[it] ?: it }, mode)

    @Test fun addsDefinitionsAfterABlankLine() {
        assertEquals(
            "# Note\n\nBuy [[Shopping]] and see [[Plan|the plan]].\n\n" +
                "[Shopping]: Shopping.md \"Shopping\"\n[Plan|the plan]: Work/Plan.md \"The Plan\"\n",
            update("# Note\n\nBuy [[Shopping]] and see [[Plan|the plan]].\n"),
        )
    }

    @Test fun pathsAreRelativeToTheNoteAndWrappedWhenTheyHaveSpaces() {
        assertEquals(
            "[[Big Ideas]] [[Shopping]]\n\n[Big Ideas]: <Big Ideas.md> \"Big Ideas\"\n[Shopping]: ../Shopping.md \"Shopping\"\n",
            update("[[Big Ideas]] [[Shopping]]\n", path = "Work/Plan.md"),
        )
    }

    @Test fun replacesStaleDefinitionsAndKeepsOthers() {
        val text = "[[Shopping]] and [site][web]\n\n[Plan]: Work/Plan.md \"The Plan\"\n[web]: https://example.com\n"
        assertEquals(
            "[[Shopping]] and [site][web]\n\n[web]: https://example.com\n[Shopping]: Shopping.md \"Shopping\"\n",
            update(text),
        )
    }

    @Test fun keepsExistingDefinitionsAsWrittenAndAddsMissingOnesAtTheEnd() {
        val text = "[[Shopping]] [[Plan]]\n\n[Plan]: Work/Plan.md \"Old title\"\n"
        assertEquals("$text[Shopping]: Shopping.md \"Shopping\"\n", update(text))
    }

    @Test fun removesTheBlockWhenTheLinksAreGone() {
        assertEquals("Nothing here\n", update("Nothing here\n\n[Shopping]: Shopping.md \"Shopping\"\n"))
    }

    @Test fun skipsMissingNotesAndCodeAndLeavesUpToDateNotesAlone() {
        assertNull(update("[[Nowhere]] and `[[Shopping]]`\n\n```\n[[Plan]]\n```\n"))
        assertNull(update("[[Shopping]]\n\n[Shopping]: Shopping.md \"Shopping\"\n"))
    }

    @Test fun followsTheWorkspaceStyle() {
        assertEquals("[[Shopping]]\n\n[Shopping]: Shopping \"Shopping\"\n", update("[[Shopping]]\n", mode = LinkReferences.Mode.WITHOUT_EXTENSIONS))
        assertNull(update("[[Shopping]]\n", mode = LinkReferences.Mode.OFF))
    }

    @Test fun embedsGetDefinitionsToo() {
        assertEquals("![[a.png]]\n\n[a.png]: images/a.png \"a.png\"\n", update("![[a.png]]\n"))
    }
}

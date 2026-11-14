package app.daranotes.ui

import app.daranotes.core.Toc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class HelpTest {
    /** Where each page lives in the repository, for checking its links work on GitHub too. */
    private fun location(page: String) = if (page == "privacy") "PRIVACY.md" else "docs/$page.md"

    private fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        for (part in path.split('/')) when (part) {
            "", "." -> {}
            ".." -> out.removeLastOrNull()
            else -> out.addLast(part)
        }
        return out.joinToString("/")
    }

    /** The targets of Markdown links outside code. */
    private fun links(markdown: String): List<String> {
        val out = mutableListOf<String>()
        var inFence = false
        for (line in markdown.lines()) {
            if (line.trimStart().startsWith("```")) { inFence = !inFence; continue }
            if (inFence) continue
            val noCode = line.replace(Regex("`[^`]*`"), "")
            Regex("""\]\(([^)\s]+)\)""").findAll(noCode).forEach { out += it.groupValues[1] }
        }
        return out
    }

    @Test fun hasTheContentsPageAndThePrivacyPolicy() {
        assertTrue(Help.INDEX in Help.names)
        assertTrue("privacy" in Help.names)
        assertTrue("templates" in Help.names)
    }

    @Test fun everyPageHasATitle() {
        for (name in Help.names) {
            val title = Help.title(Help.page(name)!!, fallback = "")
            assertTrue(title.isNotBlank(), "$name has no # title")
        }
    }

    @Test fun everyLinkBetweenPagesWorksInTheAppAndOnGitHub() {
        for (name in Help.names) {
            val markdown = Help.page(name)!!
            val ids = Toc.headings(markdown).map { it.id }.toSet()
            for (link in links(markdown)) {
                if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(link)) continue
                if (link.startsWith("#")) {
                    assertTrue(link.removePrefix("#") in ids, "$name links to a missing heading: $link")
                    continue
                }
                val target = Help.pageFor(link) ?: fail("$name links to $link, which isn't a help page")
                val fromDir = location(name).substringBeforeLast('/', "")
                val resolved = normalize("$fromDir/${link.substringBefore('#')}")
                assertEquals(location(target), resolved, "$name: $link doesn't point at ${location(target)} on GitHub")
            }
        }
    }

    @Test fun findsThePageALinkPointsTo() {
        assertEquals("templates", Help.pageFor("templates.md"))
        assertEquals("templates", Help.pageFor("docs/templates"))
        assertEquals("privacy", Help.pageFor("../PRIVACY.md#contact"))
        assertNull(Help.pageFor("not-a-page.md"))
    }

    @Test fun putsATableOfContentsUnderTheTitle() {
        val page = "# Title\n\nIntro.\n\n## One\n\ntext\n\n### One A\n\n## Two\n"
        assertEquals(
            "# Title\n\n- [One](#one)\n  - [One A](#one-a)\n- [Two](#two)\n\nIntro.\n\n## One\n\ntext\n\n### One A\n\n## Two\n",
            Help.withToc(page),
        )
        // Not for a page with fewer than two sections, or one that has its own.
        assertEquals("# Title\n\n## Only\n", Help.withToc("# Title\n\n## Only\n"))
        val own = "# T\n\n- [A](#a)\n- [B](#b)\n\n## A\n\n## B\n"
        assertEquals(own, Help.withToc(own))
        // The real pages: templates gets one, the contents page doesn't need one.
        assertNotEquals(Help.page("templates"), Help.withToc(Help.page("templates")!!))
        assertEquals(Help.page(Help.INDEX), Help.withToc(Help.page(Help.INDEX)!!))
    }
}

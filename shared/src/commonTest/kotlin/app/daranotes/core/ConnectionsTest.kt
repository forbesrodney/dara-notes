package app.daranotes.core

import app.daranotes.core.NoteLink.Kind
import app.daranotes.core.NoteLink.Status
import kotlin.test.assertEquals
import kotlin.test.Test

class ConnectionsTest {
    private val files = listOf("Index.md", "projects/plan.md", "projects/notes.md", "img/cat.png", "docs/a b.pdf")

    private fun links(text: String, path: String = "projects/notes.md") =
        Connections.links(text, path, files).map { listOf(it.line, it.kind, it.target, it.heading, it.resolved, it.status) }

    @Test fun wikilinksAndEmbeds() = assertEquals(
        listOf(
            listOf(1, Kind.WIKILINK, "plan", "Goals", "projects/plan.md", Status.NOTE),
            listOf(1, Kind.WIKILINK, "Someday", null, null, Status.MISSING),
            listOf(2, Kind.EMBED, "cat.png", null, "img/cat.png", Status.FILE),
            listOf(2, Kind.WIKILINK, "", "Top", null, Status.SAME_NOTE),
        ),
        links("See [[plan#Goals|the plan]] and [[Someday]].\n![[cat.png]] back to [[#Top]]"),
    )

    @Test fun markdownLinksImagesAndUrls() = assertEquals(
        listOf(
            listOf(1, Kind.LINK, "plan.md", null, "projects/plan.md", Status.NOTE),
            listOf(1, Kind.LINK, "../Index", null, "Index.md", Status.NOTE),
            listOf(2, Kind.IMAGE, "../img/cat.png", null, "img/cat.png", Status.FILE),
            listOf(2, Kind.LINK, "/docs/a b.pdf", null, "docs/a b.pdf", Status.FILE),
            listOf(3, Kind.LINK, "https://foambubble.github.io", null, null, Status.EXTERNAL),
            listOf(3, Kind.URL, "https://example.com/x", null, null, Status.EXTERNAL),
            listOf(3, Kind.URL, "https://example.org", null, null, Status.EXTERNAL),
            listOf(4, Kind.LINK, "", "goals", null, Status.SAME_NOTE),
            listOf(4, Kind.LINK, "gone.md", null, null, Status.MISSING),
        ),
        links(
            "[Plan](plan.md) and [home](../Index \"Home\")\n" +
                "![cat](../img/cat.png) [pdf](</docs/a%20b.pdf>)\n" +
                "[Foam](https://foambubble.github.io) <https://example.com/x> see https://example.org.\n" +
                "[goals](#goals) [gone](gone.md)",
        ),
    )

    @Test fun skipsCodeFrontMatterAndDefinitions() = assertEquals(
        listOf(listOf(7, Kind.WIKILINK, "plan", null, "projects/plan.md", Status.NOTE)),
        links("---\nup: [[Index]]\n---\n```\n[[Index]]\n```\n`[[Index]]` [[plan]]\n[plan]: plan.md \"Plan\""),
    )

    @Test fun backlinksListSourceLineAndContext() {
        val notes = mapOf(
            "Index.md" to "# Index\n\n- [[plan]]\n- [[notes]]",
            "projects/notes.md" to "Up: [Plan](plan.md), [[plan]] again",
            "projects/plan.md" to "[[plan]] links to itself",
        )
        assertEquals(
            listOf(
                Backlink("Index.md", 3, "- [[plan]]"),
                Backlink("projects/notes.md", 1, "Up: [Plan](plan.md), [[plan]] again"),
            ),
            Connections.backlinks("projects/plan.md", notes, files),
        )
    }
}

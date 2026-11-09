package app.foamforandroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphTest {
    @Test fun frontMatterTagStyles() {
        assertEquals(listOf("a", "b"), NoteParser.frontMatterTags(listOf("tags: [a, b]")))
        assertEquals(listOf("a", "b"), NoteParser.frontMatterTags(listOf("tags: a, b")))
        assertEquals(listOf("a", "b"), NoteParser.frontMatterTags(listOf("tags: '#a' b")))
        assertEquals(listOf("work", "dev/git"), NoteParser.frontMatterTags(listOf("title: x", "tags:", "  - work", "  - \"dev/git\"", "other: 1")))
    }

    @Test fun inlineTagsSkipHeadingsCodeNumbersAndUrls() {
        val p = NoteParser.parse(
            "---\ntags: [fm]\n---\n# Heading\nSome #idea and #dev/git, not #123 or x#y\n" +
                "`#code` http://x.com/#frag\n```\n#fenced\n```\n[[note#Section]] #last",
        )
        assertEquals(setOf("fm", "idea", "dev/git", "last"), p.tags)
        assertEquals(listOf("note"), p.wikilinks)
    }

    @Test fun buildsNotesTagsPlaceholdersAndLinks() {
        val notes = mapOf(
            "Index.md" to "* [[TODO]]\n* [[Missing Note]]\n[plan](projects/plan.md)\n![[cat.png]]",
            "TODO.md" to "---\ntags: [work]\n---\nSee [[Index]] and [[TODO]]",
            "projects/plan.md" to "#work back to [index](../Index.md)",
        )
        val g = Graph.build(notes, mapOf("TODO.md" to "To do"), notes.keys.toList() + "cat.png")
        val ids = g.nodes.associate { it.id to it.type }
        assertEquals(Graph.Type.PLACEHOLDER, ids["placeholder:missing note"])
        assertEquals(Graph.Type.TAG, ids["tag:work"])
        assertEquals("To do", g.nodes.first { it.id == "TODO.md" }.label)
        val links = g.links.map { it.source to it.target }.toSet()
        assertEquals(
            setOf(
                "Index.md" to "TODO.md", "Index.md" to "placeholder:missing note", "Index.md" to "projects/plan.md",
                "TODO.md" to "Index.md", "TODO.md" to "tag:work",
                "projects/plan.md" to "Index.md", "projects/plan.md" to "tag:work",
            ),
            links,
        )
        assertTrue(g.toJson().contains("\"degree\""))
    }
}

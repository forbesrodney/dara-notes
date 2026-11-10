package app.foamforandroid.core

import kotlin.test.assertEquals
import kotlin.test.Test

class TagsTest {
    private val tree = Tags.tree(
        mapOf(
            "a.md" to setOf("dev/git", "work"),
            "b.md" to setOf("Dev/Kotlin", "dev"),
            "c.md" to setOf("work", "Idea"),
            "d.md" to setOf("dev/git/"),
        ),
    )

    @Test fun nestsByPathMergesCaseAndCountsDistinctNotes() {
        assertEquals(listOf("dev", "Idea", "work"), tree.map { it.name })
        val dev = tree[0]
        assertEquals(listOf("b.md"), dev.notes)
        assertEquals(3, dev.noteCount)                     // a, b, d (b counted once)
        assertEquals(listOf("git", "Kotlin"), dev.children.map { it.name })
        assertEquals("dev/git", dev.children[0].tag)
        assertEquals(listOf("a.md", "d.md"), dev.children[0].notes)
        assertEquals(listOf("a.md", "c.md"), tree[2].notes)
    }

    @Test fun parentsAppearWithoutNotesOfTheirOwn() {
        val t = Tags.tree(mapOf("x.md" to setOf("projects/app/ui")))
        assertEquals("projects", t.single().tag)
        assertEquals(emptyList<String>(), t.single().notes)
        assertEquals(1, t.single().noteCount)
        assertEquals("projects/app/ui", t.single().children.single().children.single().tag)
    }

    @Test fun filterKeepsMatchesAndTheirParents() {
        val f = Tags.filter(tree, "#kot")
        assertEquals(listOf("dev"), f.map { it.name })
        assertEquals(listOf("Kotlin"), f[0].children.map { it.name })
        assertEquals(tree, Tags.filter(tree, "  "))
        assertEquals(2, Tags.filter(tree, "dev")[0].children.size)  // "dev" matches: kept whole
    }
}

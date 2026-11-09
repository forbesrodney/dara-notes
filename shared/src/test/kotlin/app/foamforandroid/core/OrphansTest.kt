package app.foamforandroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrphansTest {
    @Test fun orphansHaveNoNoteOrPlaceholderLinks() {
        val notes = mapOf(
            "Index.md" to "* [[a]]",
            "a.md" to "nothing out",                 // linked from Index
            "b.md" to "only a #tag",                 // tags don't count
            "c.md" to "[[Not written yet]]",         // links to a placeholder
            "d.md" to "[[d]] links to itself",       // self-links don't count
            "e.md" to "[e](f.md)",                   // Markdown link out
            "f.md" to "",                            // ... and in
        )
        val g = Graph.build(notes, emptyMap(), notes.keys.toList())
        assertEquals(listOf("b.md", "d.md"), g.orphans())
    }

    @Test fun globs() {
        assertTrue(Globs.matches("journal/**", "journal/2026-11-09.md"))
        assertTrue(Globs.matches("journal/**", "journal/2026/11/09.md"))
        assertFalse(Globs.matches("journal/**", "notes/journal.md"))
        assertTrue(Globs.matches("**/templates/**", "a/templates/t.md"))
        assertTrue(Globs.matches("**/templates/**", "templates/t.md"))
        assertTrue(Globs.matches("*.md", "Index.md"))
        assertFalse(Globs.matches("*.md", "a/Index.md"))
        assertTrue(Globs.matches("**/*.md", "a/b/Index.md"))
        assertTrue(Globs.matches("{inbox,archive}/**", "archive/old.md"))
        assertTrue(Globs.matches("note-?.md", "note-1.md"))
        assertTrue(Globs.matches("journal", "journal/x.md"))
        assertTrue(Globs.matches("/journal/", "journal/x.md"))
        assertFalse(Globs.matches("journal", "journals/x.md"))
        assertFalse(Globs.matches("a+b.md", "aab.md"))
    }
}

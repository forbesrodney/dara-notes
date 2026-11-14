package app.daranotes.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrontMatterTagsTest {
    private fun add(text: String, tag: String) = FrontMatterTags.add(text, tag)
    private fun remove(text: String, tag: String) = FrontMatterTags.remove(text, tag)

    @Test fun addsAFrontMatterBlockWhenThereIsNone() {
        assertEquals("---\ntags: [idea]\n---\n# Plan\n\nText\n", add("# Plan\n\nText\n", "idea"))
        assertEquals("---\ntags: [idea]\n---\n", add("", "idea"))
    }

    @Test fun removesTheBlockWhenItsLastTagGoes() {
        assertEquals("# Plan\n\nText\n", remove("---\ntags: [idea]\n---\n# Plan\n\nText\n", "idea"))
        // With a blank line between the block and the note, that goes too.
        assertEquals("# Plan\n", remove("---\ntags: idea\n---\n\n# Plan\n", "idea"))
        // Other front matter stays, and so does the block.
        assertEquals("---\ntitle: Plan\n---\n# Plan\n", remove("---\ntitle: Plan\ntags: [idea]\n---\n# Plan\n", "idea"))
    }

    @Test fun addsATagsLineToFrontMatterWithout() {
        assertEquals("---\ntitle: Plan\ntags: [idea]\n---\n# Plan\n", add("---\ntitle: Plan\n---\n# Plan\n", "idea"))
    }

    @Test fun keepsTheFlowListStyle() {
        assertEquals("---\ntags: [a, b, idea]\n---\n", add("---\ntags: [a, b]\n---\n", "idea"))
        assertEquals("---\ntags: [idea]\n---\n", add("---\ntags: []\n---\n", "idea"))
        assertEquals("---\ntags: [\"a\", \"idea\"]\n---\n", add("---\ntags: [\"a\"]\n---\n", "idea"))
        assertEquals("---\ntags: [a, c]\n---\n", remove("---\ntags: [a, b, c]\n---\n", "b"))
    }

    @Test fun keepsTheCommaAndSpaceStyles() {
        assertEquals("---\ntags: a, b, idea\n---\n", add("---\ntags: a, b\n---\n", "idea"))
        assertEquals("---\ntags: a b idea\n---\n", add("---\ntags: a b\n---\n", "idea"))
        assertEquals("---\ntags: a, idea\n---\n", add("---\ntags: a\n---\n", "idea"))
        assertEquals("---\ntags: a, c\n---\n", remove("---\ntags: a, b, c\n---\n", "b"))
        assertEquals("---\ntags: a c\n---\n", remove("---\ntags: a b c\n---\n", "b"))
    }

    @Test fun keepsTheYamlListStyle() {
        assertEquals("---\ntags:\n  - a\n  - idea\nx: 1\n---\n", add("---\ntags:\n  - a\nx: 1\n---\n", "idea"))
        assertEquals("---\ntags:\n- 'a'\n- 'idea'\n---\n", add("---\ntags:\n- 'a'\n---\n", "idea"))
        assertEquals("---\ntags: [idea]\n---\n", add("---\ntags:\n---\n", "idea"))
        assertEquals("---\ntags:\n  - a\n---\n", remove("---\ntags:\n  - a\n  - b\n---\n", "b"))
        // The last item goes, and the key with it.
        assertEquals("---\nx: 1\n---\n", remove("---\ntags:\n  - b\nx: 1\n---\n", "b"))
    }

    @Test fun treatsTagLikeTagsAndIgnoresCaseAndHashes() {
        assertEquals("---\ntag: a, idea\n---\n", add("---\ntag: a\n---\n", "idea"))
        // Already there, in another case: unchanged.
        val note = "---\ntags: [Idea]\n---\n"
        assertEquals(note, add(note, "idea"))
        assertEquals("", remove("---\ntags: [\"#Idea\"]\n---\n", "idea"))
    }

    @Test fun leavesNotesAloneWhenTheTagIsntThere() {
        val note = "---\ntags: [a]\n---\n# T\n"
        assertEquals(note, remove(note, "b"))
        assertEquals("# T\n", remove("# T\n", "a"))
        // An empty tags line elsewhere isn't touched by removing from another.
        assertEquals("---\ntags: [b]\ntag:\n---\n", remove("---\ntags: [a, b]\ntag:\n---\n", "a"))
    }

    @Test fun keepsWindowsLineEndings() {
        assertEquals("---\r\ntags: [a, b]\r\n---\r\n# T\r\n", add("---\r\ntags: [a]\r\n---\r\n# T\r\n", "b"))
        assertEquals("# T\r\n", remove("---\r\ntags: [a]\r\n---\r\n# T\r\n", "a"))
    }

    @Test fun worksInATemplatesFrontMatter() {
        val template = "---\nfoam_template:\n  name: Idea\n---\n# \$FOAM_TITLE\n"
        val tagged = add(template, "idea")
        assertEquals("---\nfoam_template:\n  name: Idea\ntags: [idea]\n---\n# \$FOAM_TITLE\n", tagged)
        // A note made from it keeps the tag but not the template block.
        assertEquals("---\ntags: [idea]\n---\n# \$FOAM_TITLE\n", Template.parse(tagged).body)
    }

    @Test fun readsWhatItWrites() {
        var note = "# T\n"
        for (t in listOf("a", "dev/git", "b")) note = add(note, t)
        assertEquals(listOf("a", "dev/git", "b"), FrontMatterTags.read(note))
        assertEquals(setOf("a", "dev/git", "b"), NoteParser.parse(note).tags)
    }

    @Test fun acceptsOnlyTagsFoamAccepts() {
        assertEquals("idea", FrontMatterTags.normalize(" #idea "))
        assertEquals("dev/git", FrontMatterTags.normalize("dev/git/"))
        assertEquals("work_2026-q4", FrontMatterTags.normalize("work_2026-q4"))
        assertNull(FrontMatterTags.normalize("two words"))
        assertNull(FrontMatterTags.normalize("2026"))
        assertNull(FrontMatterTags.normalize("a,b"))
        assertNull(FrontMatterTags.normalize(""))
    }
}

package app.foamforandroid.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TocTest {
    /** Heading source -> ID. web/preview.js must give the same IDs (checked against it by hand). */
    val idCases = listOf(
        "Snippet Syntax" to "snippet-syntax",
        "For inserting line or block comments, honoring the current language" to
            "for-inserting-line-or-block-comments-honoring-the-current-language",
        "What's **new** in `v2.0`?" to "whats-new-in-v20",
        "[Links](https://example.com) and [[Wiki Note]]" to "links-and-wiki-note",
        "[[target|Alias]] here" to "alias-here",
        "snake_case and _emphasis_" to "snake_case-and-emphasis",
        "Émigré café 2026" to "émigré-café-2026",
        "Rocket 🚀 launch" to "rocket--launch",
        "A -- B" to "a----b",
        "C++ & C#" to "c--c",
        "Done ~~old~~ <!-- omit from toc -->" to "done-old",
    )

    @Test fun ids() {
        for ((source, id) in idCases) assertEquals(id, Toc.slug(Toc.plainText(source)), source)
    }

    @Test fun repeatsAreNumbered() {
        val ids = Toc.headings("## Notes\n## Notes\n## Notes-1\n## Notes").map { it.id }
        assertEquals(listOf("notes", "notes-1", "notes-1-1", "notes-2"), ids)
    }

    @Test fun skipsCodeAndFrontMatter() {
        val md = "---\ntitle: x\n---\n# Title\n```\n## Not a heading\n```\n    ## Indented code\n## Real\n"
        assertEquals(listOf("Title", "Real"), Toc.headings(md).map { it.text })
    }

    @Test fun setextHeadings() {
        val md = "Title\n=====\n\nSection\n-------\ntext\n\n- item\n---\n"
        assertEquals(listOf(1 to "Title", 2 to "Section"), Toc.headings(md).map { it.level to it.text })
    }

    @Test fun buildsNestedListWithoutH1AndOmitted() {
        val md = """
            # Title
            ## One
            ### One A
            #### Deep
            ## Two <!-- omit from toc -->
            <!-- omit in toc -->
            ## Three
            ## Four [x]
        """.trimIndent()
        assertEquals(
            """
            * [One](#one)
              * [One A](#one-a)
                * [Deep](#deep)
            * [Four \[x\]](#four-x)
            """.trimIndent(),
            Toc.build(md, "*"),
        )
    }

    private val note = "# Title\n\nIntro.\n\n## One\n\nText.\n\n## Two\n"
    private val toc = "- [One](#one)\n- [Two](#two)"

    @Test fun insertsUnderTheH1() {
        val edit = Toc.insertOrUpdate(note)!!
        assertEquals("# Title\n\n$toc\n\nIntro.\n\n## One\n\nText.\n\n## Two\n", edit.text)
        assertEquals("\n$toc\n\n", edit.text.substring(edit.start, edit.start + edit.added))
    }

    @Test fun insertsAtTopWithoutH1() {
        val text = Toc.insertOrUpdate("---\na: 1\n---\n## One\n")!!.text
        assertEquals("---\na: 1\n---\n- [One](#one)\n\n## One\n", text)
    }

    @Test fun insertsUnderSetextH1() {
        val text = Toc.insertOrUpdate("Title\n=====\n## One\n")!!.text
        assertEquals("Title\n=====\n\n- [One](#one)\n\n## One\n", text)
    }

    @Test fun updatesInPlaceAndDropsH1Entry() {
        val old = "# Title\n\n- [Title](#title)\n  - [One](#one)\n  - [Gone](#gone)\n\nIntro.\n\n## One\n\nText.\n\n## Two\n"
        val edit = Toc.update(old)!!
        assertEquals("# Title\n\n$toc\n\nIntro.\n\n## One\n\nText.\n\n## Two\n", edit.text)
        assertEquals(toc.length, edit.added)
        assertNull(Toc.update(edit.text)) // current: nothing to do
    }

    @Test fun noTocMeansNoUpdate() {
        assertNull(Toc.update(note))
        assertNull(Toc.update("# Title\n\n- [link](#x) inside text\n")) // not only links
        assertNull(Toc.update("```\n- [One](#one)\n```\n## One\n"))     // in a code block
    }

    @Test fun nothingToInsertWithoutSections() {
        assertNull(Toc.insertOrUpdate("# Just a title\n\nText.\n"))
    }

    @Test fun windowsLineEndings() {
        val md = "# Title\r\n\r\n- [Old](#old)\r\n\r\n## New\r\n"
        assertEquals("# Title\r\n\r\n- [New](#new)\r\n\r\n## New\r\n", Toc.update(md)!!.text)
    }
}

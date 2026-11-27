package app.daranotes.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class WordCountTest {
    @Test fun countsWordsWithApostrophesAsOne() =
        assertEquals(5, wordCount("# It's a test\n\nDon't stop."))

    @Test fun leavesOutFrontMatter() =
        assertEquals(2, wordCount("---\ntags: [one, two]\ntitle: Three\n---\n# Some words\n"))

    @Test fun frontMatterCanEndWithDots() =
        assertEquals(1, wordCount("---\ntitle: x\n...\nWord"))

    @Test fun leavesOutHtmlComments() =
        assertEquals(
            3,
            wordCount("<!-- markdownlint-disable-file -->\n# Three\n<!-- cspell:ignore xyz\nmore -->\nwords here"),
        )

    @Test fun anUnclosedCommentRunsToTheEnd() =
        assertEquals(1, wordCount("Before <!-- still typing"))

    @Test fun aRuleLaterInTheNoteIsNotFrontMatter() =
        assertEquals(4, wordCount("One two\n\n---\n\nthree four\n"))
}

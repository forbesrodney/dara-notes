package app.foamforandroid

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkFixerTest {
    private val files = listOf(
        "Index.md",
        "shopping.md",
        "projects/todo.md",
        "projects/plan.md",
        "journal/2026-11-08.md",
        "images/cat.png",
        "notes.md",
    )

    private fun fix(old: String, new: String, text: String, file: String, fileAfter: String = file) =
        LinkFixer(files, old, new).fix(text, file, fileAfter)

    @Test fun renamesWikilinksWithAliasHeadingAndEmbed() {
        val r = fix(
            "shopping.md", "groceries.md",
            "- [[shopping]]\nsee [[Shopping|the list]] and [[shopping#Fruit]]\n![[shopping]]\n[[shopping.md]]",
            "Index.md",
        )
        assertEquals(
            "- [[groceries]]\nsee [[groceries|the list]] and [[groceries#Fruit]]\n![[groceries]]\n[[groceries.md]]",
            r.text,
        )
        assertEquals(5, r.changes)
    }

    @Test fun leavesOtherLinksAndCodeAlone() {
        val text = "[[plan]] `[[shopping]]`\n```\n[[shopping]]\n```\n[[missing]]"
        val r = fix("shopping.md", "groceries.md", text, "Index.md")
        assertEquals(text, r.text)
        assertEquals(0, r.changes)
    }

    @Test fun pathStyleWikilinkKeepsPathStyleWhenMoved() {
        val r = fix("projects/todo.md", "archive/todo.md", "[[projects/todo]] and [[todo]]", "Index.md")
        assertEquals("[[archive/todo]] and [[todo]]", r.text)
    }

    @Test fun renameThatClashesUsesPathAndProtectsOtherLinks() {
        // Renaming shopping.md to todo.md at the root: the root note would now win [[todo]],
        // so links that meant projects/todo.md must become path-qualified.
        val r = fix("shopping.md", "todo.md", "[[todo]] and [[shopping]]", "Index.md")
        assertEquals("[[projects/todo]] and [[todo]]", r.text)
    }

    @Test fun markdownLinksToTheMovedNote() {
        val r = fix(
            "projects/todo.md", "archive/2026/todo.md",
            "[todo](projects/todo.md) [h](projects/todo.md#Next \"Title\") [web](https://x.y/todo.md) [a](#top)",
            "Index.md",
        )
        assertEquals(
            "[todo](archive/2026/todo.md) [h](archive/2026/todo.md#Next \"Title\") [web](https://x.y/todo.md) [a](#top)",
            r.text,
        )
    }

    @Test fun relativeLinksFromASubfolder() {
        val r = fix("shopping.md", "lists/shopping list.md", "[s](../shopping.md)", "projects/plan.md")
        assertEquals("[s](../lists/shopping%20list.md)", r.text)
    }

    @Test fun movedNoteKeepsItsOwnRelativeLinksWorking() {
        val text = "[plan](plan.md) ![cat](../images/cat.png) [self](todo.md) [[plan]] [gone](nothing.md)"
        val r = fix("projects/todo.md", "todo.md", text, "projects/todo.md", "todo.md")
        assertEquals(
            "[plan](projects/plan.md) ![cat](images/cat.png) [self](todo.md) [[plan]] [gone](nothing.md)",
            r.text,
        )
    }

    @Test fun foamReferenceDefinitions() {
        val text = "[[shopping]]\n\n[shopping]: shopping \"Shopping\"\n[plan]: projects/plan.md"
        val r = fix("shopping.md", "lists/groceries.md", text, "Index.md")
        assertEquals("[[groceries]]\n\n[groceries]: lists/groceries \"Shopping\"\n[plan]: projects/plan.md", r.text)
    }

    @Test fun footnotesAreNotTreatedAsLinks() {
        val text = "Text[^1]\n\n[^1]: notes about [[shopping]]"
        val r = fix("shopping.md", "groceries.md", text, "projects/plan.md", "elsewhere/plan.md")
        assertEquals("Text[^1]\n\n[^1]: notes about [[groceries]]", r.text)
    }

    @Test fun angleBracketDestinations() {
        val r = fix("shopping.md", "my lists/shopping.md", "[s](<shopping.md>)", "Index.md")
        assertEquals("[s](<my lists/shopping.md>)", r.text)
    }

    @Test fun relativePaths() {
        assertEquals("../a/b.md", RepoPaths.relative("x", "a/b.md"))
        assertEquals("b.md", RepoPaths.relative("a", "a/b.md"))
        assertEquals("a/b.md", RepoPaths.relative("", "a/b.md"))
        assertEquals("../../c.md", RepoPaths.relative("a/b", "c.md"))
        assertEquals(null, RepoPaths.join("", "../x.md"))
    }
}

class TemplateTest {
    @Test fun dropsBlankLinesLeftByTheFoamTemplateBlock() {
        val t = Template.parse("---\nfoam_template:\n  filepath: '/dev-notes/\$FOAM_SLUG.md'\n---\n\n# \$FOAM_TITLE\n")
        assertEquals("/dev-notes/\$FOAM_SLUG.md", t.filepath)
        assertEquals("# \$FOAM_TITLE\n", t.body)
    }

    @Test fun keepsOtherFrontMatterAndItsSpacing() {
        val t = Template.parse("---\nfoam_template:\n  filepath: x.md\ntags: daily\n---\n\n# Title\n")
        assertEquals("---\ntags: daily\n---\n\n# Title\n", t.body)
    }
}

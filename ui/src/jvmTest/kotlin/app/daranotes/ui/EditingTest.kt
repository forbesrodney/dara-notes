package app.daranotes.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class EditingTest {
    /** Text with the selection marked by [ and ], or the cursor by |. */
    private fun v(marked: String): TextFieldValue {
        val c = marked.indexOf('|')
        if (c >= 0) return TextFieldValue(marked.removeRange(c, c + 1), TextRange(c))
        val s = marked.indexOf('[')
        val e = marked.indexOf(']') - 1
        return TextFieldValue(marked.replace("[", "").replace("]", ""), TextRange(s, e))
    }

    private fun show(t: TextFieldValue): String =
        if (t.selection.collapsed) t.text.substring(0, t.selection.min) + "|" + t.text.substring(t.selection.min)
        else t.text.substring(0, t.selection.min) + "[" + t.text.substring(t.selection.min, t.selection.max) + "]" + t.text.substring(t.selection.max)

    private fun check(before: String, marker: String, after: String) =
        assertEquals(after, show(v(before).toggleWrap(marker, "x")))

    @Test fun boldAddsAndRemoves() {
        check("a [word] b", "**", "a **[word]** b")
        check("a **[word]** b", "**", "a [word] b")
        check("a [**word**] b", "**", "a [word] b")
    }

    @Test fun cursorUsesTheWord() {
        check("a wo|rd b", "**", "a **[word]** b")
        check("a **wo|rd** b", "**", "a [word] b")
        check("a | b", "**", "a **[x]** b")
    }

    @Test fun italicIsNotConfusedWithBold() {
        check("**[word]**", "*", "***[word]***")
        check("***[word]***", "*", "**[word]**")
        check("***[word]***", "**", "*[word]*")
        check("*[word]*", "*", "[word]")
    }

    @Test fun strikeThrough() {
        check("[done]", "~~", "~~[done]~~")
        check("~~[done]~~", "~~", "[done]")
        check("~[sub]~", "~~", "~~~[sub]~~~")
    }

    private fun indent(before: String, after: String) = assertEquals(after, show(v(before).tab()))
    private fun outdent(before: String, after: String) = assertEquals(after, show(v(before).outdentLines()))

    @Test fun tabNestsAListItemUnderTheOneAbove() {
        indent("- a\n- b|", "- a\n  - b|")
        indent("1. a\n1. b|", "1. a\n   1. b|")
        indent("- a\n  - b\n  - c|", "- a\n  - b\n    - c|")
        indent("- [ ] a\n- [ ] b|", "- [ ] a\n  - [ ] b|")
    }

    @Test fun tabIndentsEverySelectedLine() {
        indent("- a\n[- b\n- c]\n- d", "- a\n  [- b\n  - c]\n- d")
        // A selection ending at a line's start leaves that line alone; blank lines stay empty.
        indent("[- a\n\n- b\n]- c", "  [- a\n\n  - b\n]- c")
    }

    @Test fun tabTypesATabOutsideListsAndSelections() {
        indent("some |text", "some \t|text")
        indent("|", "\t|")
        indent("- a\n  more |text", "- a\n  more \t|text") // under an item, but not one
    }

    @Test fun tabIndentsSelectedLinesThatAreNotListItemsByFour() {
        indent("some [text]", "    some [text]")
        indent("- a\n  [more text]", "- a\n    [more text]")
    }

    @Test fun tabNestsTheItemWhereverTheCursorIs() {
        indent("- a\n- b|c", "- a\n  - b|c")
        indent("- a\n|- b", "- a\n  |- b")
    }

    @Test fun shiftTabOutdents() {
        outdent("- a\n  - b|", "- a\n- b|")
        outdent("- a\n  [- b\n  - c]", "- a\n[- b\n- c]")
        outdent("- a\n - b|", "- a\n- b|") // only what's there
        outdent("\t- b|", "- b|")
        outdent("- a|", "- a|") // nothing to remove
        outdent("    so|me", "so|me")
        outdent("  | - b", "| - b") // cursor inside the indentation goes to the line start
    }
}

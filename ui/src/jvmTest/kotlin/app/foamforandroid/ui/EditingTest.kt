package app.foamforandroid.ui

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
}

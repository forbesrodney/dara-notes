package app.daranotes.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

// Tab and Shift+Tab in the editor, as in other Markdown editors: Tab indents the selected
// lines, or the list item the cursor is on, and otherwise types a tab; Shift+Tab outdents.
// Indenting is always with spaces, since a tab character means different widths to
// different Markdown tools.

private val listMarker = Regex("""^[ \t]*([-*+]|\d{1,9}[.)])[ \t]+""")

/**
 * What Tab does: indents the lines a selection touches, or nests the list item the cursor
 * is on; anywhere else, types a tab at the cursor.
 */
fun TextFieldValue.tab(): TextFieldValue {
    if (!selection.collapsed) return indentLines()
    val c = selection.min
    val lineStart = text.lastIndexOf('\n', c - 1) + 1
    val lineEnd = text.indexOf('\n', c).let { if (it < 0) text.length else it }
    if (listMarker.containsMatchIn(text.substring(lineStart, lineEnd))) return indentLines()
    return TextFieldValue(text.substring(0, c) + "\t" + text.substring(c), TextRange(c + 1))
}

/** The lines the cursor or selection touches, indented one level. */
fun TextFieldValue.indentLines(): TextFieldValue = shiftLines(outdent = false)

/** The lines the cursor or selection touches, outdented one level (as far as they can go). */
fun TextFieldValue.outdentLines(): TextFieldValue = shiftLines(outdent = true)

private class LineEdit(val at: Int, val removed: Int, val added: Int)

private fun TextFieldValue.shiftLines(outdent: Boolean): TextFieldValue {
    val starts = touchedLineStarts()
    val unit = indentUnit(text, starts.first())
    val edits = starts.mapNotNull { s ->
        val end = text.indexOf('\n', s).let { if (it < 0) text.length else it }
        // Blank lines in a selection stay empty, as in other editors; the cursor's own
        // line is indented even when empty, to start a nested item there.
        if (starts.size > 1 && text.substring(s, end).isBlank()) return@mapNotNull null
        if (outdent) {
            var n = 0
            if (text.getOrNull(s) == '\t') n = 1 else while (n < unit && s + n < end && text[s + n] == ' ') n++
            if (n == 0) null else LineEdit(s, n, 0)
        } else LineEdit(s, 0, unit)
    }
    if (edits.isEmpty()) return this
    val out = StringBuilder()
    var last = 0
    for (e in edits) {
        out.append(text, last, e.at).append(" ".repeat(e.added))
        last = e.at + e.removed
    }
    out.append(text, last, text.length)
    fun map(p: Int): Int {
        var q = p
        for (e in edits) when {
            p >= e.at + e.removed -> q += e.added - e.removed
            p > e.at -> q -= p - e.at // inside removed indentation: to the line's new start
        }
        return q
    }
    return TextFieldValue(out.toString(), TextRange(map(selection.start), map(selection.end)))
}

/**
 * Where each line the cursor or selection touches starts. A selection ending at the start
 * of a line (after its newline) doesn't take in that line.
 */
private fun TextFieldValue.touchedLineStarts(): List<Int> {
    val min = selection.min
    var max = selection.max
    if (max > min && text[max - 1] == '\n') max--
    val starts = mutableListOf(text.lastIndexOf('\n', min - 1) + 1)
    while (true) {
        val nl = text.indexOf('\n', starts.last())
        if (nl < 0 || nl >= max) break
        starts += nl + 1
    }
    return starts
}

/**
 * How many spaces one level is, for lines starting at [lineStart]. Under a list item, its
 * marker's width: 2 for "- ", 3 for "1. ", which is how far Markdown needs a nested item
 * indented (and markdownlint's default for bullets). Otherwise 4.
 */
private fun indentUnit(text: String, lineStart: Int): Int {
    fun width(line: String) = listMarker.find(line)?.let { it.groupValues[1].length + 1 }
    // The nearest list item above, through blank and indented lines (the list goes on).
    var end = lineStart - 1
    while (end >= 0) {
        val start = text.lastIndexOf('\n', end - 1) + 1
        val line = text.substring(start, end)
        if (line.isNotBlank()) {
            width(line)?.let { return it }
            if (!line[0].isWhitespace()) break
        }
        end = start - 1
    }
    val first = text.substring(lineStart, text.indexOf('\n', lineStart).let { if (it < 0) text.length else it })
    return width(first) ?: 4
}

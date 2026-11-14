package app.daranotes.core

/**
 * Adds and removes tags in a note's front matter, keeping the style the note already uses:
 *
 *     tags: [a, b]        a flow list (what the app writes when there's no tags line yet)
 *     tags: a, b          comma-separated
 *     tags: a b           space-separated
 *     tags:               a YAML list
 *       - a
 *       - b
 *
 * `tag:` works like `tags:`, as in Foam. Adding a tag to a note without front matter adds a
 * front matter block; removing the last tag removes the tags line, and the block too when
 * nothing else is left in it. Everything else in the note is left exactly as it was.
 */
object FrontMatterTags {
    /** Foam's tag characters: letters, digits, `_`, `-` and `/`, with at least one non-digit. */
    private val VALID = Regex("""[\p{L}\p{N}_/-]*[\p{L}_/-][\p{L}\p{N}_/-]*""")
    private val KEY = Regex("""^(\s*)(tags?)(\s*:\s*)(.*?)\s*$""")
    private val ITEM = Regex("""^(\s*)-\s+(.*?)\s*$""")

    /**
     * [input] as a tag: without a leading `#` or surrounding space, or null if it isn't one
     * Foam accepts (spaces and punctuation other than `_`, `-` and `/` aren't allowed).
     */
    fun normalize(input: String): String? {
        val t = input.trim().removePrefix("#").trim().trim('/')
        return t.takeIf { VALID.matches(it) }
    }

    /** The note's front matter tags, in order. */
    fun read(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").split("\n")
        val end = frontMatterEnd(lines) ?: return emptyList()
        return NoteParser.frontMatterTags(lines.subList(1, end))
    }

    /** [text] with [tag] added to its front matter, or unchanged if it's already there. */
    fun add(text: String, tag: String): String {
        val crlf = "\r\n" in text
        val lines = text.replace("\r\n", "\n").split("\n").toMutableList()
        if (read(text).any { it.equals(tag, ignoreCase = true) }) return text
        val end = frontMatterEnd(lines)
        if (end == null) {
            // No front matter yet: a block of its own at the top.
            return restore("---\ntags: [$tag]\n---\n" + lines.joinToString("\n"), crlf)
        }
        val keyLine = (1 until end).firstOrNull { i -> KEY.find(lines[i])?.let { it.groupValues[1].isEmpty() } == true }
        if (keyLine == null) {
            lines.add(end, "tags: [$tag]")
            return restore(lines.joinToString("\n"), crlf)
        }
        val m = KEY.find(lines[keyLine])!!
        val (indent, key, sep, value) = m.destructured
        if (value.isNotEmpty()) {
            lines[keyLine] = indent + key + sep + appended(value, tag)
            return restore(lines.joinToString("\n"), crlf)
        }
        // A YAML list: after its last item, written like the others.
        var last = keyLine
        while (last + 1 < end && ITEM.containsMatchIn(lines[last + 1])) last++
        if (last == keyLine) {
            lines[keyLine] = indent + key + sep.trimEnd() + " [$tag]"
        } else {
            val item = ITEM.find(lines[last])!!
            val q = quoteOf(item.groupValues[2])
            lines.add(last + 1, item.groupValues[1] + "- " + q + tag + q)
        }
        return restore(lines.joinToString("\n"), crlf)
    }

    /**
     * [text] with [tag] removed from its front matter (in any letter case), or unchanged if
     * it isn't there. An emptied tags line goes, and so does a front matter block left empty.
     */
    fun remove(text: String, tag: String): String {
        val crlf = "\r\n" in text
        val lines = text.replace("\r\n", "\n").split("\n").toMutableList()
        val end = frontMatterEnd(lines) ?: return text
        var i = 1
        var changed = false
        var fmEnd = end
        while (i < fmEnd) {
            val m = KEY.find(lines[i])
            if (m == null || m.groupValues[1].isNotEmpty()) { i++; continue }
            val (indent, key, sep, value) = m.destructured
            if (value.isNotEmpty()) {
                val kept = without(value, tag)
                if (kept != null) {
                    changed = true
                    if (kept.isEmpty()) { lines.removeAt(i); fmEnd--; continue }
                    lines[i] = indent + key + sep + kept
                }
                i++
                continue
            }
            // A YAML list: drop the matching items, and the key if none are left.
            var j = i + 1
            var items = 0
            var removedHere = false
            while (j < fmEnd && ITEM.containsMatchIn(lines[j])) {
                if (clean(ITEM.find(lines[j])!!.groupValues[2]).equals(tag, ignoreCase = true)) {
                    lines.removeAt(j); fmEnd--; changed = true; removedHere = true
                } else {
                    items++; j++
                }
            }
            if (items == 0 && removedHere) { lines.removeAt(i); fmEnd--; continue }
            i = j
        }
        if (!changed) return text
        if ((1 until fmEnd).all { lines[it].isBlank() }) {
            // Nothing left in the front matter: remove the block, and the blank lines after it.
            repeat(fmEnd + 1) { lines.removeAt(0) }
            while (lines.size > 1 && lines.first().isBlank()) lines.removeAt(0)
        }
        return restore(lines.joinToString("\n"), crlf)
    }

    /** The index of the front matter's closing line, if [lines] start with front matter. */
    private fun frontMatterEnd(lines: List<String>): Int? {
        if (lines.firstOrNull()?.trimEnd() != "---") return null
        return (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" || lines[it].trimEnd() == "..." }
    }

    /** An inline tags value with [tag] added, in the same style. */
    private fun appended(value: String, tag: String): String {
        if (value.startsWith("[") && value.endsWith("]")) {
            val inner = value.substring(1, value.length - 1).trim()
            if (inner.isEmpty()) return "[$tag]"
            val q = quoteOf(inner.split(',').first().trim())
            return "[$inner, $q$tag$q]"
        }
        val q = quoteOf(value.split(',', ' ').first().trim())
        return if (',' in value || ' ' !in value) "$value, $q$tag$q" else "$value $q$tag$q"
    }

    /**
     * An inline tags value without [tag], in the same style; empty if nothing is left, or
     * null if [tag] isn't in it.
     */
    private fun without(value: String, tag: String): String? {
        val flow = value.startsWith("[") && value.endsWith("]")
        val inner = if (flow) value.substring(1, value.length - 1) else value
        val commas = ',' in inner
        val parts = (if (commas) inner.split(',') else inner.split(' ')).map { it.trim() }.filter { it.isNotEmpty() }
        val kept = parts.filterNot { clean(it).equals(tag, ignoreCase = true) }
        if (kept.size == parts.size) return null
        if (kept.isEmpty()) return ""
        val joined = kept.joinToString(if (commas || flow) ", " else " ")
        return if (flow) "[$joined]" else joined
    }

    private fun clean(s: String) = s.trim().trim('"', '\'').removePrefix("#").trim()

    /** The quote character around [item], if it has one, so a new tag is written the same way. */
    private fun quoteOf(item: String): String = when {
        item.length >= 2 && item.startsWith('"') && item.endsWith('"') -> "\""
        item.length >= 2 && item.startsWith('\'') && item.endsWith('\'') -> "'"
        else -> ""
    }

    private fun restore(text: String, crlf: Boolean) = if (crlf) text.replace("\n", "\r\n") else text
}

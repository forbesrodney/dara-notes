package app.daranotes.core

/**
 * A note's table of contents: a list of links to its headings (H2 to H6), placed under the
 * note's H1, like
 *
 *     - [Snippet Syntax](#snippet-syntax)
 *       - [Tabstops](#tabstops)
 *
 * The links use GitHub-style heading IDs; the preview gives headings the same IDs
 * (web/preview.js, headingIds), so keep [slug] and [plainText] in step with it. A heading
 * marked `<!-- omit from toc -->` (or `<!-- omit in toc -->`), on its line or the line
 * above, is left out.
 */
object Toc {
    data class Heading(val level: Int, val text: String, val id: String, val line: Int, val omit: Boolean)

    /** A change to the note: [removed] characters at [start] replaced by [added] characters. */
    data class Edit(val text: String, val start: Int, val removed: Int, val added: Int)

    private val atx = Regex("""^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$""")
    private val setext = Regex("""^ {0,3}(=+|-+)[ \t]*$""")
    private val fence = Regex("""^ {0,3}(`{3,}|~{3,})""")
    private val omitMark = Regex("""<!--\s*omit (?:from|in) toc\s*-->""", RegexOption.IGNORE_CASE)
    private val tocItem = Regex("""^\s*(?:[-*+]|\d+[.)])[ \t]+\[.*]\(#[^)\s]*\)[ \t]*$""")
    private val notSetextText = Regex("""^ {0,3}(?:[-*+>|]|\d+[.)]|#|`{3}|~{3})""")

    /** The heading ID: lowercase, punctuation and symbols removed, spaces as hyphens. */
    fun slug(text: String): String = buildString {
        // Keeps letters, marks, numbers, connector punctuation (_), spaces and hyphens, as
        // github-slugger does. (Character checks, not a regex: Kotlin/Native's regex handles
        // this character class differently.)
        for (c in text.trim().lowercase()) {
            val keep = c == ' ' || c == '-' || c.isLetter() || c.category in keptCategories
            if (keep) append(if (c == ' ') '-' else c)
        }
    }

    private val keptCategories = setOf(
        CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK,
        CharCategory.DECIMAL_DIGIT_NUMBER, CharCategory.LETTER_NUMBER, CharCategory.OTHER_NUMBER,
        CharCategory.CONNECTOR_PUNCTUATION,
    )

    /** The heading's text as the preview shows it: Markdown formatting, HTML and images removed. */
    fun plainText(markdown: String): String {
        var s = markdown
        s = omitMark.replace(s, "")
        s = Regex("""<!--.*?-->""").replace(s, "")
        s = Regex("""!\[\[[^\]]*]]""").replace(s, "")                       // embeds
        s = Regex("""\[\[([^\]|]*)\|([^\]]*)]]""").replace(s) { it.groupValues[2] }
        s = Regex("""\[\[([^\]]*)]]""").replace(s) { it.groupValues[1] }
        s = Regex("""!\[[^\]]*]\([^)]*\)""").replace(s, "")                 // images
        s = Regex("""\[([^\]]*)]\([^)]*\)""").replace(s) { it.groupValues[1] }
        s = Regex("""`+([^`]*)`+""").replace(s) { it.groupValues[1] }
        s = Regex("""</?[A-Za-z][^>]*>""").replace(s, "")
        s = s.replace("*", "").replace("~~", "")
        s = Regex("""(?<![\p{L}\p{N}])_+|_+(?![\p{L}\p{N}])""").replace(s, "")
        s = Regex("""\\([!-/:-@\[-`{-~])""").replace(s) { it.groupValues[1] }
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        return s.trim().replace(Regex("""\s+"""), " ")
    }

    /** The note's headings in order, with unique IDs (a repeat gets -1, -2, ...). */
    fun headings(markdown: String): List<Heading> {
        val lines = markdown.split('\n').map { it.removeSuffix("\r") }
        val out = mutableListOf<Heading>()
        // Repeats numbered as GitHub's github-slugger does (and web/preview.js).
        val seen = mutableMapOf<String, Int>()
        fun add(level: Int, raw: String, line: Int) {
            val text = plainText(raw)
            val base = slug(text)
            var id = base
            while (id in seen) {
                seen[base] = seen.getValue(base) + 1
                id = "$base-${seen.getValue(base)}"
            }
            seen[id] = 0
            val omit = omitMark.containsMatchIn(raw) || (line > 0 && omitMark.containsMatchIn(lines[line - 1]) &&
                lines[line - 1].replace(omitMark, "").isBlank())
            out += Heading(level, text, id, line, omit)
        }
        var i = frontMatterEnd(lines)
        var openFence: String? = null
        while (i < lines.size) {
            val line = lines[i]
            val f = fence.find(line)
            if (openFence != null) {
                if (f != null && f.groupValues[1][0] == openFence[0] && f.groupValues[1].length >= openFence.length &&
                    line.trim().all { it == openFence!![0] }) openFence = null
            } else if (f != null) {
                openFence = f.groupValues[1]
            } else if (line.startsWith("    ") || line.startsWith("\t")) {
                // indented code
            } else {
                val m = atx.find(line)
                if (m != null) {
                    add(m.groupValues[1].length, m.groupValues[2], i)
                } else if (i + 1 < lines.size && line.isNotBlank() && !notSetextText.containsMatchIn(line) &&
                    setext.matches(lines[i + 1]) && (i == 0 || lines[i - 1].isBlank() || atx.matches(lines[i - 1]))
                ) {
                    add(if (lines[i + 1].trim()[0] == '=') 1 else 2, line.trim(), i)
                    i++
                }
            }
            i++
        }
        return out
    }

    /** The list for [markdown]'s H2 to H6 headings, nested by level; empty if there are none. */
    fun build(markdown: String, bullet: String = "-"): String {
        val items = headings(markdown).filter { it.level >= 2 && !it.omit }
        if (items.isEmpty()) return ""
        val top = items.minOf { it.level }
        return items.joinToString("\n") { h ->
            val label = h.text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
            "  ".repeat(h.level - top) + "$bullet [$label](#${h.id})"
        }
    }

    /** The lines of the note's existing table of contents (outside code blocks), if any. */
    fun find(markdown: String): IntRange? {
        val lines = markdown.split('\n').map { it.removeSuffix("\r") }
        var openFence: String? = null
        var i = frontMatterEnd(lines)
        while (i < lines.size) {
            val f = fence.find(lines[i])
            when {
                openFence != null -> if (f != null && f.groupValues[1][0] == openFence[0]) openFence = null
                f != null -> openFence = f.groupValues[1]
                tocItem.matches(lines[i]) && (i == 0 || !tocItem.matches(lines[i - 1])) -> {
                    var end = i
                    while (end + 1 < lines.size && tocItem.matches(lines[end + 1])) end++
                    // A table of contents starts at the left edge.
                    if (lines[i].first().let { it == '-' || it == '*' || it == '+' || it.isDigit() }) return i..end
                    i = end
                }
            }
            i++
        }
        return null
    }

    fun has(markdown: String) = find(markdown) != null

    /** Brings an existing table of contents up to date; null if there is none, or it's current. */
    fun update(markdown: String, bullet: String = "-"): Edit? {
        val range = find(markdown) ?: return null
        val toc = build(markdown, bullet).withLineEndingsOf(markdown)
        if (toc.isEmpty()) return null
        val (start, end) = lineSpan(markdown, range)
        if (markdown.substring(start, end).removeSuffix("\r") == toc.removeSuffix("\r")) return null
        return Edit(markdown.substring(0, start) + toc + markdown.substring(end), start, end - start, toc.length)
    }

    /**
     * Updates the table of contents, or adds one under the note's first H1 (or at the top,
     * after any front matter). Null if there's nothing to do: no H2 to H6 headings, or the
     * table of contents is already current.
     */
    fun insertOrUpdate(markdown: String, bullet: String = "-"): Edit? {
        if (has(markdown)) return update(markdown, bullet)
        val toc = build(markdown, bullet).withLineEndingsOf(markdown)
        if (toc.isEmpty()) return null
        val nl = if ("\r\n" in markdown) "\r\n" else "\n"
        val lines = markdown.split('\n')
        val h1 = headings(markdown).firstOrNull { it.level == 1 }
        // The line after the H1 (two for a setext H1), else after the front matter.
        val after = when {
            h1 == null -> frontMatterEnd(lines.map { it.removeSuffix("\r") })
            atx.matches(lines[h1.line].removeSuffix("\r")) -> h1.line + 1
            else -> h1.line + 2
        }
        var offset = lines.take(after).sumOf { it.length + 1 }.coerceAtMost(markdown.length)
        val before = when {
            offset >= markdown.length && !markdown.endsWith('\n') -> nl + nl // the H1 is the last line
            h1 == null -> ""
            else -> nl
        }
        // Skip blank lines below the H1, so the list sits one blank line under it.
        var skip = offset
        while (skip < markdown.length) {
            val nl = markdown.indexOf('\n', skip).let { if (it < 0) markdown.length else it }
            if (markdown.substring(skip, nl).isNotBlank()) break
            skip = if (nl < markdown.length) nl + 1 else nl
        }
        val rest = markdown.substring(skip)
        val insert = before + toc.removeSuffix("\r") + nl + (if (rest.isEmpty()) "" else nl)
        val text = markdown.substring(0, offset) + insert + rest
        return Edit(text, offset, skip - offset, insert.length)
    }

    /** With CRLF line endings if [markdown] uses them (each line, the last included, ends in \r). */
    private fun String.withLineEndingsOf(markdown: String) =
        if (isEmpty() || "\r\n" !in markdown) this else replace("\n", "\r\n") + "\r"

    /** Character offsets of [range]'s lines, without the last line's newline. */
    private fun lineSpan(markdown: String, range: IntRange): Pair<Int, Int> {
        val lines = markdown.split('\n')
        val start = lines.take(range.first).sumOf { it.length + 1 }
        val end = start + lines.subList(range.first, range.last + 1).sumOf { it.length + 1 } - 1
        return start to end
    }

    /** The first line after YAML front matter (0 if there is none). */
    private fun frontMatterEnd(lines: List<String>): Int {
        if (lines.isEmpty() || lines[0] != "---") return 0
        val close = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" } ?: return 0
        return close + 1
    }
}

package app.daranotes.core

/** Helpers for exporting and sharing a note outside the app. */
object Export {
    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
    private val REF_DEF = Regex("""^ {0,3}\[(?!\^)[^\]]+]:\s*\S.*$""")
    private val WIKILINK = Regex("""(!?)\[\[([^\[\]|]+?)(?:\|([^\[\]]*))?]]""")
    private val ANCHOR_LINK = Regex("""(?<!!)\[((?:[^\[\]]|\[[^\[\]]*])*)]\(\s*<?#[^)]*\)""")
    private val OMIT = Regex("""\s*<!--\s*omit (?:from|in) toc\s*-->""", RegexOption.IGNORE_CASE)

    /** A file name for the exported note: its name with [extension] (e.g. "pdf"). */
    fun fileName(notePath: String, extension: String): String =
        notePath.substringAfterLast('/').removeSuffix(".md").ifBlank { "Note" } + "." + extension

    /**
     * The note as text to send (in an email, say), where its links can't lead anywhere: no
     * front matter or Foam link definitions, wikilinks and links to its own headings (a table
     * of contents) as their text, and the rest of the Markdown as written. Code is untouched.
     */
    fun plainText(markdown: String): String {
        var lines = markdown.replace("\r\n", "\n").split("\n")
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" || lines[it].trim() == "..." }
            if (end != null) lines = lines.drop(end + 1)
        }
        var end = lines.size
        while (end > 0 && lines[end - 1].isBlank()) end--
        var start = end
        while (start > 0 && REF_DEF.matches(lines[start - 1])) start--
        if (start < end) lines = lines.subList(0, start)

        val out = ArrayList<String>()
        var fence: String? = null
        for (line in lines) {
            val f = FENCE.find(line)
            if (fence != null) {
                if (f != null && f.groupValues[1].startsWith(fence)) fence = null
                out += line
                continue
            }
            if (f != null) { fence = f.groupValues[1].take(3); out += line; continue }
            var s = WIKILINK.replace(line) { m -> m.groupValues[3].ifBlank { m.groupValues[2].trim() } }
            s = ANCHOR_LINK.replace(s) { it.groupValues[1] }
            out += OMIT.replace(s, "")
        }
        while (out.isNotEmpty() && out.first().isBlank()) out.removeAt(0)
        while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.lastIndex)
        return out.joinToString("\n") + "\n"
    }
}

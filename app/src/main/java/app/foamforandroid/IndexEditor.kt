package app.foamforandroid

/**
 * Edits the index note's text. Foam may keep a block of link reference definitions at the
 * end of the file (`[note]: <note.md> "Title"`, hidden in the preview); new entries go into
 * the list, not below that block, and get a matching definition when the block exists.
 */
object IndexEditor {
    private val wikiItem = Regex("""^(\s*)([-*+])\s+!?\[\[[^\]]+]].*$""")
    private val refDef = Regex("""^ {0,3}\[(?!\^)([^\]]+)]:\s*\S.*$""")

    /** Index of the first line of the trailing reference-definition block, or -1 if none. */
    private fun defBlockStart(lines: List<String>): Int {
        var i = lines.size - 1
        while (i >= 0 && lines[i].isBlank()) i--
        if (i < 0 || !refDef.matches(lines[i])) return -1
        while (i - 1 >= 0 && (refDef.matches(lines[i - 1]))) i--
        return i
    }

    /**
     * Adds `<bullet> [[id]]` after the last wikilink list item (using that item's bullet and
     * indent), or before the definitions block, or at the end. [definition] is added to the
     * end of an existing definitions block.
     */
    fun add(text: String, id: String, defaultBullet: String, definition: String?): String {
        val lines = text.split("\n").toMutableList()
        val defStart = defBlockStart(lines)
        val bodyEnd = if (defStart >= 0) defStart else lines.size

        if (defStart >= 0 && definition != null) {
            var lastDef = lines.size - 1
            while (lastDef >= 0 && lines[lastDef].isBlank()) lastDef--
            lines.add(lastDef + 1, definition)
        }

        val lastItem = (bodyEnd - 1 downTo 0).firstOrNull { wikiItem.matches(lines[it]) }
        if (lastItem != null) {
            val m = wikiItem.find(lines[lastItem])!!
            lines.add(lastItem + 1, "${m.groupValues[1]}${m.groupValues[2]} [[$id]]")
            return lines.joinToString("\n")
        }
        val item = "$defaultBullet [[$id]]"
        if (defStart >= 0) {
            var p = defStart - 1
            while (p >= 0 && lines[p].isBlank()) p--
            lines.addAll(p + 1, listOf("", item))
            return lines.joinToString("\n")
        }
        val sep = if (text.isEmpty() || text.endsWith("\n")) "" else "\n"
        return "$text$sep$item\n"
    }

    /** Removes list items linking to any of [ids] and definitions labelled with them. */
    fun remove(text: String, ids: Collection<String>): String {
        val lines = text.split("\n")
        val kept = lines.filterNot { line ->
            ids.any { id ->
                val e = Regex.escape(id)
                Regex("""^\s*[-*+]\s+\[\[\s*$e\s*(\|[^\]]*)?]]\s*$""", RegexOption.IGNORE_CASE).matches(line) ||
                    Regex("""^ {0,3}\[\s*$e\s*]:.*$""", RegexOption.IGNORE_CASE).matches(line)
            }
        }
        return kept.joinToString("\n")
    }

    /** A definition in Foam's style: `[id]: <path with spaces.md> "Title"`. */
    fun definition(id: String, relPath: String, title: String): String {
        val p = relPath.replace("&", "%26")
        val dest = if (p.contains(' ')) "<$p>" else p
        return "[$id]: $dest \"${title.replace("\"", "'")}\""
    }
}

package app.daranotes.core

/**
 * Foam's link reference definitions: for each wikilink in a note, a definition at the end of
 * the file (`[Shopping]: Shopping.md "Shopping"`), after a blank line, so the links also work
 * in plain Markdown viewers. Foam's `foam.edit.linkReferenceDefinitions` setting picks the
 * style; it writes them whenever a note is saved.
 */
object LinkReferences {
    enum class Mode { WITH_EXTENSIONS, WITHOUT_EXTENSIONS, OFF }

    private val REF_DEF = Regex("""^ {0,3}\[(?!\^)([^\]]+)]:\s*(<[^>]*>|\S+).*$""")

    /**
     * [text] with its trailing definitions block brought up to date for the note at [notePath],
     * or null if nothing changes. [files] are the repository's files and [title] gives a
     * note's title. Definitions that point elsewhere (a web page, say), or that the note uses
     * as `[text][label]`, are kept, and so are existing ones for the note's wikilinks.
     */
    fun update(text: String, notePath: String, files: Collection<String>, title: (String) -> String, mode: Mode): String? {
        if (mode == Mode.OFF) return null
        val lines = text.split("\n")
        var end = lines.size
        while (end > 0 && lines[end - 1].isBlank()) end--
        var start = end
        while (start > 0 && REF_DEF.matches(lines[start - 1].trimEnd('\r'))) start--
        var bodyEnd = start
        while (bodyEnd > 0 && lines[bodyEnd - 1].isBlank()) bodyEnd--
        val body = lines.subList(0, bodyEnd).joinToString("\n")
        val dir = RepoPaths.dir(notePath)

        val generated = LinkedHashMap<String, String>()
        for (label in NoteParser.wikilinkTexts(body)) {
            val key = label.lowercase()
            if (key in generated) continue
            val path = Wikilinks.resolve(label, files) ?: continue
            var rel = RepoPaths.relative(dir, path)
            if (mode == Mode.WITHOUT_EXTENSIONS) rel = rel.removeSuffix(".md")
            val name = if (path.endsWith(".md", ignoreCase = true)) title(path) else path.substringAfterLast('/')
            generated[key] = IndexEditor.definition(label, rel, name)
        }

        // Existing definitions stay as written (as Foam leaves them), in their order; the
        // missing ones go at the end and those for links no longer in the note go.
        val seen = HashSet<String>()
        val kept = lines.subList(start, end).map { it.trimEnd('\r') }.filter { line ->
            val m = REF_DEF.find(line) ?: return@filter false
            val label = m.groupValues[1]
            if (!seen.add(label.lowercase())) return@filter false
            if (label.lowercase() in generated) return@filter true
            val usedAsReference = Regex("""]\[\s*${Regex.escape(label)}\s*]""", RegexOption.IGNORE_CASE).containsMatchIn(body)
            usedAsReference || !pointsAtFile(m.groupValues[2], dir, files)
        }

        val defs = kept + generated.filterKeys { it !in seen }.values
        val newline = if (text.endsWith("\n")) "\n" else ""
        val result = if (defs.isEmpty()) body + newline
        else (if (body.isEmpty()) "" else "$body\n\n") + defs.joinToString("\n") + newline
        // Leave a note with no definitions to add or remove exactly as it was.
        if (defs.isEmpty() && start == end) return null
        return result.takeIf { it != text }
    }

    private fun pointsAtFile(dest: String, dir: String, files: Collection<String>): Boolean {
        val d = dest.removePrefix("<").removeSuffix(">").substringBefore('#')
        if (d.isEmpty() || Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""").containsMatchIn(d)) return false
        val p = RepoPaths.join(dir, RepoPaths.percentDecode(d) ?: d) ?: return false
        return files.any { it.equals(p, ignoreCase = true) || it.equals("$p.md", ignoreCase = true) }
    }
}

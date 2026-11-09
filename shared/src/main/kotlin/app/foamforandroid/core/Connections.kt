package app.foamforandroid.core

/** A link written in a note, and what it points to. */
data class NoteLink(
    /** 1-based line number in the note. */
    val line: Int,
    val kind: Kind,
    /** The link exactly as written, e.g. `[[Plan#Goals|the plan]]`. */
    val written: String,
    /** What the link names: a wikilink target, a path or a URL (no `#heading` or alias). */
    val target: String,
    /** `#heading` part, without the `#`, if any. */
    val heading: String?,
    /** Repository path the link resolves to, for [Status.NOTE] and [Status.FILE]. */
    val resolved: String?,
    val status: Status,
) {
    enum class Kind { WIKILINK, EMBED, LINK, IMAGE, URL }

    enum class Status {
        /** Another note in the repository. */
        NOTE,
        /** A non-note file in the repository (image, PDF, …). */
        FILE,
        /** Nothing in the repository matches: a placeholder or a broken link. */
        MISSING,
        /** A web address or other URL. */
        EXTERNAL,
        /** A heading in this same note. */
        SAME_NOTE,
    }
}

/** A link to a note from another note. */
data class Backlink(val source: String, val line: Int, val context: String)

/**
 * Foam's Connections panel: every link in a note (wikilinks, embeds, Markdown links and
 * images, autolinks and bare URLs) with what it points to, plus the notes linking to it.
 * Code blocks, inline code and link reference definitions are skipped.
 */
object Connections {
    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
    private val INLINE_CODE = Regex("""(`+)(?:(?!\1).)+?\1""")
    private val REF_DEFINITION = Regex("""^ {0,3}\[[^\]]+]:\s""")
    private val WIKILINK = Regex("""(!?)\[\[([^\[\]|]*)(?:\|([^\[\]]*))?]]""")
    private val MD_LINK = Regex("""(!?)\[((?:[^\[\]]|\[[^\[\]]*])*)]\(\s*(<[^>]*>|[^)\s]*)(?:\s+(?:"[^"]*"|'[^']*'))?\s*\)""")
    private val AUTOLINK = Regex("""<([A-Za-z][A-Za-z0-9+.-]*:[^<>\s]+)>""")
    private val BARE_URL = Regex("""\bhttps?://[^\s<>()\[\]]+[^\s<>()\[\].,;:!?'"]""")
    private val SCHEME = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")

    /** All links in [text], the note at [path], in order. [files] is every file in the repository. */
    fun links(text: String, path: String, files: Collection<String>): List<NoteLink> {
        val fileSet = files.toHashSet()
        val out = ArrayList<NoteLink>()
        val lines = text.replace("\r\n", "\n").split("\n")
        var i = 0
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" || lines[it].trim() == "..." }
            if (end != null) i = end + 1
        }
        var fence: String? = null
        while (i < lines.size) {
            val lineNo = i + 1
            val line = lines[i++]
            val f = FENCE.find(line)
            if (fence != null) {
                if (f != null && f.groupValues[1].startsWith(fence)) fence = null
                continue
            }
            if (f != null) { fence = f.groupValues[1].take(3); continue }
            if (REF_DEFINITION.containsMatchIn(line)) continue
            // Blank out code (keeping positions) so links inside it are ignored.
            var rest = INLINE_CODE.replace(line) { " ".repeat(it.value.length) }
            val found = ArrayList<Pair<Int, NoteLink>>()

            WIKILINK.findAll(rest).forEach { m ->
                val raw = m.groupValues[2].trim()
                val target = raw.substringBefore('#').trim()
                val heading = raw.substringAfter('#', "").trim().ifEmpty { null }
                val kind = if (m.groupValues[1] == "!") NoteLink.Kind.EMBED else NoteLink.Kind.WIKILINK
                val (resolved, status) = when {
                    target.isEmpty() -> null to NoteLink.Status.SAME_NOTE
                    else -> Wikilinks.resolve(target, files)?.let { it to statusOf(it) } ?: (null to NoteLink.Status.MISSING)
                }
                found += m.range.first to NoteLink(lineNo, kind, m.value, target, heading, resolved, status)
            }
            rest = blank(rest, WIKILINK)

            MD_LINK.findAll(rest).forEach { m ->
                var dest = m.groupValues[3]
                if (dest.startsWith("<")) dest = dest.removePrefix("<").removeSuffix(">")
                val kind = if (m.groupValues[1] == "!") NoteLink.Kind.IMAGE else NoteLink.Kind.LINK
                found += m.range.first to markdownLink(lineNo, kind, line.substring(m.range), dest, path, fileSet)
            }
            rest = blank(rest, MD_LINK)

            AUTOLINK.findAll(rest).forEach { m ->
                found += m.range.first to NoteLink(lineNo, NoteLink.Kind.URL, m.value, m.groupValues[1], null, null, NoteLink.Status.EXTERNAL)
            }
            rest = blank(rest, AUTOLINK)

            BARE_URL.findAll(rest).forEach { m ->
                found += m.range.first to NoteLink(lineNo, NoteLink.Kind.URL, m.value, m.value, null, null, NoteLink.Status.EXTERNAL)
            }
            out += found.sortedBy { it.first }.map { it.second }
        }
        return out
    }

    /** Notes linking to [path] (from [notes], note texts keyed by path), with the line each link is on. */
    fun backlinks(path: String, notes: Map<String, String>, files: Collection<String>): List<Backlink> {
        val out = ArrayList<Backlink>()
        for ((source, text) in notes) {
            if (source == path) continue
            val lines by lazy { text.replace("\r\n", "\n").split("\n") }
            links(text, source, files)
                .filter { it.status == NoteLink.Status.NOTE && it.resolved == path }
                .distinctBy { it.line }
                .forEach { out += Backlink(source, it.line, lines[it.line - 1].trim()) }
        }
        return out.sortedWith(compareBy({ it.source.lowercase() }, { it.line }))
    }

    private fun markdownLink(
        line: Int, kind: NoteLink.Kind, written: String, dest: String, path: String, files: Set<String>,
    ): NoteLink {
        if (dest.startsWith("#")) {
            return NoteLink(line, kind, written, "", dest.drop(1).ifEmpty { null }, null, NoteLink.Status.SAME_NOTE)
        }
        if (SCHEME.containsMatchIn(dest)) {
            return NoteLink(line, kind, written, dest, null, null, NoteLink.Status.EXTERNAL)
        }
        val heading = dest.substringAfter('#', "").ifEmpty { null }
        var target = dest.substringBefore('#').substringBefore('?')
        target = runCatching { java.net.URLDecoder.decode(target.replace("+", "%2B"), "UTF-8") }.getOrDefault(target)
        val joined = if (target.startsWith("/")) RepoPaths.join("", target.trimStart('/'))
        else RepoPaths.join(RepoPaths.dir(path), target)
        val resolved = joined?.let { j -> listOf(j, "$j.md").firstOrNull { it in files } }
        return NoteLink(line, kind, written, target, heading, resolved, resolved?.let(::statusOf) ?: NoteLink.Status.MISSING)
    }

    private fun statusOf(path: String) =
        if (path.endsWith(".md", ignoreCase = true)) NoteLink.Status.NOTE else NoteLink.Status.FILE

    private fun blank(s: String, re: Regex) = re.replace(s) { " ".repeat(it.value.length) }
}

package app.daranotes.core


/** Foam-style wikilink resolution over a list of repository-relative paths. */
object Wikilinks {
    /**
     * Resolves a wikilink target the way Foam does: by file name (case-insensitive, with or
     * without extension), or by a path suffix like `folder/note`. Returns a relative path.
     */
    fun resolve(target: String, files: Collection<String>): String? {
        val t = target.substringBefore('#').substringBefore('|').trim().removePrefix("/").removePrefix("./")
        if (t.isEmpty()) return null
        val tl = t.lowercase()
        val candidates = listOf(tl, "$tl.md")
        return files
            .filter { p ->
                val pl = p.lowercase()
                candidates.any { c -> pl == c || pl.endsWith("/$c") }
            }
            .minWithOrNull(compareBy<String> { it.count { ch -> ch == '/' } }.thenBy { it })
    }

    /** The shortest wikilink identifier that resolves uniquely to [path]. */
    fun linkId(path: String, files: Collection<String>): String {
        val noExt = path.removeSuffix(".md")
        val name = noExt.substringAfterLast('/')
        val clashes = files.count { it.lowercase().removeSuffix(".md").substringAfterLast('/') == name.lowercase() }
        return if (clashes <= 1) name else noExt
    }
}

/** Repository-relative path helpers ('/' separated, no leading slash). */
object RepoPaths {
    /**
     * Decodes `%XX` escapes as UTF-8, as in a Markdown link destination (`+` stays a plus).
     * Returns null if an escape is malformed.
     */
    fun percentDecode(s: String): String? {
        if ('%' !in s) return s
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                val hex = s.substring(i + 1, minOf(i + 3, s.length))
                if (hex.length != 2) return null
                val v = hex.toIntOrNull(16) ?: return null
                bytes.add(v.toByte()); i += 3
            } else {
                c.toString().encodeToByteArray().forEach { bytes.add(it) }; i++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    fun dir(path: String) = path.substringBeforeLast('/', "")

    /** Joins [base] dir and a relative [rel] path, resolving `.` and `..`. Null if it escapes the repo. */
    fun join(base: String, rel: String): String? {
        val parts = ArrayList<String>()
        if (base.isNotEmpty()) parts += base.split('/')
        for (seg in rel.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
                else -> parts += seg
            }
        }
        return parts.joinToString("/")
    }

    /** Relative path from directory [fromDir] to [target]. */
    fun relative(fromDir: String, target: String): String {
        val from = if (fromDir.isEmpty()) emptyList() else fromDir.split('/')
        val to = target.split('/')
        var i = 0
        while (i < from.size && i < to.size - 1 && from[i] == to[i]) i++
        val ups = List(from.size - i) { ".." }
        return (ups + to.drop(i)).joinToString("/")
    }
}

/**
 * Rewrites links after the note [oldPath] moves to [newPath]:
 *  - wikilinks (`[[x]]`, `[[x|alias]]`, `[[x#h]]`, `![[x]]`) that pointed at the note, plus any
 *    others whose resolution would change because of the new name;
 *  - relative Markdown links and link reference definitions pointing at the note;
 *  - relative links inside the moved note itself, whose folder changed.
 * Code blocks and inline code are left alone.
 */
class LinkFixer(
    filesBefore: Collection<String>,
    private val oldPath: String,
    private val newPath: String,
) {
    private val before = filesBefore.toList()
    private val after = before.map { if (it == oldPath) newPath else it }
    private val beforeLower = before.map { it.lowercase() }.toSet()

    data class Result(val text: String, val changes: Int)

    /** [fileAfter] is where the text lives now; [fileBefore] where it lived before the move. */
    fun fix(text: String, fileBefore: String, fileAfter: String): Result {
        var changes = 0
        val out = StringBuilder()
        var fence: String? = null
        for ((i, line) in text.split("\n").withIndex()) {
            if (i > 0) out.append('\n')
            val fenceMatch = FENCE.find(line)
            if (fence != null) {
                if (fenceMatch != null && fenceMatch.groupValues[1].startsWith(fence)) fence = null
                out.append(line)
                continue
            }
            if (fenceMatch != null) {
                fence = fenceMatch.groupValues[1].take(3)
                out.append(line)
                continue
            }
            val refDef = REF_DEF.find(line)
            if (refDef != null) {
                val (newLine, n) = fixRefDef(line, refDef, fileBefore, fileAfter)
                out.append(newLine); changes += n
                continue
            }
            // Only touch text outside inline code spans.
            var last = 0
            for (code in INLINE_CODE.findAll(line)) {
                val (seg, n) = fixSegment(line.substring(last, code.range.first), fileBefore, fileAfter)
                out.append(seg).append(code.value); changes += n
                last = code.range.last + 1
            }
            val (seg, n) = fixSegment(line.substring(last), fileBefore, fileAfter)
            out.append(seg); changes += n
        }
        return Result(out.toString(), changes)
    }

    private fun fixSegment(s: String, fileBefore: String, fileAfter: String): Pair<String, Int> {
        var n = 0
        var r = WIKILINK.replace(s) { m ->
            val target = m.groupValues[2]
            val newTarget = fixWikiTarget(target)
            if (newTarget == null) m.value else {
                n++
                m.groupValues[1] + "[[" + newTarget + m.groupValues[3] + m.groupValues[4] + "]]"
            }
        }
        r = MD_LINK.replace(r) { m ->
            val newDest = fixDestination(m.groupValues[2], fileBefore, fileAfter)
            if (newDest == null) m.value else {
                n++
                m.groupValues[1] + "(" + newDest + m.groupValues[3] + ")"
            }
        }
        return r to n
    }

    private fun fixRefDef(line: String, m: MatchResult, fileBefore: String, fileAfter: String): Pair<String, Int> {
        val label = m.groupValues[2]
        if (label.startsWith("^")) return fixSegment(line, fileBefore, fileAfter) // footnote, not a link
        val dest = m.groupValues[4]
        val newDest = fixDestination(dest, fileBefore, fileAfter) ?: return line to 0
        // Foam's generated definitions use the wikilink id as the label; keep them in step.
        val newLabel = if (pointsAtOld(dest, fileBefore)) fixWikiTarget(label) else null
        val rebuilt = m.groupValues[1] + "[" + (newLabel ?: label) + "]:" + m.groupValues[3] +
            newDest + m.groupValues[5]
        return rebuilt to 1
    }

    /** New text for a wikilink target, or null if it needs no change. */
    private fun fixWikiTarget(target: String): String? {
        val t = target.trim()
        val was = Wikilinks.resolve(t, before) ?: return null
        val want = if (was == oldPath) newPath else was
        if (Wikilinks.resolve(t, after) == want) return null
        val keepExt = t.endsWith(".md", ignoreCase = true)
        val id = if (t.contains('/')) want.removeSuffix(".md") else Wikilinks.linkId(want, after)
        return if (keepExt) "$id.md" else id
    }

    private class Dest(
        val angled: Boolean, val decoded: String, val suffix: String,
        val rooted: Boolean, val noExt: Boolean, val actual: String?,
    )

    private fun parseDest(dest: String, fileBefore: String): Dest? {
        val angled = dest.startsWith("<") && dest.endsWith(">")
        val raw = if (angled) dest.substring(1, dest.length - 1) else dest
        if (raw.isEmpty() || raw.startsWith("#") || SCHEME.containsMatchIn(raw)) return null
        val cut = raw.indexOfAny(charArrayOf('#', '?')).let { if (it < 0) raw.length else it }
        val pathPart = raw.substring(0, cut)
        val decoded = RepoPaths.percentDecode(pathPart) ?: pathPart
        val rooted = decoded.startsWith("/")
        val resolved = RepoPaths.join(if (rooted) "" else RepoPaths.dir(fileBefore), decoded)
        // Foam's reference definitions may omit the extension.
        val noExt = resolved != null && resolved.lowercase() !in beforeLower && "$resolved.md".lowercase() in beforeLower
        val actual = resolved?.let { if (noExt) "$it.md" else it }?.takeIf { it.lowercase() in beforeLower }
        return Dest(angled, decoded, raw.substring(cut), rooted, noExt, actual)
    }

    private fun pointsAtOld(dest: String, fileBefore: String) =
        parseDest(dest, fileBefore)?.actual?.equals(oldPath, ignoreCase = true) == true

    /** New destination for a Markdown link, or null if it needs no change. */
    private fun fixDestination(dest: String, fileBefore: String, fileAfter: String): String? {
        val d = parseDest(dest, fileBefore) ?: return null
        val actual = d.actual ?: return null // only links to files that exist are rewritten
        val angled = d.angled
        val decoded = d.decoded
        val suffix = d.suffix
        val rooted = d.rooted
        val noExt = d.noExt
        val target = when {
            actual.equals(oldPath, ignoreCase = true) -> newPath
            fileBefore != fileAfter -> actual // the linking note itself moved
            else -> return null
        }
        var rel = if (rooted) "/$target" else RepoPaths.relative(RepoPaths.dir(fileAfter), target)
        if (noExt) rel = rel.removeSuffix(".md")
        if (rel == decoded) return null
        val encoded = if (angled) rel else rel.replace("%", "%25").replace(" ", "%20")
        return (if (angled) "<$encoded" else encoded) + suffix + (if (angled) ">" else "")
    }

    companion object {
        private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
        private val INLINE_CODE = Regex("""(`+)(?:(?!\1).)+?\1""")
        /** !? [[ target #heading |alias ]] */
        private val WIKILINK = Regex("""(!?)\[\[([^\[\]|#]+)(#[^\[\]|]*)?(\|[^\[\]]*)?]]""")
        /** [text](dest "title") and ![alt](dest) */
        private val MD_LINK = Regex("""(!?\[(?:[^\[\]]|\[[^\[\]]*])*])\(\s*(<[^>]+>|[^)\s]+)((?:\s+"[^"]*")?\s*)\)""")
        private val REF_DEF = Regex("""^( {0,3})\[([^\]]+)]:(\s*)(<[^>]+>|\S+)(.*)$""")
        private val SCHEME = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")
    }
}

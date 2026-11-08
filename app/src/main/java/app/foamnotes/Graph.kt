package app.foamnotes

import org.json.JSONArray
import org.json.JSONObject

/** The note graph, as Foam shows it: notes, tags and placeholders (links to missing notes). */
data class Graph(val nodes: List<Node>, val links: List<Link>) {
    enum class Type(val key: String) { NOTE("note"), TAG("tag"), PLACEHOLDER("placeholder") }

    data class Node(val id: String, val type: Type, val label: String, val path: String? = null)
    data class Link(val source: String, val target: String)

    fun toJson(): String {
        val degree = HashMap<String, Int>()
        for (l in links) {
            degree.merge(l.source, 1, Int::plus)
            degree.merge(l.target, 1, Int::plus)
        }
        val ns = JSONArray()
        for (n in nodes) {
            ns.put(
                JSONObject().put("id", n.id).put("type", n.type.key).put("label", n.label)
                    .put("path", n.path ?: JSONObject.NULL).put("degree", degree[n.id] ?: 0),
            )
        }
        val ls = JSONArray()
        for (l in links) ls.put(JSONObject().put("source", l.source).put("target", l.target))
        return JSONObject().put("nodes", ns).put("links", ls).toString()
    }

    companion object {
        fun tagId(tag: String) = "tag:" + tag.lowercase()
        fun placeholderId(target: String) = "placeholder:" + target.lowercase()

        /**
         * Builds the graph from note texts keyed by repository path ([titles] gives each note's
         * display title). [allFiles] is every file, for resolving links.
         */
        fun build(notes: Map<String, String>, titles: Map<String, String>, allFiles: List<String>): Graph {
            val nodes = LinkedHashMap<String, Node>()
            val links = LinkedHashSet<Link>()
            for (path in notes.keys) {
                nodes[path] = Node(path, Type.NOTE, titles[path] ?: path.substringAfterLast('/').removeSuffix(".md"), path)
            }
            for ((path, text) in notes) {
                val parsed = NoteParser.parse(text)
                for (target in parsed.wikilinks) {
                    val resolved = Wikilinks.resolve(target, allFiles)
                    when {
                        resolved == null -> {
                            val id = placeholderId(target)
                            nodes.getOrPut(id) { Node(id, Type.PLACEHOLDER, target) }
                            links += Link(path, id)
                        }
                        resolved != path && resolved in nodes -> links += Link(path, resolved)
                        // links to attachments (images, PDFs) aren't part of the graph
                    }
                }
                for (dest in parsed.markdownLinks) {
                    val resolved = RepoPaths.join(RepoPaths.dir(path), dest) ?: continue
                    val target = listOf(resolved, "$resolved.md").firstOrNull { it in nodes && nodes[it]?.type == Type.NOTE }
                    if (target != null && target != path) links += Link(path, target)
                }
                for (tag in parsed.tags) {
                    val id = tagId(tag)
                    nodes.getOrPut(id) { Node(id, Type.TAG, "#$tag") }
                    links += Link(path, id)
                }
            }
            return Graph(nodes.values.toList(), links.toList())
        }
    }
}

/** Extracts wikilinks, relative Markdown links and tags from a note, skipping code. */
object NoteParser {
    data class Parsed(val wikilinks: List<String>, val markdownLinks: List<String>, val tags: Set<String>)

    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
    private val INLINE_CODE = Regex("""(`+)(?:(?!\1).)+?\1""")
    private val WIKILINK = Regex("""!?\[\[([^\[\]|#]+)(?:#[^\[\]|]*)?(?:\|[^\[\]]*)?]]""")
    private val MD_LINK = Regex("""\[[^\]]*]\(\s*(<[^>]+>|[^)\s]+)""")
    private val SCHEME = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")

    /** Foam's hashtag rule: after start/whitespace, letters/digits/_/-//, at least one non-digit. */
    private val HASHTAG = Regex("""(?<=^|\s)#([\p{L}\p{N}_/-]*[\p{L}_/-][\p{L}\p{N}_/-]*)""")

    fun parse(text: String): Parsed {
        val lines = text.replace("\r\n", "\n").split("\n")
        val tags = LinkedHashSet<String>()
        var i = 0
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" || lines[it].trim() == "..." }
            if (end != null) {
                tags += frontMatterTags(lines.subList(1, end))
                i = end + 1
            }
        }
        val wikilinks = ArrayList<String>()
        val mdLinks = ArrayList<String>()
        var fence: String? = null
        while (i < lines.size) {
            val line = lines[i++]
            val f = FENCE.find(line)
            if (fence != null) {
                if (f != null && f.groupValues[1].startsWith(fence)) fence = null
                continue
            }
            if (f != null) { fence = f.groupValues[1].take(3); continue }
            val prose = INLINE_CODE.replace(line, " ")
            WIKILINK.findAll(prose).forEach { wikilinks += it.groupValues[1].trim() }
            MD_LINK.findAll(prose).forEach { m ->
                var d = m.groupValues[1]
                if (d.startsWith("<")) d = d.removePrefix("<").removeSuffix(">")
                if (d.startsWith("#") || SCHEME.containsMatchIn(d)) return@forEach
                d = d.substringBefore('#').substringBefore('?')
                d = runCatching { java.net.URLDecoder.decode(d.replace("+", "%2B"), "UTF-8") }.getOrDefault(d)
                if (d.isNotEmpty()) mdLinks += d
            }
            // Headings start with "# " (a space), so they never match a tag.
            HASHTAG.findAll(WIKILINK.replace(prose, " ")).forEach { tags += it.groupValues[1].trimEnd('/') }
        }
        return Parsed(wikilinks, mdLinks, tags.filter { it.isNotEmpty() }.toSet())
    }

    /** `tags: [a, b]`, `tags: a, b`, `tags: a b` or a YAML list under `tags:`. */
    internal fun frontMatterTags(fm: List<String>): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < fm.size) {
            val m = Regex("""^(tags?)\s*:\s*(.*)$""").find(fm[i])
            i++
            if (m == null) continue
            val inline = m.groupValues[2].trim()
            if (inline.isNotEmpty()) {
                out += inline.trim('[', ']').split(',', ' ').map(::cleanTag).filter { it.isNotEmpty() }
            } else {
                while (i < fm.size && Regex("""^\s*-\s+""").containsMatchIn(fm[i])) {
                    out += cleanTag(fm[i].trim().removePrefix("-"))
                    i++
                }
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun cleanTag(s: String) = s.trim().trim('"', '\'').removePrefix("#").trim()
}

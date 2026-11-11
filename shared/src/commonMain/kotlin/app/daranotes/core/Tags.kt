package app.daranotes.core

/**
 * A tag in Foam's Tag Explorer. Tags with slashes nest: `dev/git` sits under `dev`, which
 * appears even if no note uses `dev` on its own.
 */
data class TagNode(
    /** Last part of the tag, e.g. "git". */
    val name: String,
    /** Full tag, e.g. "dev/git". */
    val tag: String,
    /** Notes (paths) tagged with exactly this tag, sorted. */
    val notes: List<String>,
    val children: List<TagNode>,
    /** Distinct notes tagged with this tag or any tag below it. */
    val noteCount: Int,
)

object Tags {
    /**
     * Builds the tag tree from each note's tags (keyed by note path). Tags that differ only
     * in case are merged, as in the graph; the first spelling seen is shown. Sorted by name.
     */
    fun tree(tagsByNote: Map<String, Set<String>>): List<TagNode> {
        val spelling = HashMap<String, String>()          // lowercase full tag -> display
        val notes = HashMap<String, MutableSet<String>>()  // lowercase full tag -> paths
        for ((path, tags) in tagsByNote.entries.sortedBy { it.key }) {
            for (raw in tags) {
                val tag = raw.trim('/')
                if (tag.isEmpty()) continue
                // Register the tag and every parent ("a/b/c" -> "a", "a/b", "a/b/c").
                val parts = tag.split('/').filter { it.isNotEmpty() }
                for (i in parts.indices) {
                    val prefix = parts.subList(0, i + 1).joinToString("/")
                    spelling.getOrPut(prefix.lowercase()) { prefix }
                }
                notes.getOrPut(parts.joinToString("/").lowercase()) { mutableSetOf() } += path
            }
        }

        fun build(parent: String?): List<TagNode> =
            spelling.keys
                .filter { key ->
                    val p = key.substringBeforeLast('/', "")
                    if (parent == null) !key.contains('/') else key.contains('/') && p == parent
                }
                .map { key ->
                    val kids = build(key)
                    val own = notes[key].orEmpty().sorted()
                    val all = HashSet(own)
                    fun collect(n: TagNode) { all += n.notes; n.children.forEach(::collect) }
                    kids.forEach(::collect)
                    val display = spelling.getValue(key)
                    TagNode(display.substringAfterLast('/'), display, own, kids, all.size)
                }
                .sortedBy { it.name.lowercase() }

        return build(null)
    }

    /**
     * The tree pruned to tags whose full name contains [query] (case-insensitive), keeping
     * their parents so they can still be reached. A blank query keeps everything.
     */
    fun filter(tree: List<TagNode>, query: String): List<TagNode> {
        val q = query.trim().removePrefix("#").lowercase()
        if (q.isEmpty()) return tree
        return tree.mapNotNull { n ->
            if (q in n.tag.lowercase()) n
            else filter(n.children, q).takeIf { it.isNotEmpty() }?.let { n.copy(children = it) }
        }
    }
}

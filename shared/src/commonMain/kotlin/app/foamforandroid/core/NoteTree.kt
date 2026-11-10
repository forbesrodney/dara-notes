package app.foamforandroid.core

/** A folder in the notes tree, like Foam's hierarchical Notes Explorer. */
data class FolderNode(
    /** Folder name, e.g. "2026"; empty for the repository root. */
    val name: String,
    /** Path from the repository root, e.g. "journal/2026"; empty for the root. */
    val path: String,
    /** Subfolders, sorted by name. */
    val folders: List<FolderNode>,
    /** Notes directly in this folder, sorted by title. */
    val notes: List<Note>,
    /** Notes in this folder and every folder below it. */
    val noteCount: Int,
)

object NoteTree {
    /** Arranges [notes] by folder. Only folders that contain notes (at any depth) appear. */
    fun build(notes: List<Note>): FolderNode = folder("", "", notes)

    private fun folder(name: String, path: String, notes: List<Note>): FolderNode {
        val prefix = if (path.isEmpty()) "" else "$path/"
        val here = ArrayList<Note>()
        val below = LinkedHashMap<String, MutableList<Note>>()
        for (n in notes) {
            val rest = n.path.removePrefix(prefix)
            val slash = rest.indexOf('/')
            if (slash < 0) here += n else below.getOrPut(rest.substring(0, slash)) { ArrayList() } += n
        }
        val folders = below.entries
            .sortedBy { it.key.lowercase() }
            .map { (sub, ns) -> folder(sub, prefix + sub, ns) }
        return FolderNode(
            name, path, folders,
            here.sortedWith(compareBy({ it.title.lowercase() }, { it.path })),
            notes.size,
        )
    }
}

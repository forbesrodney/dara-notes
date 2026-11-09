package app.foamforandroid.core

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

data class Note(
    /** Path relative to the repository root, using '/' separators. */
    val path: String,
    val title: String,
    val modified: Long,
) {
    /** Foam identifier: file name without extension. */
    val id: String get() = path.substringAfterLast('/').removeSuffix(".md")
}

/** Repository name from a clone URL: `https://github.com/me/dev-notes.git` → `dev-notes`. */
internal fun repoName(url: String): String? =
    url.trim().trimEnd('/').substringAfterLast('/').substringAfterLast(':').removeSuffix(".git").ifBlank { null }

/** File-level operations on the notes repository (the local clone at [root]), following Foam conventions. */
class NoteRepository(val root: File, private val settings: Settings) {
    private val foam get() = FoamConfig(root)

    private val ignoredDirs = setOf(".git", ".foam", ".vscode", ".github", "node_modules", "_site", "_layouts")
    private val titleCache = HashMap<String, Pair<Long, String>>()

    private companion object {
        const val INVALID_CHARS = "\\:*?\"<>|"
    }

    fun file(path: String) = File(root, path)

    // ---- listing -------------------------------------------------------------------------

    fun allFiles(): List<String> {
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .onEnter { it == root || it.name !in ignoredDirs }
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
    }

    fun listNotes(): List<Note> = allFiles()
        .filter { it.endsWith(".md", ignoreCase = true) }
        .map { path ->
            val f = file(path)
            Note(path, titleOf(path, f), f.lastModified())
        }
        .sortedByDescending { it.modified }

    @Synchronized
    private fun titleOf(path: String, f: File): String {
        val mod = f.lastModified()
        titleCache[path]?.let { (m, t) -> if (m == mod) return t }
        val title = extractTitle(runCatching { f.useLines { it.take(60).toList() } }.getOrDefault(emptyList()))
            ?: f.nameWithoutExtension
        titleCache[path] = mod to title
        return title
    }

    private fun extractTitle(lines: List<String>): String? {
        var i = 0
        if (lines.firstOrNull()?.trim() == "---") {
            i = 1
            while (i < lines.size && lines[i].trim() != "---") {
                val l = lines[i]
                if (l.startsWith("title:")) {
                    return l.removePrefix("title:").trim().trim('"', '\'').ifBlank { null }
                }
                i++
            }
            i++
        }
        return lines.drop(i).firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
    }

    fun search(query: String, notes: List<Note>): List<Note> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return notes
        val byName = notes.filter { q in it.title.lowercase() || q in it.path.lowercase() }
        val byContent = notes.filter { it !in byName && runCatching { q in file(it.path).readText().lowercase() }.getOrDefault(false) }
        return byName + byContent
    }

    // ---- read / write --------------------------------------------------------------------

    fun read(path: String): String = file(path).takeIf { it.exists() }?.readText() ?: ""

    fun write(path: String, text: String) {
        val f = file(path)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    fun delete(path: String) {
        file(path).delete()
        removeFromIndex(path)
    }

    // ---- wikilinks -----------------------------------------------------------------------

    /**
     * Resolves a wikilink target the way Foam does: by file name (case-insensitive, with or
     * without extension), or by a path suffix like `folder/note`. Returns a relative path.
     */
    fun resolve(target: String, files: List<String> = allFiles()): String? = Wikilinks.resolve(target, files)

    /** The shortest wikilink identifier that resolves uniquely to [path]. */
    fun linkId(path: String, files: List<String> = allFiles()): String = Wikilinks.linkId(path, files)

    // ---- graph ---------------------------------------------------------------------------

    /** Notes, tags and missing notes, with the links between them (Foam's graph). */
    fun graph(): Graph = graph(listNotes())

    private fun graph(notes: List<Note>): Graph = Graph.build(
        notes = notes.associate { it.path to read(it.path) },
        titles = notes.associate { it.path to it.title },
        allFiles = allFiles(),
    )

    /**
     * Orphaned notes, as in Foam's Orphans panel: no links to or from another note (tags don't
     * count). Paths matching Foam's `foam.orphans.exclude` globs are left out. Sorted by title.
     */
    fun orphans(): List<Note> {
        val notes = listNotes()
        val ids = graph(notes).orphans().toSet()
        val exclude = foam.orphansExclude
        return notes
            .filter { it.path in ids && exclude.none { g -> Globs.matches(g, it.path) } }
            .sortedBy { it.title.lowercase() }
    }

    /** Every link in the note at [path], and the notes that link to it (Foam's Connections). */
    fun connections(path: String): Pair<List<NoteLink>, List<Backlink>> {
        val files = allFiles()
        val notes = listNotes().associate { it.path to read(it.path) }
        return Connections.links(notes[path] ?: read(path), path, files) to
            Connections.backlinks(path, notes, files)
    }

    /** Tags as a tree, like Foam's Tag Explorer: front matter tags and inline #tags. */
    fun tags(): List<TagNode> =
        Tags.tree(listNotes().associate { it.path to NoteParser.parse(read(it.path)).tags })

    /** A missing note and the notes that link to it. */
    data class MissingNote(val target: String, val linkedFrom: List<Note>)

    /**
     * Placeholders, as in Foam's Placeholders panel: wikilink targets with no matching file,
     * with the notes that link to them. Links from paths matching Foam's
     * `foam.placeholders.exclude` globs are left out. Sorted by target.
     */
    fun placeholders(): List<MissingNote> {
        val notes = listNotes()
        val byPath = notes.associateBy { it.path }
        val exclude = foam.placeholdersExclude
        return graph(notes).placeholders().mapNotNull { p ->
            val from = p.sources
                .filter { src -> exclude.none { g -> Globs.matches(g, src) } }
                .mapNotNull { byPath[it] }
                .sortedBy { it.title.lowercase() }
            if (from.isEmpty()) null else MissingNote(p.label, from)
        }
    }

    // ---- rename / move -------------------------------------------------------------------

    /** Folders in the repository (excluding Git/Foam/VS Code internals), sorted. */
    fun folders(): List<String> {
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .onEnter { it == root || it.name !in ignoredDirs }
            .filter { it.isDirectory && it != root && it.name !in ignoredDirs }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    data class Relocated(val path: String, val links: Int, val notes: Int)

    /** Checks a destination path; returns an error message, or null if it is usable. */
    fun relocateError(oldPath: String, newPath: String): String? {
        val segments = newPath.split('/')
        return when {
            newPath.removeSuffix(".md").isBlank() -> "Name can't be empty"
            segments.any { it.isBlank() || it == "." || it == ".." } -> "Not a valid path"
            segments.any { seg -> seg.any { it in INVALID_CHARS } } -> "Names can't contain \\ : * ? \" < > |"
            segments.first() in ignoredDirs -> "Can't move into ${segments.first()}"
            newPath == oldPath -> "That's where it already is"
            !newPath.equals(oldPath, ignoreCase = true) &&
                allFiles().any { it.equals(newPath, ignoreCase = true) } -> "$newPath already exists"
            else -> null
        }
    }

    /**
     * Renames or moves a note and fixes every link to it (wikilinks, Markdown links, Foam's
     * reference definitions), plus the relative links inside the note itself.
     */
    fun relocate(oldPath: String, newPathRaw: String): Relocated {
        val newPath = newPathRaw.trim().trim('/').let { if (it.endsWith(".md", ignoreCase = true)) it else "$it.md" }
        relocateError(oldPath, newPath)?.let { throw IllegalArgumentException(it) }
        val from = file(oldPath)
        if (!from.exists()) throw IllegalArgumentException("$oldPath no longer exists")

        val before = allFiles()
        val fixer = LinkFixer(before, oldPath, newPath)
        val to = file(newPath)
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) throw IllegalStateException("Couldn't move $oldPath")
        titleCache.remove(oldPath)

        var links = 0
        var notes = 0
        for (path in allFiles().filter { it.endsWith(".md", ignoreCase = true) }) {
            val text = read(path)
            val r = fixer.fix(text, if (path == newPath) oldPath else path, path)
            if (r.changes > 0 && r.text != text) {
                write(path, r.text)
                links += r.changes
                notes++
            }
        }
        // Remove folders the move left empty (Git doesn't track empty folders anyway).
        var dir = from.parentFile
        while (dir != null && dir != root && dir.listFiles()?.isEmpty() == true) {
            dir.delete()
            dir = dir.parentFile
        }
        return Relocated(newPath, links, notes)
    }

    /** Creates a note for a dangling wikilink, as Foam does when following one. */
    fun createFromWikilink(target: String): String {
        val name = target.substringBefore('#').substringBefore('|').trim()
        val path = uniquePath(joinPath(if (name.contains('/')) "" else settings.newNoteDir, "$name.md"))
        createNote(path, name.substringAfterLast('/'))
        return path
    }

    // ---- creation ------------------------------------------------------------------------

    /** Creates a note from a title (file name is the slugified title). */
    fun createNoteFromTitle(title: String): String {
        val template = foam.template("new-note")
        val fromTemplate = template?.filepath?.let { templatePath(FoamVariables.resolve(it, title)) }
        val path = uniquePath(fromTemplate ?: joinPath(settings.newNoteDir, FoamVariables.slugify(title) + ".md"))
        createNote(path, title)
        return path
    }

    private fun createNote(path: String, title: String) {
        val template = foam.template("new-note")
        val body = if (template != null) FoamVariables.resolve(template.body, title)
        else "# $title\n\n"
        write(path, body)
        addToIndex(path)
    }

    /** Opens (creating if needed) the daily note for [date]. Returns its path. */
    fun dailyNote(date: LocalDate = LocalDate.now()): String {
        val now = LocalDateTime.of(date, java.time.LocalTime.now())
        val cfg = foam
        val template = cfg.template("daily-note")
        val title = DateFormat.format(cfg.dailyTitleFormat, now)
        val path = template?.filepath?.let { templatePath(FoamVariables.resolve(it, title, now)) }
            ?: run {
                val dir = settings.dailyDirOverride.ifBlank { cfg.dailyDirectory ?: "journal" }
                val name = DateFormat.format(cfg.dailyFilenameFormat, now) + "." + cfg.dailyFileExtension
                joinPath(dir, name)
            }
        if (!file(path).exists()) {
            val body = if (template != null) FoamVariables.resolve(template.body, title, now)
            else "# $title\n\n"
            write(path, body)
            if (settings.indexDailyNotes) addToIndex(path)
        }
        return path
    }

    /**
     * Turns a template `filepath` into a repository-relative path. Foam treats a leading
     * `/<workspace-folder>/` as that workspace folder, so `/dev-notes/x.md` in a repo named
     * dev-notes means `x.md` at the repository root, not a dev-notes subfolder.
     */
    internal fun templatePath(raw: String): String {
        val path = raw.replace('\\', '/')
        if (!path.startsWith("/")) return path
        val trimmed = path.trimStart('/')
        val first = trimmed.substringBefore('/', missingDelimiterValue = "")
        val repoName = repoName(settings.remoteUrl)
        return if (first.isNotEmpty() && repoName != null && first.equals(repoName, ignoreCase = true))
            trimmed.substringAfter('/') else trimmed
    }

    private fun joinPath(dir: String, name: String): String {
        val d = dir.trim().trim('/')
        return if (d.isEmpty()) name else "$d/$name"
    }

    private fun uniquePath(path: String): String {
        if (!file(path).exists()) return path
        val base = path.removeSuffix(".md")
        var n = 2
        while (file("$base-$n.md").exists()) n++
        return "$base-$n.md"
    }

    // ---- Index.md ------------------------------------------------------------------------

    private val indexPath get() = settings.indexFile.trim().trimStart('/').ifBlank { "Index.md" }

    private fun isIndex(path: String) = path.equals(indexPath, ignoreCase = true)

    /**
     * Links a new note from the index (`- [[id]]`), unless it is already linked. The entry goes
     * at the end of the index's list of links, above Foam's link definitions; if the index has
     * those definitions, a matching one is added too.
     */
    fun addToIndex(path: String) {
        if (isIndex(path) || !path.endsWith(".md")) return
        val id = linkId(path)
        val f = file(indexPath)
        val text = if (f.exists()) f.readText() else "# Index\n\n"
        if (linkPattern(id).containsMatchIn(text)) return
        val rel = RepoPaths.relative(RepoPaths.dir(indexPath), path)
        val def = IndexEditor.definition(id, rel, titleOf(path, file(path)))
        write(indexPath, IndexEditor.add(text, id, settings.bullet, def))
    }

    fun removeFromIndex(path: String) {
        val f = file(indexPath)
        if (!f.exists()) return
        val ids = setOf(path.substringAfterLast('/').removeSuffix(".md"), path.removeSuffix(".md"))
        val text = f.readText()
        val updated = IndexEditor.remove(text, ids)
        if (updated != text) write(indexPath, updated)
    }

    /** Adds every note not yet linked from the index. Returns the number added. */
    fun rebuildIndex(): Int {
        val files = allFiles()
        val dailyDir = (settings.dailyDirOverride.ifBlank { foam.dailyDirectory ?: "journal" }).trim('/')
        var added = 0
        for (path in files.filter { it.endsWith(".md") }.sorted()) {
            if (isIndex(path)) continue
            if (!settings.indexDailyNotes && dailyDir.isNotEmpty() && path.startsWith("$dailyDir/")) continue
            val before = file(indexPath).takeIf { it.exists() }?.readText()
            addToIndex(path)
            if (file(indexPath).readText() != before) added++
        }
        return added
    }

    private fun linkPattern(id: String) =
        Regex("\\[\\[\\s*${Regex.escape(id)}(\\.md)?\\s*(\\||#|]])", RegexOption.IGNORE_CASE)

    // ---- tasks ---------------------------------------------------------------------------

    /** Toggles the checkbox on 0-based [line] of [text]. */
    fun toggleTask(text: String, line: Int): String {
        val lines = text.split("\n").toMutableList()
        if (line !in lines.indices) return text
        lines[line] = Tasks.toggle(lines[line], TaskStyle.from(settings)) ?: return text
        return lines.joinToString("\n")
    }
}

package app.daranotes.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okio.FileSystem
import okio.Path

/**
 * File-level operations on the notes repository (the local clone at [root]), following Foam
 * conventions. [fs] is the file system ([FileSystem.SYSTEM] in the apps, a fake in tests).
 */
class NoteRepository(val fs: FileSystem, val root: Path, private val settings: Settings) {
    /** Foam's settings and templates in this repository. Re-read on each use. */
    val foam get() = FoamConfig(fs, root)

    /** The markdownlint configuration in this repository. */
    fun lintConfig(): LintConfig = LintConfig.find(fs, root)

    private val ignoredDirs = setOf(".git", ".foam", ".vscode", ".github", "node_modules", "_site", "_layouts")
    private val titleCache = HashMap<String, Pair<Long, String>>()

    private companion object {
        const val INVALID_CHARS = "\\:*?\"<>|"
    }

    fun file(path: String): Path = root / path

    private fun exists(path: String) = fs.exists(file(path))

    private fun modified(f: Path): Long = fs.metadataOrNull(f)?.lastModifiedAtMillis ?: 0L

    private fun relative(p: Path) = p.relativeTo(root).segments.joinToString("/")

    /** Files (or, with [folders], folders) under [dir], skipping Git/Foam/VS Code internals. */
    private fun walk(dir: Path, folders: Boolean, out: MutableList<Path> = mutableListOf()): List<Path> {
        for (child in runCatching { fs.list(dir) }.getOrDefault(emptyList())) {
            val meta = fs.metadataOrNull(child) ?: continue
            if (meta.isDirectory) {
                if (child.name in ignoredDirs) continue
                if (folders) out += child
                walk(child, folders, out)
            } else if (!folders && meta.isRegularFile) {
                out += child
            }
        }
        return out
    }

    // ---- listing -------------------------------------------------------------------------

    fun allFiles(): List<String> {
        if (!fs.exists(root)) return emptyList()
        return walk(root, folders = false).map(::relative)
    }

    fun listNotes(): List<Note> = allFiles()
        .filter { it.endsWith(".md", ignoreCase = true) }
        .map { path ->
            val f = file(path)
            Note(path, titleOf(path, f), modified(f))
        }
        .sortedByDescending { it.modified }

    private fun titleOf(path: String, f: Path): String = locked(titleCache) {
        val mod = modified(f)
        titleCache[path]?.let { (m, t) -> if (m == mod) return@locked t }
        val lines = runCatching {
            fs.read(f) { generateSequence { readUtf8Line() }.take(60).toList() }
        }.getOrDefault(emptyList())
        val title = extractTitle(lines) ?: f.name.substringBeforeLast('.')
        titleCache[path] = mod to title
        title
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
        val byContent = notes.filter { it !in byName && runCatching { q in read(it.path).lowercase() }.getOrDefault(false) }
        return byName + byContent
    }

    // ---- read / write --------------------------------------------------------------------

    fun read(path: String): String =
        runCatching { fs.read(file(path)) { readUtf8() } }.getOrDefault("")

    fun write(path: String, text: String) {
        val f = file(path)
        f.parent?.let(fs::createDirectories)
        fs.write(f) { writeUtf8(text) }
    }

    fun delete(path: String) {
        fs.delete(file(path), mustExist = false)
        if (settings.linkFromIndex) removeFromIndex(path)
    }

    // ---- wikilinks -----------------------------------------------------------------------

    /**
     * Resolves a wikilink target the way Foam does: by file name (case-insensitive, with or
     * without extension), or by a path suffix like `folder/note`. Returns a relative path.
     */
    fun resolve(target: String, files: List<String> = allFiles()): String? = Wikilinks.resolve(target, files)

    /** The shortest wikilink identifier that resolves uniquely to [path]. */
    fun linkId(path: String, files: List<String> = allFiles()): String = Wikilinks.linkId(path, files)

    /**
     * [text] (the note at [path]) with Foam's link reference definitions for its wikilinks
     * brought up to date, per the workspace's `foam.edit.linkReferenceDefinitions`.
     */
    fun withLinkReferences(path: String, text: String): String =
        LinkReferences.update(text, path, allFiles(), { titleOf(it, file(it)) }, foam.linkReferenceDefinitions) ?: text

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
        if (!fs.exists(root)) return emptyList()
        return walk(root, folders = true).map(::relative).sorted()
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
        if (!fs.exists(from)) throw IllegalArgumentException("$oldPath no longer exists")

        val before = allFiles()
        val fixer = LinkFixer(before, oldPath, newPath)
        val to = file(newPath)
        to.parent?.let(fs::createDirectories)
        runCatching { fs.atomicMove(from, to) }.onFailure { throw IllegalStateException("Couldn't move $oldPath", it) }
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
        var dir = from.parent
        while (dir != null && dir != root && runCatching { fs.list(dir).isEmpty() }.getOrDefault(false)) {
            fs.delete(dir)
            dir = dir.parent
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

    /**
     * Creates a note from a title: from the template at [templatePath] (relative to the
     * templates folder) if given, otherwise from new-note.md if that's turned on. The file is
     * the template's `filepath`, or the slugified title. A picked template that names the file
     * without the title (a daily note, say) opens that note instead if it's already there.
     */
    fun createNoteFromTitle(title: String, templatePath: String? = null): String {
        val template = if (templatePath != null) foam.templateAt(templatePath) else defaultNoteTemplate()
        val fromTemplate = template?.filepath?.let { templatePath(FoamVariables.resolve(it, title)) }
        if (templatePath != null && fromTemplate != null && template?.needsTitle == false && exists(fromTemplate)) return fromTemplate
        val path = uniquePath(fromTemplate ?: joinPath(settings.newNoteDir, FoamVariables.slugify(title) + ".md"))
        createNote(path, title, template)
        return path
    }

    private fun defaultNoteTemplate(): Template? =
        if (settings.useNewNoteTemplate) foam.template("new-note") else null

    private fun createNote(path: String, title: String, template: Template? = defaultNoteTemplate()) {
        // Without a template (turned off, or none) the note starts empty: the heading is the
        // template's to add.
        val body = template?.let { FoamVariables.resolve(it.body, title) } ?: ""
        write(path, body)
        if (settings.linkFromIndex) addToIndex(path)
    }

    /** Opens (creating if needed) the daily note for [date]. Returns its path. */
    fun dailyNote(date: LocalDate = Dates.today()): String {
        val now = LocalDateTime(date, Dates.now().time)
        val cfg = foam
        val template = if (settings.useDailyNoteTemplate) cfg.template("daily-note") else null
        val title = date.toString() // yyyy-mm-dd, as Foam names daily notes
        val path = template?.filepath?.let { templatePath(FoamVariables.resolve(it, title, now)) }
            ?: run {
                val dir = settings.dailyDirOverride.ifBlank { "journal" }
                val name = "$title.md"
                joinPath(dir, name)
            }
        if (!exists(path)) {
            // Without a template (turned off, or none) the note starts empty, as new notes do.
            val body = template?.let { FoamVariables.resolve(it.body, title, now) } ?: ""
            write(path, body)
            if (settings.linkFromIndex && settings.indexDailyNotes) addToIndex(path)
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
        if (!exists(path)) return path
        val base = path.removeSuffix(".md")
        var n = 2
        while (exists("$base-$n.md")) n++
        return "$base-$n.md"
    }

    // ---- Index.md ------------------------------------------------------------------------

    private val indexPath get() = settings.indexFile.trim().trimStart('/').ifBlank { "Index.md" }

    private fun isIndex(path: String) = path.equals(indexPath, ignoreCase = true)

    /**
     * What a missing index starts as, before its first link: new-note.md (titled after the
     * index's file name, and ending in a blank line so the list starts apart from it) if new
     * notes start from that, otherwise nothing. The template's `filepath` doesn't apply.
     */
    private fun newIndexText(): String {
        val template = defaultNoteTemplate() ?: return ""
        val title = indexPath.substringAfterLast('/').removeSuffix(".md")
        val body = FoamVariables.resolve(template.body, title).trimEnd('\n', '\r', ' ', '\t')
        return if (body.isEmpty()) "" else "$body\n\n"
    }

    /**
     * Links a new note from the index (`- [[id]]`), unless it is already linked. The entry goes
     * at the end of the index's list of links, above Foam's link definitions; if the index has
     * those definitions, a matching one is added too.
     */
    fun addToIndex(path: String) {
        if (isIndex(path) || !path.endsWith(".md")) return
        val id = linkId(path)
        val text = if (exists(indexPath)) read(indexPath) else newIndexText()
        if (linkPattern(id).containsMatchIn(text)) return
        val rel = RepoPaths.relative(RepoPaths.dir(indexPath), path)
        val def = IndexEditor.definition(id, rel, titleOf(path, file(path)))
        write(indexPath, IndexEditor.add(text, id, settings.bullet, def))
    }

    fun removeFromIndex(path: String) {
        if (!exists(indexPath)) return
        val ids = setOf(path.substringAfterLast('/').removeSuffix(".md"), path.removeSuffix(".md"))
        val text = read(indexPath)
        val updated = IndexEditor.remove(text, ids)
        if (updated != text) write(indexPath, updated)
    }

    /** Adds every note not yet linked from the index. Returns the number added. */
    fun rebuildIndex(): Int {
        val files = allFiles()
        val dailyDir = settings.dailyDirOverride.ifBlank { "journal" }.trim('/')
        var added = 0
        for (path in files.filter { it.endsWith(".md") }.sorted()) {
            if (isIndex(path)) continue
            if (!settings.indexDailyNotes && dailyDir.isNotEmpty() && path.startsWith("$dailyDir/")) continue
            val before = if (exists(indexPath)) read(indexPath) else null
            addToIndex(path)
            if (read(indexPath) != before) added++
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

package app.daranotes.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import okio.Path
import kotlinx.serialization.json.Json as JsonParser

/** Foam workspace conventions: settings, templates and template variables. */
class FoamConfig(private val fs: FileSystem, private val root: Path) {
    private val vscode: JsonObject by lazy {
        runCatching {
            val text = fs.read(root / ".vscode/settings.json") { readUtf8() }
            JsonParser.parseToJsonElement(stripJsonc(text)).jsonObject
        }.getOrElse { JsonObject(emptyMap()) }
    }

    /** Globs for notes Foam leaves out of its Orphans panel (`foam.orphans.exclude`). */
    val orphansExclude: List<String>
        get() = globs("foam.orphans.exclude")

    private fun globs(key: String): List<String> =
        (vscode[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.ifBlank { null } }
            ?: emptyList()

    /** Globs for notes whose links Foam leaves out of its Placeholders panel (`foam.placeholders.exclude`). */
    val placeholdersExclude: List<String>
        get() = globs("foam.placeholders.exclude")

    /**
     * How Foam writes link reference definitions for wikilinks (`foam.edit.linkReferenceDefinitions`):
     * "withExtensions" (Foam's default), "withoutExtensions" or "off".
     */
    val linkReferenceDefinitions: LinkReferences.Mode
        get() = when ((vscode["foam.edit.linkReferenceDefinitions"] as? JsonPrimitive)?.content?.trim()) {
            "off" -> LinkReferences.Mode.OFF
            "withoutExtensions" -> LinkReferences.Mode.WITHOUT_EXTENSIONS
            else -> LinkReferences.Mode.WITH_EXTENSIONS
        }

    private val templatesDir get() = root / TEMPLATES_DIR

    /** The template `.foam/templates/<name>.md` (or `<name>`), if it exists. */
    fun template(name: String): Template? {
        val f = listOf("$name.md", name).map { templatesDir / it }
            .firstOrNull { fs.metadataOrNull(it)?.isRegularFile == true } ?: return null
        return Template.parse(fs.read(f) { readUtf8() })
    }

    /** The template at [path], relative to the templates folder (as [templates] lists them). */
    fun templateAt(path: String): Template? {
        if (path.split('/').any { it == ".." || it.isEmpty() }) return null
        val f = templatesDir / path
        if (fs.metadataOrNull(f)?.isRegularFile != true) return null
        return runCatching { Template.parse(fs.read(f) { readUtf8() }) }.getOrNull()
    }

    /**
     * The Markdown templates in `.foam/templates` and its subfolders, by name, as Foam's
     * "Create New Note From Template" lists them. (Foam's JavaScript templates aren't supported.)
     */
    fun templates(): List<TemplateFile> {
        val out = mutableListOf<TemplateFile>()
        fun walk(dir: Path, prefix: String) {
            for (child in runCatching { fs.list(dir) }.getOrDefault(emptyList())) {
                val meta = fs.metadataOrNull(child) ?: continue
                val rel = prefix + child.name
                if (meta.isDirectory) walk(child, "$rel/")
                else if (meta.isRegularFile && child.name.endsWith(".md", ignoreCase = true)) {
                    val t = runCatching { Template.parse(fs.read(child) { readUtf8() }) }.getOrNull() ?: continue
                    out += TemplateFile(rel, t.name ?: rel.removeSuffix(".md"), t.description, t.needsTitle)
                }
            }
        }
        walk(templatesDir, "")
        return out.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    companion object {
        const val TEMPLATES_DIR = ".foam/templates"

        /** Removes // and /* */ comments and trailing commas from VS Code's JSONC. */
        fun stripJsonc(src: String): String {
            val out = StringBuilder()
            var i = 0
            var inString = false
            while (i < src.length) {
                val c = src[i]
                if (inString) {
                    out.append(c)
                    if (c == '\\' && i + 1 < src.length) { out.append(src[i + 1]); i += 2; continue }
                    if (c == '"') inString = false
                    i++
                } else if (c == '"') {
                    inString = true; out.append(c); i++
                } else if (src.startsWith("//", i)) {
                    while (i < src.length && src[i] != '\n') i++
                } else if (src.startsWith("/*", i)) {
                    val end = src.indexOf("*/", i + 2)
                    i = if (end < 0) src.length else end + 2
                } else {
                    out.append(c); i++
                }
            }
            return out.toString().replace(Regex(",(\\s*[}\\]])"), "$1")
        }
    }
}

/**
 * A template in the templates folder: [path] relative to it, the name to show, and whether
 * making a note from it needs a title (see [Template.needsTitle]).
 */
data class TemplateFile(val path: String, val name: String, val description: String?, val needsTitle: Boolean = true)

/**
 * A Foam template: optional `foam_template` front matter (filepath, and the name and
 * description Foam shows when picking one) plus a body.
 */
class Template(val filepath: String?, val body: String, val name: String? = null, val description: String? = null) {
    /**
     * Whether a note made from this needs a title: when it has no `filepath` (the file is
     * named after the title) or uses the title (`$FOAM_TITLE`, `$FOAM_TITLE_SAFE`, `$FOAM_SLUG`).
     * A daily-note template, named by the date, doesn't.
     */
    val needsTitle: Boolean
        get() = filepath == null || TITLE_VARIABLE.containsMatchIn(filepath) || TITLE_VARIABLE.containsMatchIn(body)

    companion object {
        private val TITLE_VARIABLE = Regex("""\$\{?FOAM_(?:TITLE|TITLE_SAFE|SLUG)\b""")

        fun parse(text: String): Template {
            val src = text.replace("\r\n", "\n")
            if (!src.startsWith("---\n")) return Template(null, src)
            val end = src.indexOf("\n---", 4)
            if (end < 0) return Template(null, src)
            val fmLines = src.substring(4, end).split("\n")
            val rest = src.substring(end + 4).removePrefix("\n")
            val fields = HashMap<String, String>()
            val kept = mutableListOf<String>()
            var inFoamBlock = false
            for (line in fmLines) {
                if (line.startsWith("foam_template:")) { inFoamBlock = true; continue }
                if (inFoamBlock && (line.startsWith(" ") || line.startsWith("\t") || line.isBlank())) {
                    val t = line.trim()
                    val key = t.substringBefore(':', "")
                    if (key in setOf("filepath", "name", "description")) {
                        fields[key] = t.substringAfter(':').trim().trim('"', '\'')
                    }
                    continue
                }
                inFoamBlock = false
                kept += line
            }
            // With the front matter gone, drop the blank lines that separated it from the body.
            val body = if (kept.all { it.isBlank() }) rest.replace(Regex("^(?:[ \t]*\n)+"), "")
            else "---\n" + kept.joinToString("\n") + "\n---\n" + rest
            return Template(
                fields["filepath"]?.ifBlank { null }, body,
                fields["name"]?.ifBlank { null }, fields["description"]?.ifBlank { null },
            )
        }
    }
}

object FoamVariables {
    fun resolve(text: String, title: String, now: LocalDateTime = Dates.now()): String {
        val pad = Dates::pad
        val month = now.month.number
        val day = now.dayOfWeek.isoDayNumber
        val vars = mapOf(
            "FOAM_TITLE" to title,
            "FOAM_SLUG" to slugify(title),
            "FOAM_TITLE_SAFE" to title.replace(Regex("[\\\\/:*?\"<>|#^\\[\\]]"), "-"),
            "FOAM_SELECTED_TEXT" to "",
            "FOAM_DATE_YEAR" to pad(now.year, 4),
            "FOAM_DATE_YEAR_SHORT" to pad(now.year % 100, 2),
            "FOAM_DATE_MONTH" to pad(month, 2),
            "FOAM_DATE_MONTH_NAME" to PlatformDates.monthName(month, short = false),
            "FOAM_DATE_MONTH_NAME_SHORT" to PlatformDates.monthName(month, short = true),
            "FOAM_DATE_DATE" to pad(now.day, 2),
            "FOAM_DATE_DAY_NAME" to PlatformDates.dayName(day, short = false),
            "FOAM_DATE_DAY_NAME_SHORT" to PlatformDates.dayName(day, short = true),
            "FOAM_DATE_HOUR" to pad(now.hour, 2),
            "FOAM_DATE_MINUTE" to pad(now.minute, 2),
            "FOAM_DATE_SECOND" to pad(now.second, 2),
            "FOAM_DATE_WEEK" to pad(Dates.isoWeek(now.date), 2),
            "FOAM_DATE_SECONDS_UNIX" to Dates.nowSeconds().toString(),
            "CURRENT_YEAR" to pad(now.year, 4),
            "CURRENT_MONTH" to pad(month, 2),
            "CURRENT_DATE" to pad(now.day, 2),
        )
        var out = Regex("\\$\\{([A-Z_]+)(?::([^}]*))?\\}").replace(text) { m ->
            vars[m.groupValues[1]] ?: m.groupValues[2]
        }
        out = Regex("\\$([A-Z_]+)").replace(out) { m -> vars[m.groupValues[1]] ?: m.value }
        // VS Code snippet tab stops: ${1:default} -> default, $1 / $0 -> ""
        out = Regex("\\$\\{\\d+:([^}]*)\\}").replace(out) { it.groupValues[1] }
        out = Regex("\\$\\{\\d+\\}|\\$\\d+").replace(out, "")
        return out
    }

    fun slugify(title: String): String =
        title.trim().lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .replace(Regex("[\\s_]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "untitled" }
}

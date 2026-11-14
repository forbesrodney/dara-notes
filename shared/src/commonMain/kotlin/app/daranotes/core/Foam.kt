@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.daranotes.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.uuid.Uuid
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
     * Leaves out new-note.md and daily-note.md: Foam uses those on its own, for every new note
     * and daily note.
     */
    fun templates(): List<TemplateFile> {
        val out = mutableListOf<TemplateFile>()
        fun walk(dir: Path, prefix: String) {
            for (child in runCatching { fs.list(dir) }.getOrDefault(emptyList())) {
                val meta = fs.metadataOrNull(child) ?: continue
                val rel = prefix + child.name
                if (meta.isDirectory) walk(child, "$rel/")
                else if (rel in DEFAULT_TEMPLATES) continue
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

        /** Foam's template for every new note, in [TEMPLATES_DIR]. */
        const val NEW_NOTE_TEMPLATE = "new-note.md"

        /** Foam's template for daily notes, in [TEMPLATES_DIR]. */
        const val DAILY_NOTE_TEMPLATE = "daily-note.md"

        /** The templates Foam applies by itself, which aren't offered for picking. */
        val DEFAULT_TEMPLATES = setOf(NEW_NOTE_TEMPLATE, DAILY_NOTE_TEMPLATE)

        /** Whether [path] (repository-relative) is in the templates folder. */
        fun isTemplate(path: String): Boolean = path.startsWith("$TEMPLATES_DIR/")

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
    /**
     * Fills in Foam's template variables. The `FOAM_DATE_*` ones are for [now], the note's
     * date (a daily note's day). VS Code's snippet date variables (`CURRENT_*`), which Foam
     * templates may use too, are for [current], the time the note is made, as in VS Code.
     * `TM_FILENAME_BASE` is the name of the note's file, [path], without `.md`; it stays as
     * it is without one (in a template's `filepath`, which names that file). `UUID`, `RANDOM`
     * and `RANDOM_HEX` are new each time they appear. Each variable is filled in once: a
     * value with a `$` in it (a title, say) is left as it is.
     */
    fun resolve(
        text: String,
        title: String,
        now: LocalDateTime = Dates.now(),
        path: String? = null,
        current: LocalDateTime = Dates.now(),
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
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
            "FOAM_DATE_DAY_ISO" to day.toString(), // 1 = Monday ... 7 = Sunday
            "FOAM_DATE_DAY_NAME" to PlatformDates.dayName(day, short = false),
            "FOAM_DATE_DAY_NAME_SHORT" to PlatformDates.dayName(day, short = true),
            "FOAM_DATE_HOUR" to pad(now.hour, 2),
            "FOAM_DATE_MINUTE" to pad(now.minute, 2),
            "FOAM_DATE_SECOND" to pad(now.second, 2),
            "FOAM_DATE_WEEK" to pad(Dates.isoWeek(now.date), 2),
            "FOAM_DATE_WEEK_YEAR" to Dates.isoWeekYear(now.date).toString(),
            "FOAM_DATE_SECONDS_UNIX" to now.toInstant(zone).epochSeconds.toString(),
        ) + currentVariables(current, zone) +
            listOfNotNull(path?.let { "TM_FILENAME_BASE" to it.substringAfterLast('/').substringBeforeLast('.') })

        fun value(name: String): String? = vars[name] ?: when (name) {
            "UUID" -> Uuid.random().toString()
            "RANDOM" -> (0 until 6).joinToString("") { Random.nextInt(10).toString() }
            "RANDOM_HEX" -> (0 until 6).joinToString("") { Random.nextInt(16).toString(16) }
            else -> null
        }
        // One pass, so nothing filled in is read again. ${NAME:default} falls back to default;
        // VS Code's tab stops ${1:text} become their text, and $1 or ${1} go.
        return SNIPPET.replace(text) { m ->
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> value(g[1]) ?: g[2]
                g[3].isNotEmpty() -> value(g[3]) ?: m.value
                else -> g[4]
            }
        }
    }

    private val SNIPPET = Regex(
        """\$\{([A-Z_]+)(?::([^}]*))?\}|\$([A-Z_]+)|\$\{\d+:([^}]*)\}|\$\{\d+\}|\$\d+"""
    )

    /** VS Code's snippet date variables, for [at]. */
    private fun currentVariables(at: LocalDateTime, zone: TimeZone): Map<String, String> {
        val pad = Dates::pad
        val month = at.month.number
        val day = at.dayOfWeek.isoDayNumber
        return mapOf(
            "CURRENT_YEAR" to pad(at.year, 4),
            "CURRENT_YEAR_SHORT" to pad(at.year % 100, 2),
            "CURRENT_MONTH" to pad(month, 2),
            "CURRENT_MONTH_NAME" to PlatformDates.monthName(month, short = false),
            "CURRENT_MONTH_NAME_SHORT" to PlatformDates.monthName(month, short = true),
            "CURRENT_DATE" to pad(at.day, 2),
            "CURRENT_DAY_NAME" to PlatformDates.dayName(day, short = false),
            "CURRENT_DAY_NAME_SHORT" to PlatformDates.dayName(day, short = true),
            "CURRENT_HOUR" to pad(at.hour, 2),
            "CURRENT_MINUTE" to pad(at.minute, 2),
            "CURRENT_SECOND" to pad(at.second, 2),
            "CURRENT_SECONDS_UNIX" to at.toInstant(zone).epochSeconds.toString(),
            "CURRENT_TIMEZONE_OFFSET" to Dates.offset(at, zone),
        )
    }

    /** A variable for the editor's Insert variable list: its group and what it becomes. */
    data class Info(val group: String, val name: String, val description: String)

    /** The variables worth inserting by hand, grouped as the help's Templates page has them. */
    val catalog: List<Info> = buildList {
        fun group(name: String, vararg vars: Pair<String, String>) = vars.forEach { (v, d) -> add(Info(name, v, d)) }
        group(
            "Title and file",
            "FOAM_TITLE" to "The title you enter",
            "FOAM_TITLE_SAFE" to "The title, safe as a file name",
            "FOAM_SLUG" to "The title for a file name or link: shopping-list",
            "TM_FILENAME_BASE" to "The note's file name without .md",
        )
        group(
            "The note's date",
            "FOAM_DATE_YEAR" to "Year: 2026",
            "FOAM_DATE_YEAR_SHORT" to "Year, two digits: 26",
            "FOAM_DATE_MONTH" to "Month: 11",
            "FOAM_DATE_MONTH_NAME" to "Month name: November",
            "FOAM_DATE_MONTH_NAME_SHORT" to "Short month name: Nov",
            "FOAM_DATE_DATE" to "Day of the month: 13",
            "FOAM_DATE_DAY_NAME" to "Day name: Friday",
            "FOAM_DATE_DAY_NAME_SHORT" to "Short day name: Fri",
            "FOAM_DATE_DAY_ISO" to "Day of the week, Monday = 1: 5",
            "FOAM_DATE_WEEK" to "ISO week number: 46",
            "FOAM_DATE_WEEK_YEAR" to "Year of the ISO week: 2026",
            "FOAM_DATE_HOUR" to "Hour, 24-hour clock: 14",
            "FOAM_DATE_MINUTE" to "Minute: 05",
            "FOAM_DATE_SECOND" to "Second: 09",
            "FOAM_DATE_SECONDS_UNIX" to "Seconds since 1970",
        )
        group(
            "The current time",
            "CURRENT_YEAR" to "Year: 2026",
            "CURRENT_YEAR_SHORT" to "Year, two digits: 26",
            "CURRENT_MONTH" to "Month: 11",
            "CURRENT_MONTH_NAME" to "Month name: November",
            "CURRENT_MONTH_NAME_SHORT" to "Short month name: Nov",
            "CURRENT_DATE" to "Day of the month: 13",
            "CURRENT_DAY_NAME" to "Day name: Friday",
            "CURRENT_DAY_NAME_SHORT" to "Short day name: Fri",
            "CURRENT_HOUR" to "Hour, 24-hour clock: 14",
            "CURRENT_MINUTE" to "Minute: 05",
            "CURRENT_SECOND" to "Second: 09",
            "CURRENT_SECONDS_UNIX" to "Seconds since 1970",
            "CURRENT_TIMEZONE_OFFSET" to "Time zone: -05:00",
        )
        group(
            "Random values",
            "UUID" to "A random UUID",
            "RANDOM" to "Six random digits",
            "RANDOM_HEX" to "Six random hexadecimal digits",
        )
    }

    /**
     * [v] with variable [name] inserted at the cursor (replacing any selection) as `$NAME`,
     * or as `${NAME}` when the text right after it would otherwise run into the name.
     * Returns the new text and the cursor position after it.
     */
    fun insert(text: String, selStart: Int, selEnd: Int, name: String): Pair<String, Int> {
        val start = minOf(selStart, selEnd).coerceIn(0, text.length)
        val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val next = text.getOrNull(end)
        val token = if (next != null && (next in 'A'..'Z' || next == '_')) "\${$name}" else "\$$name"
        return text.substring(0, start) + token + text.substring(end) to start + token.length
    }

    fun slugify(title: String): String =
        title.trim().lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .replace(Regex("[\\s_]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "untitled" }
}

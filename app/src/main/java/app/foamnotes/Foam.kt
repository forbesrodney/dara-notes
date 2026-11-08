package app.foamnotes

import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

/** Foam workspace conventions: daily note settings, templates and template variables. */
class FoamConfig(private val root: File) {
    private val vscode: JSONObject by lazy {
        val f = File(root, ".vscode/settings.json")
        if (!f.exists()) JSONObject()
        else runCatching { JSONObject(stripJsonc(f.readText())) }.getOrElse { JSONObject() }
    }

    val dailyDirectory: String?
        get() = vscode.optString("foam.openDailyNote.directory").ifBlank { null }
    val dailyFilenameFormat: String
        get() = vscode.optString("foam.openDailyNote.filenameFormat").ifBlank { "isoDate" }
    val dailyFileExtension: String
        get() = vscode.optString("foam.openDailyNote.fileExtension").ifBlank { "md" }
    val dailyTitleFormat: String
        get() = vscode.optString("foam.openDailyNote.titleFormat").ifBlank { "isoDate" }

    fun template(name: String): Template? {
        val dir = File(root, ".foam/templates")
        val f = listOf("$name.md", name).map { File(dir, it) }.firstOrNull { it.isFile } ?: return null
        return Template.parse(f.readText())
    }

    companion object {
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

/** A Foam template: optional `foam_template` front matter (filepath) plus a body. */
class Template(val filepath: String?, val body: String) {
    companion object {
        fun parse(text: String): Template {
            val src = text.replace("\r\n", "\n")
            if (!src.startsWith("---\n")) return Template(null, src)
            val end = src.indexOf("\n---", 4)
            if (end < 0) return Template(null, src)
            val fmLines = src.substring(4, end).split("\n")
            val rest = src.substring(end + 4).removePrefix("\n")
            var filepath: String? = null
            val kept = mutableListOf<String>()
            var inFoamBlock = false
            for (line in fmLines) {
                if (line.startsWith("foam_template:")) { inFoamBlock = true; continue }
                if (inFoamBlock && (line.startsWith(" ") || line.startsWith("\t") || line.isBlank())) {
                    val t = line.trim()
                    if (t.startsWith("filepath:")) {
                        filepath = t.removePrefix("filepath:").trim().trim('"', '\'')
                    }
                    continue
                }
                inFoamBlock = false
                kept += line
            }
            // With the front matter gone, drop the blank lines that separated it from the body.
            val body = if (kept.all { it.isBlank() }) rest.replace(Regex("^(?:[ \t]*\n)+"), "")
            else "---\n" + kept.joinToString("\n") + "\n---\n" + rest
            return Template(filepath, body)
        }
    }
}

object FoamVariables {
    fun resolve(text: String, title: String, now: LocalDateTime = LocalDateTime.now()): String {
        val en = Locale.getDefault()
        val vars = mapOf(
            "FOAM_TITLE" to title,
            "FOAM_SLUG" to slugify(title),
            "FOAM_TITLE_SAFE" to title.replace(Regex("[\\\\/:*?\"<>|#^\\[\\]]"), "-"),
            "FOAM_SELECTED_TEXT" to "",
            "FOAM_DATE_YEAR" to "%04d".format(now.year),
            "FOAM_DATE_YEAR_SHORT" to "%02d".format(now.year % 100),
            "FOAM_DATE_MONTH" to "%02d".format(now.monthValue),
            "FOAM_DATE_MONTH_NAME" to now.month.getDisplayName(TextStyle.FULL, en),
            "FOAM_DATE_MONTH_NAME_SHORT" to now.month.getDisplayName(TextStyle.SHORT, en),
            "FOAM_DATE_DATE" to "%02d".format(now.dayOfMonth),
            "FOAM_DATE_DAY_NAME" to now.dayOfWeek.getDisplayName(TextStyle.FULL, en),
            "FOAM_DATE_DAY_NAME_SHORT" to now.dayOfWeek.getDisplayName(TextStyle.SHORT, en),
            "FOAM_DATE_HOUR" to "%02d".format(now.hour),
            "FOAM_DATE_MINUTE" to "%02d".format(now.minute),
            "FOAM_DATE_SECOND" to "%02d".format(now.second),
            "FOAM_DATE_WEEK" to "%02d".format(now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)),
            "FOAM_DATE_SECONDS_UNIX" to (System.currentTimeMillis() / 1000).toString(),
            "CURRENT_YEAR" to "%04d".format(now.year),
            "CURRENT_MONTH" to "%02d".format(now.monthValue),
            "CURRENT_DATE" to "%02d".format(now.dayOfMonth),
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

/** Implements the `dateformat` masks Foam uses for daily note names (e.g. "yyyy-mm-dd"). */
object DateFormat {
    private val named = mapOf(
        "isoDate" to "yyyy-mm-dd",
        "shortDate" to "m/d/yy",
        "mediumDate" to "mmm d, yyyy",
        "longDate" to "mmmm d, yyyy",
        "fullDate" to "dddd, mmmm d, yyyy",
        "isoDateTime" to "yyyy-mm-dd'T'HH:MM:ss",
    )
    private val token = Regex("d{1,4}|m{1,4}|yy(?:yy)?|([HhMsTt])\\1?|W{1,2}|N|\"[^\"]*\"|'[^']*'")

    fun format(mask: String, t: LocalDateTime): String {
        val loc = Locale.getDefault()
        return token.replace(named[mask] ?: mask) { m ->
            val v = m.value
            when (v) {
                "d" -> t.dayOfMonth.toString()
                "dd" -> "%02d".format(t.dayOfMonth)
                "ddd" -> t.dayOfWeek.getDisplayName(TextStyle.SHORT, loc)
                "dddd" -> t.dayOfWeek.getDisplayName(TextStyle.FULL, loc)
                "m" -> t.monthValue.toString()
                "mm" -> "%02d".format(t.monthValue)
                "mmm" -> t.month.getDisplayName(TextStyle.SHORT, loc)
                "mmmm" -> t.month.getDisplayName(TextStyle.FULL, loc)
                "yy" -> "%02d".format(t.year % 100)
                "yyyy" -> "%04d".format(t.year)
                "H" -> t.hour.toString()
                "HH" -> "%02d".format(t.hour)
                "h" -> ((t.hour + 11) % 12 + 1).toString()
                "hh" -> "%02d".format((t.hour + 11) % 12 + 1)
                "M" -> t.minute.toString()
                "MM" -> "%02d".format(t.minute)
                "s" -> t.second.toString()
                "ss" -> "%02d".format(t.second)
                "t" -> if (t.hour < 12) "a" else "p"
                "tt" -> if (t.hour < 12) "am" else "pm"
                "T" -> if (t.hour < 12) "A" else "P"
                "TT" -> if (t.hour < 12) "AM" else "PM"
                "W" -> t.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()
                "WW" -> "%02d".format(t.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
                "N" -> t.dayOfWeek.value.toString()
                else -> v.substring(1, v.length - 1) // quoted literal
            }
        }
    }
}

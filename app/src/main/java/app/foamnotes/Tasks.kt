package app.foamnotes

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Completion date formats, as stored in settings. */
enum class DateOrder(val key: String, val label: String, val usesSeparator: Boolean = true) {
    YMD("ymd", "yyyy-mm-dd"),
    MDY("mdy", "mm-dd-yyyy"),
    DMY("dmy", "dd-mm-yyyy"),
    D_MON_Y("d_mon_y", "d mmm yyyy", usesSeparator = false),
    MON_D_Y("mon_d_y", "mmm d, yyyy", usesSeparator = false),
    Y_MON_D("y_mon_d", "yyyy-mmm-dd");

    /** The label with [sep] in place of '-' where this format uses the separator. */
    fun label(sep: String) = if (usesSeparator) label.replace("-", sep) else label

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

object CompletionDate {
    const val LOCALE = "locale"
    val separators = listOf("-", "/", ".")

    /** Order and separator of the device locale's short date, e.g. US → MDY "/", Germany → DMY ".". */
    fun localeDefaults(locale: Locale = Locale.getDefault()): Pair<DateOrder, String> {
        val pattern = runCatching {
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale,
            )
        }.getOrDefault("y-MM-dd")
        val y = pattern.indexOf('y')
        val m = pattern.indexOf('M')
        val d = pattern.indexOf('d')
        val order = when {
            y < 0 || m < 0 || d < 0 -> DateOrder.YMD
            y < m && m < d -> DateOrder.YMD
            m < d && d < y -> DateOrder.MDY
            d < m && m < y -> DateOrder.DMY
            else -> DateOrder.YMD
        }
        val sep = pattern.firstOrNull { !it.isLetter() && !it.isWhitespace() && it != '\'' }?.toString()
        return order to (sep?.takeIf { it in separators } ?: "-")
    }

    fun resolve(orderSetting: String, sepSetting: String): Pair<DateOrder, String> {
        val (lo, ls) = localeDefaults()
        val order = DateOrder.of(orderSetting) ?: lo
        val sep = sepSetting.takeIf { it in separators } ?: ls
        return order to sep
    }

    fun format(date: LocalDate, order: DateOrder, sep: String, locale: Locale = Locale.getDefault()): String {
        val y = "%04d".format(date.year)
        val m = "%02d".format(date.monthValue)
        val d = "%02d".format(date.dayOfMonth)
        val mon = date.month.getDisplayName(TextStyle.SHORT, locale)
        return when (order) {
            DateOrder.YMD -> "$y$sep$m$sep$d"
            DateOrder.MDY -> "$m$sep$d$sep$y"
            DateOrder.DMY -> "$d$sep$m$sep$y"
            DateOrder.D_MON_Y -> "${date.dayOfMonth} $mon $y"
            DateOrder.MON_D_Y -> "$mon ${date.dayOfMonth}, $y"
            DateOrder.Y_MON_D -> "$y$sep$mon$sep$d"
        }
    }

    /** The completion date as written into a note: the date in square brackets. */
    fun stamp(date: LocalDate, order: DateOrder, sep: String) = "[" + format(date, order, sep) + "]"

    /**
     * A trailing bracketed completion date in any supported format (so changing the format
     * later still works). Brackets keep it apart from dates you type yourself.
     */
    val trailing = Regex(
        """\s*\[(\d{4}([-/.])\d{2}\2\d{2}|\d{2}([-/.])\d{2}\3\d{4}|\d{1,2} [\p{L}.]+ \d{4}|[\p{L}.]+ \d{1,2}, \d{4}|\d{4}([-/.])[\p{L}.]+\4\d{2})]\s*$"""
    )
}

/** How checking an item changes its text. */
data class TaskStyle(
    val strike: Boolean,
    val addDate: Boolean,
    val order: DateOrder,
    val separator: String,
) {
    companion object {
        fun from(s: Settings): TaskStyle {
            val (order, sep) = CompletionDate.resolve(s.dateOrder, s.dateSeparator)
            return TaskStyle(s.strikeCompleted, s.dateOnComplete, order, sep)
        }
    }
}

object Tasks {
    /** prefix (indent, quotes, marker, "[") · mark · "]" + spacing · content */
    private val task = Regex("""^(\s*(?:>\s*)*(?:[-*+]|\d+[.)])\s+\[)([ xX])(]\s?)(.*)$""")

    fun isTask(line: String) = task.containsMatchIn(line)

    /** Checks or unchecks a task line. Returns null if [line] is not a task item. */
    fun toggle(line: String, style: TaskStyle, today: LocalDate = LocalDate.now()): String? {
        val m = task.find(line) ?: return null
        val (prefix, mark, close, content) = m.destructured
        val checking = mark == " "
        val newContent = if (checking) complete(content, style, today) else reopen(content, style)
        val sep = if (close.endsWith(" ") || newContent.isEmpty()) close else "$close "
        return prefix + (if (checking) "x" else " ") + sep + newContent
    }

    private fun complete(content: String, style: TaskStyle, today: LocalDate): String {
        var c = content.trimEnd()
        if (style.strike && c.isNotEmpty() && !(c.startsWith("~~") && c.endsWith("~~"))) c = "~~$c~~"
        if (style.addDate) {
            val date = CompletionDate.stamp(today, style.order, style.separator)
            c = if (c.isEmpty()) date else "$c $date"
        }
        return c
    }

    private fun reopen(content: String, style: TaskStyle): String {
        var c = content
        if (style.addDate) c = c.replace(CompletionDate.trailing, "")
        if (style.strike && c.length >= 4 && c.startsWith("~~") && c.endsWith("~~")) c = c.substring(2, c.length - 2)
        return c
    }
}

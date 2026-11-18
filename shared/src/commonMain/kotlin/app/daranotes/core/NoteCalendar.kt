@file:OptIn(ExperimentalTime::class)

package app.daranotes.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The notes the calendar shows on one day: its daily note(s), and other notes last edited then. */
data class CalendarDay(val daily: List<Note>, val edited: List<Note>)

/** Places notes on a calendar: daily notes on their date, other notes on the day of their last edit. */
object NoteCalendar {
    private val DATE_NAME = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * The day of the daily note [note], from its name (Foam names daily notes `yyyy-mm-dd`,
     * whatever folder they're in); null if it isn't one.
     */
    fun dailyDate(note: Note): LocalDate? =
        note.id.takeIf(DATE_NAME::matches)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * Every day with notes. A daily note appears only on its own date, not when it was last
     * edited; any other note appears on the day [times] says it was last edited, in [zone].
     */
    fun days(
        notes: List<Note>,
        times: EditTimes,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Map<LocalDate, CalendarDay> {
        val daily = HashMap<LocalDate, MutableList<Note>>()
        val edited = HashMap<LocalDate, MutableList<Pair<Long, Note>>>()
        for (note in notes) {
            val date = dailyDate(note)
            if (date != null) {
                daily.getOrPut(date, ::mutableListOf) += note
            } else {
                val at = times.of(note.path, note.modified)
                if (at <= 0L) continue
                val day = Instant.fromEpochMilliseconds(at).toLocalDateTime(zone).date
                edited.getOrPut(day, ::mutableListOf) += at to note
            }
        }
        return (daily.keys + edited.keys).associateWith { day ->
            CalendarDay(
                daily = daily[day].orEmpty().sortedBy { it.path },
                // Latest edit first, like the note list.
                edited = edited[day].orEmpty().sortedByDescending { it.first }.map { it.second },
            )
        }
    }

    /**
     * The days to lay out for the month starting [first]: whole weeks from the one holding
     * the 1st to the one holding the last day, weeks starting on [weekStart] (1 = Monday to
     * 7 = Sunday). Days outside the month are null.
     */
    fun monthGrid(first: LocalDate, weekStart: Int): List<List<LocalDate?>> {
        val next = first.plus(1, DateTimeUnit.MONTH)
        val lead = (first.dayOfWeek.isoDayNumber - weekStart + 7) % 7
        var day = first.minus(lead, DateTimeUnit.DAY)
        val weeks = mutableListOf<List<LocalDate?>>()
        while (day < next) {
            weeks += (0 until 7).map { i ->
                day.plus(i, DateTimeUnit.DAY).takeIf { it >= first && it < next }
            }
            day = day.plus(7, DateTimeUnit.DAY)
        }
        return weeks
    }
}

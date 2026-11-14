@file:OptIn(ExperimentalTime::class)

package app.daranotes.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The current date and time in the device's time zone, and small date helpers. */
object Dates {
    fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    fun today(): LocalDate = now().date

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /** [n] with leading zeros to [width] digits. */
    fun pad(n: Int, width: Int = 2): String = n.toString().padStart(width, '0')

    /** ISO 8601 week number: weeks start on Monday, and week 1 holds the year's first Thursday. */
    fun isoWeek(d: LocalDate): Int {
        val thursday = d.plus(4 - d.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
        return (thursday.dayOfYear - 1) / 7 + 1
    }

    /**
     * The year [isoWeek] belongs to, the year of that week's Thursday: around New Year it
     * can differ from the calendar year (2027-01-01 is in week 53 of 2026).
     */
    fun isoWeekYear(d: LocalDate): Int = d.plus(4 - d.dayOfWeek.isoDayNumber, DateTimeUnit.DAY).year

    /** The offset of [zone] from UTC at [at], as `+HH:MM` or `-HH:MM`, as VS Code writes it. */
    fun offset(at: LocalDateTime, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val seconds = zone.offsetAt(at.toInstant(zone)).totalSeconds
        val minutes = kotlin.math.abs(seconds) / 60
        return (if (seconds < 0) "-" else "+") + pad(minutes / 60) + ":" + pad(minutes % 60)
    }
}

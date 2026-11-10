@file:OptIn(ExperimentalTime::class)

package app.foamforandroid.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The current date and time in the device's time zone, and small date helpers. */
object Dates {
    fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    fun today(): LocalDate = now().date

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    fun nowSeconds(): Long = Clock.System.now().epochSeconds

    /** [n] with leading zeros to [width] digits. */
    fun pad(n: Int, width: Int = 2): String = n.toString().padStart(width, '0')

    /** ISO 8601 week number: weeks start on Monday, and week 1 holds the year's first Thursday. */
    fun isoWeek(d: LocalDate): Int {
        val thursday = d.plus(4 - d.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
        return (thursday.dayOfYear - 1) / 7 + 1
    }
}

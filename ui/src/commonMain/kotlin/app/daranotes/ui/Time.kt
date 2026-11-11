package app.daranotes.ui

import app.daranotes.core.Dates
import app.daranotes.core.PlatformDates
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** How long ago [millis] was, in words: "just now", "5 minutes ago", "yesterday", "Nov 3". */
fun relativeTime(millis: Long, now: Long = Dates.nowMillis()): String {
    val minutes = (now - millis) / 60_000
    if (minutes < 1) return "just now"
    if (minutes < 60) return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
    val hours = minutes / 60
    if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    val zone = TimeZone.currentSystemDefault()
    val then = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    val days = today.toEpochDays() - then.toEpochDays()
    if (days <= 1) return "yesterday"
    if (days < 7) return "$days days ago"
    val date = PlatformDates.monthName(then.month.number, short = true) + " " + then.day
    return if (then.year == today.year) date else "$date, ${then.year}"
}

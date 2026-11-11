package app.daranotes.core

import kotlinx.coroutines.CoroutineDispatcher

/** Runs [block] holding [lock], so threads can share a cache. */
internal expect fun <T> locked(lock: Any, block: () -> T): T

/** The dispatcher for blocking file and network work (Dispatchers.IO). */
expect val ioDispatcher: CoroutineDispatcher

/** The platform's Git implementation. */
internal expect fun platformGitEngine(): GitEngine

/** A hint for network errors the platform reports with its own exception types, else null. */
internal expect fun networkErrorHint(t: Throwable): String?

/** Month and weekday names and the short date pattern, in the device's language and region. */
expect object PlatformDates {
    /** [month] is 1 (January) to 12. */
    fun monthName(month: Int, short: Boolean): String

    /** [isoDay] is 1 (Monday) to 7 (Sunday). */
    fun dayName(isoDay: Int, short: Boolean): String

    /** The locale's short date pattern, e.g. "M/d/yy" in the US or "dd.MM.yy" in Germany. */
    fun shortDatePattern(): String
}

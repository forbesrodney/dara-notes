package app.foamforandroid.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.DayOfWeek
import java.time.Month
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

internal actual fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

internal actual fun platformGitEngine(): GitEngine = JGitEngine

internal actual fun networkErrorHint(t: Throwable): String? = when (t) {
    is UnknownHostException -> GitErrors.OFFLINE
    is SocketTimeoutException -> GitErrors.TIMEOUT
    else -> null
}

actual object PlatformDates {
    private fun style(short: Boolean) = if (short) TextStyle.SHORT else TextStyle.FULL

    actual fun monthName(month: Int, short: Boolean): String =
        Month.of(month).getDisplayName(style(short), Locale.getDefault())

    actual fun dayName(isoDay: Int, short: Boolean): String =
        DayOfWeek.of(isoDay).getDisplayName(style(short), Locale.getDefault())

    actual fun shortDatePattern(): String = runCatching {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT, null, IsoChronology.INSTANCE, Locale.getDefault(),
        )
    }.getOrDefault("y-MM-dd")
}

package app.foamforandroid.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSRecursiveLock
import platform.Foundation.currentLocale

// The iOS (and native macOS) pieces, using Foundation.

// One lock for everything: the shared caches are small and rarely contended. Recursive, so a
// locked block may call another.
private val globalLock = NSRecursiveLock()

internal actual fun <T> locked(lock: Any, block: () -> T): T {
    globalLock.lock()
    try {
        return block()
    } finally {
        globalLock.unlock()
    }
}

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

internal actual fun platformGitEngine(): GitEngine = LibGit2Engine

internal actual fun networkErrorHint(t: Throwable): String? = null

actual object PlatformDates {
    private fun formatter() = NSDateFormatter().apply { locale = NSLocale.currentLocale }

    actual fun monthName(month: Int, short: Boolean): String {
        val names = formatter().let { if (short) it.shortMonthSymbols else it.monthSymbols }
        return names.getOrNull(month - 1) as? String ?: month.toString()
    }

    // Foundation lists weekdays from Sunday; ISO numbers them from Monday (1) to Sunday (7).
    actual fun dayName(isoDay: Int, short: Boolean): String {
        val names = formatter().let { if (short) it.shortWeekdaySymbols else it.weekdaySymbols }
        return names.getOrNull(isoDay % 7) as? String ?: isoDay.toString()
    }

    actual fun shortDatePattern(): String =
        NSDateFormatter.dateFormatFromTemplate("yMd", 0u, NSLocale.currentLocale) ?: "y-MM-dd"
}

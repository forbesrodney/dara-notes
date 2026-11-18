package app.daranotes.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy
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

actual fun httpGet(url: String): ByteArray {
    val nsUrl = NSURL.URLWithString(url) ?: throw okio.IOException("Bad URL: $url")
    val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
        setTimeoutInterval(60.0)
        setValue("DaraNotes", forHTTPHeaderField = "User-Agent")
    }
    val done = dispatch_semaphore_create(0)
    var data: NSData? = null
    var status = 0L
    var error: String? = null
    NSURLSession.sharedSession.dataTaskWithRequest(request) { d, response, e ->
        data = d
        status = (response as? NSHTTPURLResponse)?.statusCode ?: 0L
        error = e?.localizedDescription
        dispatch_semaphore_signal(done)
    }.resume()
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER)
    error?.let { throw okio.IOException(it) }
    if (status !in 200L..299L) throw okio.IOException("HTTP $status from $url")
    val d = data ?: return ByteArray(0)
    val size = d.length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), d.bytes, d.length) } }
}

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

    // Foundation numbers weekdays from Sunday (1) to Saturday (7).
    actual fun firstDayOfWeek(): Int = NSCalendar.currentCalendar.firstWeekday.toInt().let { if (it == 1) 7 else it - 1 }

    actual fun shortDatePattern(): String =
        NSDateFormatter.dateFormatFromTemplate("yMd", 0u, NSLocale.currentLocale) ?: "y-MM-dd"
}

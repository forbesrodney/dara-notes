package app.foamforandroid.core

/** Runs [block] holding [lock], so threads can share a cache. */
internal expect fun <T> locked(lock: Any, block: () -> T): T

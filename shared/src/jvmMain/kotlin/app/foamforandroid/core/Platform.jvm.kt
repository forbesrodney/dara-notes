package app.foamforandroid.core

internal actual fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)

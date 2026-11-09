package app.foamforandroid.core

import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Plain-language hints for the Git errors people actually hit (wrong URL, bad token, offline…). */
object GitErrors {
    fun hint(t: Throwable): String? {
        val chain = generateSequence(t) { it.cause }.take(10).toList()
        if (chain.any { it is UnknownHostException }) return OFFLINE
        if (chain.any { it is SocketTimeoutException }) return TIMEOUT
        return hint(chain.mapNotNull { it.message }.joinToString(" "))
    }

    fun hint(message: String): String? {
        val m = message.lowercase()
        return when {
            "not permitted" in m -> REFUSED
            "not authorized" in m || "authentication is required" in m || "authentication not supported" in m -> SIGN_IN
            "remote branch" in m && "not found" in m -> BRANCH
            "repository not found" in m || "not found" in m && "http" in m -> NOT_FOUND
            "unable to resolve host" in m || "no address associated" in m -> OFFLINE
            "timed out" in m || "timeout" in m -> TIMEOUT
            else -> null
        }
    }

    const val REFUSED = "The Git host refused access to this repository. Check that the Remote URL is your " +
        "notes repository and that the access token is allowed to read and write it (for a GitHub " +
        "fine-grained token: Repository access includes this repository, and Contents is Read and write)."
    const val SIGN_IN = "The Git host didn't accept the sign-in. Check the access token: it may be missing, " +
        "mistyped or expired."
    const val NOT_FOUND = "The repository wasn't found. Check the Remote URL. A private repository also looks " +
        "like this when the access token can't see it."
    const val BRANCH = "That branch doesn't exist in the repository. Check the Branch setting (usually main or master)."
    const val OFFLINE = "Couldn't reach the Git host. Check your internet connection and the Remote URL."
    const val TIMEOUT = "The connection timed out. Check your internet connection and try again."
}

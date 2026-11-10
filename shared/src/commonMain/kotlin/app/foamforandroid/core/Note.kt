package app.foamforandroid.core

data class Note(
    /** Path relative to the repository root, using '/' separators. */
    val path: String,
    val title: String,
    val modified: Long,
) {
    /** Foam identifier: file name without extension. */
    val id: String get() = path.substringAfterLast('/').removeSuffix(".md")
}

/** Repository name from a clone URL: `https://github.com/me/dev-notes.git` → `dev-notes`. */
internal fun repoName(url: String): String? =
    url.trim().trimEnd('/').substringAfterLast('/').substringAfterLast(':').removeSuffix(".git").ifBlank { null }

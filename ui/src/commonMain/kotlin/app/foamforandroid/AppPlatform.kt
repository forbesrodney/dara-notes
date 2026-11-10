package app.foamforandroid

import app.foamforandroid.core.LintConfig
import app.foamforandroid.core.LintResult
import app.foamforandroid.core.Settings
import okio.FileSystem
import okio.Path

/** What the shared UI needs from the platform it runs on. */
interface AppPlatform {
    val settings: Settings
    val fs: FileSystem

    /** Where the notes repository is cloned. */
    val repoDir: Path

    /** Schedules (or cancels) background sync to match the settings. */
    fun scheduleSync()

    /** Syncs shortly, in the background, e.g. after an edit. */
    fun syncSoon()

    /** Checks [text] with markdownlint. */
    suspend fun lint(text: String, config: LintConfig): LintResult

    /** Applies markdownlint's automatic fixes to [text]. */
    suspend fun fixAll(text: String, config: LintConfig): String
}

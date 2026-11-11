package app.daranotes

import app.daranotes.core.LintConfig
import app.daranotes.core.LintResult
import app.daranotes.core.Settings
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

    /**
     * Cancels a sync that [syncSoon] has waiting, and says whether there was one, so it can be
     * run straight away instead (before the app is suspended or quits). Android's waiting
     * sync is run by the system even after the app has gone, so it keeps it.
     */
    fun takePendingSync(): Boolean = false

    /** Checks [text] with markdownlint. */
    suspend fun lint(text: String, config: LintConfig): LintResult

    /** Applies markdownlint's automatic fixes to [text]. */
    suspend fun fixAll(text: String, config: LintConfig): String

    /**
     * The desktop app (mouse and keyboard, a wide window): actions go in the toolbar rather
     * than in floating buttons, which suit thumbs on a phone.
     */
    val desktop: Boolean get() = false
}

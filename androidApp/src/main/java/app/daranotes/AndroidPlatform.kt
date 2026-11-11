package app.daranotes

import android.content.Context
import app.daranotes.core.LintConfig
import app.daranotes.core.LintResult
import app.daranotes.core.Settings
import okio.FileSystem
import okio.Path

/** The Android pieces the shared UI uses: settings, storage, WorkManager sync and markdownlint. */
class AndroidPlatform(context: Context) : AppPlatform {
    private val app = context.applicationContext
    override val settings: Settings = AndroidSettings(app)
    override val fs: FileSystem = FileSystem.SYSTEM
    override val repoDir: Path = repoDir(app)

    override fun scheduleSync() = SyncScheduler.schedulePeriodic(app, settings)

    override fun syncSoon() = SyncScheduler.syncSoon(app)

    override suspend fun lint(text: String, config: LintConfig): LintResult = Linter.lint(app, text, config)

    override suspend fun fixAll(text: String, config: LintConfig): String = Linter.fixAll(app, text, config)
}

package app.daranotes

import app.daranotes.core.GitSync
import app.daranotes.core.LintConfig
import app.daranotes.core.LintResult
import app.daranotes.core.Settings
import app.daranotes.ui.IosLinter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dateWithTimeIntervalSinceNow

/** The iOS pieces the shared UI uses. */
class IosPlatform : AppPlatform {
    override val settings: Settings = IosSettings()
    override val fs: FileSystem = FileSystem.SYSTEM
    override val repoDir: Path = (appSupportDir() + "/repo").toPath()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var soon: Job? = null

    /** Asks iOS for a background refresh after the "Sync every" interval (iOS picks the time). */
    override fun scheduleSync() = BackgroundSync.schedule(settings)

    override fun takePendingSync(): Boolean {
        val pending = soon?.isActive == true
        soon?.cancel()
        soon = null
        return pending
    }

    /** Debounced: each call pushes the sync back by 30 seconds. */
    override fun syncSoon() {
        soon?.cancel()
        soon = scope.launch {
            delay(30_000)
            GitSync.sync(repoDir, settings)
        }
    }

    override suspend fun lint(text: String, config: LintConfig): LintResult = IosLinter.lint(text, config)

    override suspend fun fixAll(text: String, config: LintConfig): String = IosLinter.fixAll(text, config)
}

/**
 * Background sync with BGTaskScheduler. iOS runs app refresh tasks when it sees fit (usually
 * while the phone is idle and online), no sooner than the requested time.
 */
object BackgroundSync {
    /** Also listed under BGTaskSchedulerPermittedIdentifiers in iosApp/iosApp/Info.plist. */
    const val TASK_ID = "app.daranotes.sync"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Registers the task handler. iOS requires this before the app finishes launching. */
    fun register(platform: () -> IosPlatform) {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(TASK_ID, usingQueue = null) { task ->
            val refresh = task as? BGAppRefreshTask ?: return@registerForTaskWithIdentifier
            val p = platform()
            schedule(p.settings) // the next one
            val job = scope.launch {
                val ok = GitSync.sync(p.repoDir, p.settings).isSuccess
                refresh.setTaskCompletedWithSuccess(ok)
            }
            refresh.expirationHandler = { job.cancel() }
        }
    }

    fun schedule(settings: Settings) {
        val minutes = settings.autoSyncMinutes
        if (minutes <= 0 || !settings.isConfigured) {
            BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_ID)
            return
        }
        val request = BGAppRefreshTaskRequest(identifier = TASK_ID)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(minutes * 60.0)
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    }
}

/** The app's Application Support folder (created if needed); not shown in the Files app. */
private fun appSupportDir(): String {
    val fm = NSFileManager.defaultManager
    val url = fm.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
    fm.createDirectoryAtURL(url, withIntermediateDirectories = true, attributes = null, error = null)
    return url.path!!
}

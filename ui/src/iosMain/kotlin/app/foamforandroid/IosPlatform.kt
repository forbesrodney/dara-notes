package app.foamforandroid

import app.foamforandroid.core.LintConfig
import app.foamforandroid.core.LintResult
import app.foamforandroid.core.Settings
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** The iOS pieces the shared UI uses. */
class IosPlatform : AppPlatform {
    override val settings: Settings = IosSettings()
    override val fs: FileSystem = FileSystem.SYSTEM
    override val repoDir: Path = (appSupportDir() + "/repo").toPath()

    override fun scheduleSync() {}

    override fun syncSoon() {}

    override suspend fun lint(text: String, config: LintConfig): LintResult =
        LintResult(emptyList(), config.file, "markdownlint isn't available on iOS yet")

    override suspend fun fixAll(text: String, config: LintConfig): String = text
}

/** The app's Application Support folder (created if needed); not shown in the Files app. */
private fun appSupportDir(): String {
    val fm = NSFileManager.defaultManager
    val url = fm.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
    fm.createDirectoryAtURL(url, withIntermediateDirectories = true, attributes = null, error = null)
    return url.path!!
}

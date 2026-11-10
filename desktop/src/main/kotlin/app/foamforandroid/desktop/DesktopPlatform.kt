package app.foamforandroid.desktop

import app.foamforandroid.AppPlatform
import app.foamforandroid.core.GitSync
import app.foamforandroid.core.LintConfig
import app.foamforandroid.core.LintResult
import app.foamforandroid.core.Settings
import app.foamforandroid.ui.DesktopLinter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

/** The desktop pieces the shared UI uses. Background sync runs on a timer while the app is open. */
class DesktopPlatform : AppPlatform {
    override val settings: Settings = DesktopSettings()
    override val fs: FileSystem = FileSystem.SYSTEM
    override val repoDir: Path = File(appDataDir(), "repo").toOkioPath()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodic: Job? = null
    private var soon: Job? = null

    override fun scheduleSync() {
        periodic?.cancel()
        val minutes = settings.autoSyncMinutes
        if (minutes <= 0 || !settings.isConfigured) return
        periodic = scope.launch {
            while (isActive) {
                delay(minutes * 60_000L)
                GitSync.sync(repoDir, settings)
            }
        }
    }

    /** Debounced: each call pushes the sync back by 30 seconds. */
    override fun syncSoon() {
        soon?.cancel()
        soon = scope.launch {
            delay(30_000)
            GitSync.sync(repoDir, settings)
        }
    }

    override suspend fun lint(text: String, config: LintConfig): LintResult = DesktopLinter.lint(text, config)

    override suspend fun fixAll(text: String, config: LintConfig): String = DesktopLinter.fixAll(text, config)
}

/**
 * Where the app keeps its data, following each platform's convention:
 * `~/Library/Application Support/Foam for macOS`, `%APPDATA%\Foam for Windows`,
 * `~/.local/share/foam-for-linux`.
 */
fun appDataDir(): File = dataDir(appName())

private fun dataDir(name: String): File {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    return when {
        "mac" in os -> File(home, "Library/Application Support/$name")
        "win" in os -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", name)
        else -> File(
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share",
            name.lowercase().replace(' ', '-'),
        )
    }
}

/** Moves data from the folder earlier versions used ("Foam for Android") to [appDataDir]. */
fun migrateAppDataDir() {
    val old = dataDir("Foam for Android")
    val new = appDataDir()
    if (old.isDirectory && !new.exists()) {
        new.parentFile?.mkdirs()
        if (!old.renameTo(new)) System.err.println("Couldn't move $old to $new")
    }
}

/** The app's name on this platform: "Foam for macOS", "Foam for Windows" or "Foam for Linux". */
fun appName(): String = "Foam for ${clientName()}"

/** The platform's name for commit messages: "macOS", "Windows" or "Linux". */
fun clientName(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        "mac" in os -> "macOS"
        "win" in os -> "Windows"
        else -> "Linux"
    }
}

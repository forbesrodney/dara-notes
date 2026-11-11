package app.foamforandroid.desktop

import app.foamforandroid.core.Settings
import java.util.prefs.Preferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Settings in Java preferences (on macOS, ~/Library/Preferences/app.foamforandroid.plist), with
 * the access token in the system keychain.
 */
class DesktopSettings : Settings {
    private val prefs: Preferences = Preferences.userRoot().node("app/foamforandroid")

    override var remoteUrl by StringPref("remote_url", "")
    override var branch by StringPref("branch", "main")
    override var username by StringPref("username", "")
    override var token: String
        get() = Token.get()
        set(value) = Token.set(value)
    override var authorName by StringPref("author_name", "")
    override var authorEmail by StringPref("author_email", "")

    override var linkFromIndex by BoolPref("link_from_index", false)
    override var indexFile by StringPref("index_file", "Index.md")
    override var indexDailyNotes by BoolPref("index_daily_notes", false)

    override var bullet by StringPref("bullet", "-")

    override var strikeCompleted by BoolPref("strike_completed", false)
    override var dateOnComplete by BoolPref("date_on_complete", false)
    override var dateOrder by StringPref("date_order", "locale")
    override var dateSeparator by StringPref("date_separator", "locale")

    override var lintEnabled by BoolPref("lint_enabled", false)

    override var notesView by StringPref("notes_view", "flat")

    override var graphTags by BoolPref("graph_tags", true)
    override var graphPlaceholders by BoolPref("graph_placeholders", true)

    override var newNoteDir by StringPref("new_note_dir", "")

    override var dailyDirOverride by StringPref("daily_dir", "")

    override var autoSyncMinutes by IntPref("auto_sync_minutes", 15)
    override var syncAfterEdit by BoolPref("sync_after_edit", true)

    override var cloned by BoolPref("cloned", false)

    private inner class StringPref(val key: String, val def: String) : ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>): String = prefs.get(key, def)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) { prefs.put(key, value); prefs.flush() }
    }

    private inner class BoolPref(val key: String, val def: Boolean) : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Boolean = prefs.getBoolean(key, def)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) { prefs.putBoolean(key, value); prefs.flush() }
    }

    private inner class IntPref(val key: String, val def: Int) : ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Int = prefs.getInt(key, def)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) { prefs.putInt(key, value); prefs.flush() }
    }

    /**
     * The access token in the system keychain, under the app's name ("Foam for macOS"), read
     * once and then kept in memory.
     */
    private object Token {
        private val SERVICE = appName()
        /** The name earlier versions used; a token found there is moved to [SERVICE]. */
        private const val OLD_SERVICE = "Foam for Android"
        private const val ACCOUNT = "git-access-token"
        @Volatile private var cached: String? = null

        fun get(): String = cached ?: (load() ?: "").also { cached = it }

        private val secrets = Secrets.system()

        private fun load(): String? = runCatching {
            secrets.get(SERVICE, ACCOUNT)
                ?: secrets.get(OLD_SERVICE, ACCOUNT)?.also { token ->
                    secrets.set(SERVICE, ACCOUNT, token)
                    runCatching { secrets.delete(OLD_SERVICE, ACCOUNT) }
                }
        }.getOrNull()

        fun set(value: String) {
            if (value == cached) return
            runCatching {
                if (value.isEmpty()) secrets.delete(SERVICE, ACCOUNT) else secrets.set(SERVICE, ACCOUNT, value)
            }.onFailure { System.err.println("Couldn't save the access token in the keychain: ${it.message}") }
            cached = value
        }
    }
}

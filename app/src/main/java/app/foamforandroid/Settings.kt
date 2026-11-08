package app.foamforandroid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** App settings. The access token is kept in encrypted preferences. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** HTTPS clone URL, e.g. https://github.com/me/notes.git. Blank = local-only repository. */
    var remoteUrl by StringPref(prefs, "remote_url", "")
    var branch by StringPref(prefs, "branch", "main")
    var username by StringPref(prefs, "username", "")
    var token by StringPref(secure, "token", "")
    var authorName by StringPref(prefs, "author_name", "")
    var authorEmail by StringPref(prefs, "author_email", "")

    /** Index file maintained when notes are created. */
    var indexFile by StringPref(prefs, "index_file", "Index.md")
    var indexDailyNotes by BoolPref(prefs, "index_daily_notes", false)

    /** List bullet the app writes: "-" or "*". */
    var bullet by StringPref(prefs, "bullet", "-")

    /** Checklists: what checking an item does to its text. */
    var strikeCompleted by BoolPref(prefs, "strike_completed", false)
    var dateOnComplete by BoolPref(prefs, "date_on_complete", false)
    /** "locale", "ymd", "mdy" or "dmy". */
    var dateOrder by StringPref(prefs, "date_order", "locale")
    /** "locale", "-", "/" or ".". */
    var dateSeparator by StringPref(prefs, "date_separator", "locale")

    /** Check notes with markdownlint while editing. */
    var lintEnabled by BoolPref(prefs, "lint_enabled", false)

    /** Graph filters. */
    var graphTags by BoolPref(prefs, "graph_tags", true)
    var graphPlaceholders by BoolPref(prefs, "graph_placeholders", true)

    /** Folder (relative to repo root) for new notes. Blank = root. */
    var newNoteDir by StringPref(prefs, "new_note_dir", "")

    /** Daily note folder override. Blank = use Foam's .vscode/settings.json, else "journal". */
    var dailyDirOverride by StringPref(prefs, "daily_dir", "")

    /** Periodic background sync interval in minutes; 0 disables. Android minimum is 15. */
    var autoSyncMinutes by IntPref(prefs, "auto_sync_minutes", 15)
    var syncAfterEdit by BoolPref(prefs, "sync_after_edit", true)

    var cloned by BoolPref(prefs, "cloned", false)

    val isConfigured: Boolean get() = cloned

    private class StringPref(val p: SharedPreferences, val key: String, val def: String) :
        ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = p.getString(key, def) ?: def
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) =
            p.edit().putString(key, value).apply()
    }

    private class BoolPref(val p: SharedPreferences, val key: String, val def: Boolean) :
        ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = p.getBoolean(key, def)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) =
            p.edit().putBoolean(key, value).apply()
    }

    private class IntPref(val p: SharedPreferences, val key: String, val def: Int) :
        ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = p.getInt(key, def)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) =
            p.edit().putInt(key, value).apply()
    }
}

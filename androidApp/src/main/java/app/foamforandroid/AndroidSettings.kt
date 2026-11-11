package app.foamforandroid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.foamforandroid.core.Settings
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** [Settings] stored in SharedPreferences; the access token is kept in encrypted preferences. */
class AndroidSettings(context: Context) : Settings {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override var remoteUrl by StringPref(prefs, "remote_url", "")
    override var branch by StringPref(prefs, "branch", "main")
    override var username by StringPref(prefs, "username", "")
    override var token by StringPref(secure, "token", "")
    override var authorName by StringPref(prefs, "author_name", "")
    override var authorEmail by StringPref(prefs, "author_email", "")

    override var linkFromIndex by BoolPref(prefs, "link_from_index", false)
    override var indexFile by StringPref(prefs, "index_file", "Index.md")
    override var indexDailyNotes by BoolPref(prefs, "index_daily_notes", false)

    override var bullet by StringPref(prefs, "bullet", "-")

    override var strikeCompleted by BoolPref(prefs, "strike_completed", false)
    override var dateOnComplete by BoolPref(prefs, "date_on_complete", false)
    override var dateOrder by StringPref(prefs, "date_order", "locale")
    override var dateSeparator by StringPref(prefs, "date_separator", "locale")

    override var lintEnabled by BoolPref(prefs, "lint_enabled", false)

    override var notesView by StringPref(prefs, "notes_view", "flat")

    override var graphTags by BoolPref(prefs, "graph_tags", true)
    override var graphPlaceholders by BoolPref(prefs, "graph_placeholders", true)

    override var newNoteDir by StringPref(prefs, "new_note_dir", "")

    override var dailyDirOverride by StringPref(prefs, "daily_dir", "")

    // WorkManager runs periodic work at most every 15 minutes.
    override var autoSyncMinutes by IntPref(prefs, "auto_sync_minutes", 15)
    override var syncAfterEdit by BoolPref(prefs, "sync_after_edit", true)

    override var cloned by BoolPref(prefs, "cloned", false)

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

/** Where the notes repository is cloned, in the app's private storage. */
fun repoDir(context: Context): Path = File(context.filesDir, "repo").toOkioPath()

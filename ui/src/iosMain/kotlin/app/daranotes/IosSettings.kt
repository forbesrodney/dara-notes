package app.daranotes

import app.daranotes.core.Settings
import platform.Foundation.NSUserDefaults
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Settings in NSUserDefaults, with the access token in the Keychain. */
class IosSettings : Settings {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var remoteUrl by StringPref("remote_url", "")
    override var branch by StringPref("branch", "main")
    override var username by StringPref("username", "")
    private val keychain = Keychain(service = "Dara Notes", account = "git-access-token")
    private var cachedToken: String? = null
    override var token: String
        get() = cachedToken ?: (keychain.get() ?: "").also { cachedToken = it }
        set(value) {
            if (value != cachedToken) keychain.set(value)
            cachedToken = value
        }
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
    override var spellEnabled by BoolPref("spell_enabled", false)

    override var notesView by StringPref("notes_view", "flat")

    override var graphTags by BoolPref("graph_tags", true)
    override var graphPlaceholders by BoolPref("graph_placeholders", true)

    override var newNoteDir by StringPref("new_note_dir", "")

    override var dailyDirOverride by StringPref("daily_dir", "")

    override var autoSyncMinutes by IntPref("auto_sync_minutes", 15)
    override var syncAfterEdit by BoolPref("sync_after_edit", true)

    override var cloned by BoolPref("cloned", false)

    private inner class StringPref(val key: String, val def: String) : ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>): String = defaults.stringForKey(key) ?: def
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) = defaults.setObject(value, key)
    }

    private inner class BoolPref(val key: String, val def: Boolean) : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Boolean =
            if (defaults.objectForKey(key) == null) def else defaults.boolForKey(key)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) = defaults.setBool(value, key)
    }

    private inner class IntPref(val key: String, val def: Int) : ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Int =
            if (defaults.objectForKey(key) == null) def else defaults.integerForKey(key).toInt()
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) = defaults.setInteger(value.toLong(), key)
    }
}

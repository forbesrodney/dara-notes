package app.foamforandroid.core

/**
 * App settings. Each platform stores them its own way (Android: SharedPreferences, with the
 * access token in encrypted preferences).
 */
interface Settings {
    /** HTTPS clone URL, e.g. https://github.com/me/notes.git. Blank = local-only repository. */
    var remoteUrl: String
    var branch: String
    var username: String
    /** Access token. Platforms must store this encrypted. */
    var token: String
    var authorName: String
    var authorEmail: String

    /** Index file maintained when notes are created. */
    var indexFile: String
    var indexDailyNotes: Boolean

    /** List bullet the app writes: "-" or "*". */
    var bullet: String

    /** Checklists: what checking an item does to its text. */
    var strikeCompleted: Boolean
    var dateOnComplete: Boolean
    /** "locale", "ymd", "mdy", "dmy", … (see [DateOrder]). */
    var dateOrder: String
    /** "locale", "-", "/" or ".". */
    var dateSeparator: String

    /** Check notes with markdownlint while editing. */
    var lintEnabled: Boolean

    /** Note list layout: "flat" (every note, newest first) or "tree" (by folder). */
    var notesView: String

    /** Graph filters. */
    var graphTags: Boolean
    var graphPlaceholders: Boolean

    /** Folder (relative to repo root) for new notes. Blank = root. */
    var newNoteDir: String

    /** Daily note folder override. Blank = use Foam's .vscode/settings.json, else "journal". */
    var dailyDirOverride: String

    /** Periodic background sync interval in minutes; 0 disables. */
    var autoSyncMinutes: Int
    var syncAfterEdit: Boolean

    var cloned: Boolean

    val isConfigured: Boolean get() = cloned
}

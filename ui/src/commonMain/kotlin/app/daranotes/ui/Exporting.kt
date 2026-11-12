package app.daranotes.ui

import androidx.compose.runtime.Composable
import okio.Path

/** A note to export: its path, its text as it is now, and what the preview needs to draw it. */
class ExportNote(
    val path: String,
    val markdown: String,
    val repoRoot: Path,
    val callbacks: PreviewCallbacks,
)

/**
 * One way to export or share a note, as the platform offers it (a share sheet on a phone, a
 * Save dialog on the desktop). [run] returns a message to show, or null.
 */
class ExportAction(val label: String, val run: suspend (ExportNote) -> String?)

/** The platform's ways to export a note: as a Markdown file, as a PDF and as text. */
@Composable
expect fun rememberExportActions(): List<ExportAction>

/** The `renderExport(...)` call that draws [note] in the preview page's printable layout. */
internal fun exportScript(note: ExportNote, quote: (String) -> String): String =
    "renderExport(${quote(note.markdown)}, ${quote(note.path.substringBeforeLast('/', ""))})"

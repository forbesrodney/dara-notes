package app.foamforandroid.ui

import app.foamforandroid.core.ioDispatcher
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.foamforandroid.MainViewModel
import app.foamforandroid.Screen
import app.foamforandroid.core.LintResult
import app.foamforandroid.core.TaskStyle
import app.foamforandroid.core.Tasks
import app.foamforandroid.core.Toc
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun EditorScreen(vm: MainViewModel, screen: Screen.Editor, snackbar: SnackbarHostState) {
    val path = screen.path
    var value by remember(path) { mutableStateOf(TextFieldValue(vm.repo.read(path))) }
    var saved by remember(path) { mutableStateOf(value.text) }
    // The note as loaded (or reloaded after a sync): edited if the text differs.
    var baseline by remember(path) { mutableStateOf(value.text) }
    // False once the note is renamed, moved or deleted, so leaving it writes nothing.
    var flushOnLeave by remember(path) { mutableStateOf(true) }
    var editing by rememberSaveable(path) { mutableStateOf(screen.startEditing) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val editorScroll = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var editorFocused by remember { mutableStateOf(false) }
    var viewportPx by remember { mutableStateOf(0) }
    val editorPadPx = with(LocalDensity.current) { 12.dp.toPx() }
    var lint by remember(path) { mutableStateOf<LintResult?>(null) }
    var showIssues by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lintOn = vm.settings.lintEnabled
    var moving by remember { mutableStateOf(false) }
    val notes by vm.notes.collectAsState()
    val sync by vm.syncState.collectAsState()
    val focus = remember { FocusRequester() }
    var swallowTyped by remember { mutableStateOf(false) }

    // New notes open straight into the editor with the cursor at the end.
    LaunchedEffect(Unit) {
        if (screen.startEditing) {
            value = value.copy(selection = TextRange(value.text.length))
            runCatching { focus.requestFocus() }
        }
    }

    val bullet = vm.settings.bullet
    // Brings the table of contents up to date when an edited note goes to Preview or is left,
    // so it never changes under the cursor while typing.
    fun refreshToc() {
        if (value.text == baseline) return
        Toc.update(value.text, bullet)?.let { value = value.applying(it) }
    }

    // Auto-save shortly after typing stops.
    LaunchedEffect(path) {
        snapshotFlow { value.text }.debounce(800).collect { text ->
            if (text != saved) { vm.save(path, text); saved = text }
        }
    }
    // Save anything pending when leaving the note.
    val latest by rememberUpdatedState(value.text)
    val latestBaseline by rememberUpdatedState(baseline)
    val latestFlush by rememberUpdatedState(flushOnLeave)
    DisposableEffect(path) {
        onDispose {
            if (!latestFlush) return@onDispose
            val text = if (latest != latestBaseline) Toc.update(latest, bullet)?.text ?: latest else latest
            if (text != saved) vm.save(path, text)
        }
    }
    // Keep the cursor on screen. A tap focuses the field before it moves the cursor, and on
    // focus the field scrolls to the old cursor (often the top of the note); so whenever the
    // cursor moves, or the keyboard changes the visible height, scroll to where it really is.
    LaunchedEffect(value.selection, viewportPx, textLayout) {
        val layout = textLayout
        if (!editorFocused || layout == null || viewportPx <= 0) return@LaunchedEffect
        val end = value.selection.end.coerceIn(0, layout.layoutInput.text.length)
        val cursor = layout.getCursorRect(end)
        val top = cursor.top + editorPadPx
        val bottom = cursor.bottom + editorPadPx
        val margin = cursor.height * 2 // a little room around the cursor line
        val current = editorScroll.value
        when {
            top < current -> editorScroll.scrollTo((top - margin).toInt().coerceAtLeast(0))
            bottom > current + viewportPx -> editorScroll.scrollTo((bottom - viewportPx + margin).toInt())
        }
    }
    // markdownlint, shortly after typing stops (only while editing, when switched on).
    LaunchedEffect(path, editing, lintOn) {
        if (!lintOn || !editing) { lint = null; return@LaunchedEffect }
        snapshotFlow { value.text }.debounce(600).collectLatest { text ->
            val config = withContext(ioDispatcher) { vm.repo.lintConfig() }
            lint = runCatching { vm.platform.lint(text, config) }.getOrNull()
        }
    }
    // After a sync, reload the note if it changed on disk and there are no unsaved edits.
    LaunchedEffect(path) {
        vm.syncState.map { it.generation }.distinctUntilChanged().collect {
            val disk = withContext(ioDispatcher) { vm.repo.read(path) }
            if (value.text == saved && disk != saved) {
                val c = value.selection.start.coerceAtMost(disk.length)
                value = TextFieldValue(disk, TextRange(c))
                saved = disk
                baseline = disk
            }
        }
    }

    val callbacks = remember { PreviewCallbacks() }
    callbacks.resolve = { target -> vm.repo.resolve(target) }
    callbacks.readNote = { p -> vm.repo.read(p) }
    callbacks.openWikilink = vm::openWikilink
    callbacks.openPath = { p -> vm.openPath(p) }
    callbacks.toggleTask = { line ->
        val text = vm.repo.toggleTask(value.text, line)
        if (text != value.text) {
            value = value.copy(text = text)
            vm.save(path, text)
            saved = text
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (path.contains('/')) {
                            Text(
                                path.substringBeforeLast('/'),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (editing) refreshToc()
                        editing = !editing
                    }) { Text(if (editing) "Preview" else "Edit") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Sync now") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = !sync.running,
                            onClick = {
                                menu = false
                                if (value.text != saved) { vm.save(path, value.text); saved = value.text }
                                vm.syncNow()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Connections") },
                            leadingIcon = { Icon(LinkIcon, null) },
                            onClick = {
                                menu = false
                                if (value.text != saved) { vm.save(path, value.text); saved = value.text }
                                vm.navigate(Screen.Connections(path))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Show in graph") },
                            leadingIcon = { Icon(GraphIcon, null) },
                            onClick = {
                                menu = false
                                if (value.text != saved) { vm.save(path, value.text); saved = value.text }
                                vm.navigate(Screen.Graph(focus = path))
                            },
                        )
                        val hasToc = Toc.has(value.text)
                        DropdownMenuItem(
                            text = { Text(if (hasToc) "Update table of contents" else "Add table of contents") },
                            leadingIcon = { Icon(TocIcon, null) },
                            onClick = {
                                menu = false
                                val edit = Toc.insertOrUpdate(value.text, bullet)
                                if (edit == null) {
                                    val why = if (hasToc) "The table of contents is up to date" else "No sections (## headings) to list"
                                    scope.launch { snackbar.showSnackbar(why) }
                                } else {
                                    value = value.applying(edit)
                                    vm.save(path, value.text)
                                    saved = value.text
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Move to folder") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                            onClick = { menu = false; moving = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete note") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (editing) {
                val query = wikilinkQuery(value)
                if (query != null) {
                    val q = query.second.lowercase()
                    val matches = notes.asSequence()
                        .filter { q.isEmpty() || q in it.id.lowercase() || q in it.title.lowercase() }
                        .take(12).toList()
                    if (matches.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(matches, key = { it.path }) { note ->
                                SuggestionChip(
                                    onClick = { value = completeWikilink(value, query.first, vm.repo.linkId(note.path)) },
                                    label = { Text(note.id) },
                                )
                            }
                        }
                    }
                }
                FormatToolbar(bullet = vm.settings.bullet, taskStyle = { TaskStyle.from(vm.settings) }, onAction = { value = it(value) })
                HorizontalDivider()
                lint?.let { result ->
                    if (result.issues.isNotEmpty() || result.configError != null) {
                        LintBar(result) { showIssues = true }
                    }
                }
                // The editor scrolls in its own container rather than inside the text field: the
                // field's built-in scrolling resets to the top when the keyboard shrinks it,
                // while a scroll container keeps the cursor in view.
                Surface(Modifier.fillMaxSize()) {
                  BoxWithConstraints(Modifier.fillMaxSize()) {
                    val viewport = maxHeight
                    viewportPx = constraints.maxHeight
                    BasicTextField(
                        value = value,
                        onValueChange = { value = continueList(value, it) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        onTextLayout = { textLayout = it },
                        visualTransformation = LintHighlight(
                            lint?.issues.orEmpty(),
                            MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(editorScroll)
                            // At least a screenful, so tapping below the text still places the cursor.
                            .heightIn(min = viewport)
                            .padding(12.dp)
                            .onFocusChanged { editorFocused = it.isFocused }
                            .onPreviewKeyEvent { e ->
                                // The character a shortcut's key types arrives as a separate event
                                // (a Mac sends Cmd+Enter's newline separately); swallow it, or the text field
                                // inserts it into the note as it was before the shortcut.
                                if (e.type != KeyEventType.KeyDown && e.type != KeyEventType.KeyUp) {
                                    return@onPreviewKeyEvent swallowTyped.also { swallowTyped = false }
                                }
                                val action = shortcut(e, vm.settings.bullet) { TaskStyle.from(vm.settings) }
                                    ?: return@onPreviewKeyEvent false
                                if (e.type == KeyEventType.KeyDown) {
                                    value = action(value)
                                    swallowTyped = true
                                } else swallowTyped = false
                                true
                            }
                            .focusRequester(focus),
                    )
                  }
                }
            } else {
                MarkdownPreview(
                    markdown = value.text,
                    notePath = path,
                    repoRoot = vm.repo.root,
                    callbacks = callbacks,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    val relocate = { newPath: String ->
        renaming = false
        moving = false
        saved = value.text // written by relocateNote, so leaving this editor must not flush it
        flushOnLeave = false
        vm.relocateNote(path, newPath, pendingText = value.text, keepEditing = editing)
    }
    val result = lint
    if (showIssues && result != null) {
        LintIssuesDialog(
            result,
            onDismiss = { showIssues = false },
            onJump = { issue ->
                showIssues = false
                value = value.copy(selection = issueRange(value.text, issue))
                runCatching { focus.requestFocus() }
            },
            onFixAll = {
                showIssues = false
                scope.launch {
                    val config = withContext(ioDispatcher) { vm.repo.lintConfig() }
                    val fixed = runCatching { vm.platform.fixAll(value.text, config) }.getOrNull()
                    if (fixed != null && fixed != value.text) {
                        value = TextFieldValue(fixed, TextRange(value.selection.start.coerceAtMost(fixed.length)))
                    }
                }
            },
        )
    }
    if (renaming) RenameNoteDialog(vm, path, onDismiss = { renaming = false }, onConfirm = relocate)
    if (moving) MoveNoteDialog(vm, path, onDismiss = { moving = false }, onConfirm = relocate)

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?") },
            text = { Text("$path will be deleted and removed from the index.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    saved = value.text // nothing to flush
                    flushOnLeave = false
                    vm.deleteNote(path)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FormatToolbar(bullet: String, taskStyle: () -> TaskStyle, onAction: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val editActions: List<Pair<String, (TextFieldValue) -> TextFieldValue>> = listOf(
        "All" to { v: TextFieldValue -> v.copy(selection = TextRange(0, v.text.length)) },
        "Cut" to { v: TextFieldValue ->
            val r = v.selectionOrLine()
            clipboard.setText(AnnotatedString(v.text.substring(r.min, r.max)))
            TextFieldValue(v.text.removeRange(r.min, r.max), TextRange(r.min))
        },
        "Copy" to { v: TextFieldValue ->
            val r = v.selectionOrLine()
            clipboard.setText(AnnotatedString(v.text.substring(r.min, r.max)))
            v
        },
        "Paste" to { v: TextFieldValue ->
            val clip = clipboard.getText()?.text
            if (clip.isNullOrEmpty()) v else {
                val s = v.selection.min
                TextFieldValue(v.text.replaceRange(s, v.selection.max, clip), TextRange(s + clip.length))
            }
        },
    )
    val actions: List<Pair<String, (TextFieldValue) -> TextFieldValue>> = listOf(
        "H" to { v: TextFieldValue -> v.toggleLinePrefix("# ") },
        "B" to { v: TextFieldValue -> v.toggleWrap("**", "bold") },
        "I" to { v: TextFieldValue -> v.toggleWrap("*", "italic") },
        "☐" to { v: TextFieldValue -> v.toggleCheckbox(bullet, taskStyle()) },
        "•" to { v: TextFieldValue -> v.toggleListPrefix(bullet, task = false) },
        "[[ ]]" to { v: TextFieldValue -> v.wrap("[[", "]]") },
        "x²" to { v: TextFieldValue -> v.wrap("^", "^", "sup") },
        "x₂" to { v: TextFieldValue -> v.wrap("~", "~", "sub") },
        "∑" to { v: TextFieldValue -> v.wrap("$", "$", "x") },
        "∑∑" to { v: TextFieldValue -> v.insertBlock("$$\n", "\n$$", "E = mc^2") },
        "fn" to { v: TextFieldValue -> v.addFootnote() },
        "◇" to { v: TextFieldValue -> v.insertBlock("```mermaid\n", "\n```", "graph TD\n  A --> B") },
        "<>" to { v: TextFieldValue -> v.wrap("`", "`", "code") },
        "🔗" to { v: TextFieldValue -> v.wrap("[", "](url)", "text") },
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
    ) {
        editActions.forEach { (label, transform) ->
            TextButton(onClick = { onAction(transform) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(label)
            }
        }
        VerticalDivider(Modifier.height(24.dp).align(Alignment.CenterVertically).padding(horizontal = 4.dp))
        actions.forEach { (label, transform) ->
            TextButton(onClick = { onAction(transform) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(label, fontFamily = if (label.length <= 2) FontFamily.Serif else FontFamily.Monospace)
            }
        }
    }
}

/**
 * Hardware-keyboard shortcuts, with Ctrl or Cmd: B bold, I italic, Shift+X strike-through,
 * and Enter to check the line's task (as in Notion). Returns the edit for [e], or null if
 * it isn't a shortcut. Option/Alt is left alone: many keyboard layouts type letters with it.
 */
private fun shortcut(e: KeyEvent, bullet: String, taskStyle: () -> TaskStyle): ((TextFieldValue) -> TextFieldValue)? {
    if (!(e.isCtrlPressed || e.isMetaPressed) || e.isAltPressed) return null
    return if (e.isShiftPressed) when (e.key) {
        Key.X -> { v -> v.toggleWrap("~~", "strike") }
        else -> null
    } else when (e.key) {
        Key.B -> { v -> v.toggleWrap("**", "bold") }
        Key.I -> { v -> v.toggleWrap("*", "italic") }
        Key.Enter, Key.NumPadEnter -> { v -> v.toggleCheckbox(bullet, taskStyle()) }
        else -> null
    }
}

/** Material "toc" icon (not in material-icons-core). */
val TocIcon: ImageVector by lazy {
    ImageVector.Builder("Toc", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M3,9h14V7H3v2zM3,13h14v-2H3v2zM3,17h14v-2H3v2zM19,17h2v-2h-2v2zM19,7v2h2V7h-2zM19,13h2v-2h-2v2z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** The note after [edit], with the cursor or selection moved along with the text. */
fun TextFieldValue.applying(edit: Toc.Edit): TextFieldValue {
    fun map(p: Int) = when {
        p >= edit.start + edit.removed -> p + edit.added - edit.removed
        p > edit.start -> edit.start
        else -> p
    }
    return TextFieldValue(edit.text, TextRange(map(selection.start), map(selection.end)))
}

// ---- text editing helpers -------------------------------------------------------------------

/** If the cursor is inside an unfinished `[[...`, returns (start of query, query). */
fun wikilinkQuery(v: TextFieldValue): Pair<Int, String>? {
    if (!v.selection.collapsed) return null
    val before = v.text.substring(0, v.selection.start)
    val open = before.lastIndexOf("[[")
    if (open < 0) return null
    val q = before.substring(open + 2)
    if (q.contains("]") || q.contains('\n') || q.contains('|') || q.contains('#') || q.length > 80) return null
    return (open + 2) to q
}

fun completeWikilink(v: TextFieldValue, start: Int, id: String): TextFieldValue {
    val cursor = v.selection.start
    val after = v.text.substring(cursor)
    val closing = if (after.startsWith("]]")) "" else "]]"
    val text = v.text.substring(0, start) + id + closing + after
    return TextFieldValue(text, TextRange(start + id.length + 2))
}

fun TextFieldValue.wrap(prefix: String, suffix: String, placeholder: String = ""): TextFieldValue {
    val s = selection.min
    val e = selection.max
    val selected = text.substring(s, e)
    val inner = selected.ifEmpty { placeholder }
    val newText = text.substring(0, s) + prefix + inner + suffix + text.substring(e)
    val start = s + prefix.length
    return TextFieldValue(newText, TextRange(start, start + inner.length))
}

/**
 * Bold, italic or strike-through as a toggle: removes [marker] if the selection is already
 * wrapped in it (just inside or just outside the selection), and adds it otherwise. With
 * nothing selected it applies to the word at the cursor, or inserts [placeholder]. A `*`
 * marker treats `**bold**` as not italic, and `***both***` as italic.
 */
fun TextFieldValue.toggleWrap(marker: String, placeholder: String): TextFieldValue {
    var s = selection.min
    var e = selection.max
    if (s == e) {
        while (s > 0 && text[s - 1].isLetterOrDigit()) s--
        while (e < text.length && text[e].isLetterOrDigit()) e++
        if (s == e) return wrap(marker, marker, placeholder)
    }
    val c = marker[0]
    val m = marker.length
    fun present(run: Int) = if (m == 1) run % 2 == 1 else run >= m
    fun runBack(from: Int, limit: Int): Int { var i = from; while (i > limit && text[i - 1] == c) i--; return from - i }
    fun runFwd(from: Int, limit: Int): Int { var i = from; while (i < limit && text[i] == c) i++; return i - from }

    // Markers just outside the selection: **[bold]**
    val outside = minOf(runBack(s, 0), runFwd(e, text.length))
    if (present(outside)) {
        val t = text.substring(0, s - m) + text.substring(s, e) + text.substring(e + m)
        return TextFieldValue(t, TextRange(s - m, e - m))
    }
    // Markers just inside it: [**bold**]
    val inside = minOf(runFwd(s, e), runBack(e, s))
    if (e - s > 2 * m && present(inside)) {
        val t = text.substring(0, s) + text.substring(s + m, e - m) + text.substring(e)
        return TextFieldValue(t, TextRange(s, e - 2 * m))
    }
    return TextFieldValue(this.text, TextRange(s, e)).wrap(marker, marker)
}

fun TextFieldValue.toggleLinePrefix(prefix: String): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', selection.min - 1) + 1
    return if (text.startsWith(prefix, lineStart)) {
        val newText = text.removeRange(lineStart, lineStart + prefix.length)
        val c = (selection.start - prefix.length).coerceAtLeast(lineStart)
        TextFieldValue(newText, TextRange(c))
    } else {
        val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        TextFieldValue(newText, TextRange(selection.start + prefix.length, selection.end + prefix.length))
    }
}

private val taskBox = Regex("""^(\s*(?:[-*+]|\d+[.)])\s+)\[[ xX]]\s?""")
private val plainItem = Regex("""^\s*(?:[-*+]|\d+[.)])\s+""")

/** Replaces the current line, keeping the cursor at the same distance from the line's end. */
private fun TextFieldValue.replaceLine(lineStart: Int, lineEnd: Int, newLine: String): TextFieldValue {
    val fromEnd = (lineEnd - selection.end).coerceAtLeast(0)
    val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    val c = (lineStart + newLine.length - fromEnd).coerceIn(lineStart, lineStart + newLine.length)
    return TextFieldValue(newText, TextRange(c))
}

/**
 * The ☐ button: a plain line becomes an unchecked item, a list item gains a checkbox, and an
 * existing task is checked or unchecked (applying the strike-through and date settings).
 */
fun TextFieldValue.toggleCheckbox(bullet: String, style: TaskStyle): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', selection.min - 1) + 1
    val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    Tasks.toggle(line, style)?.let { return replaceLine(lineStart, lineEnd, it) }
    val item = plainItem.find(line)
    val newLine = if (item != null) item.value + "[ ] " + line.substring(item.value.length)
    else "$bullet [ ] $line"
    return replaceLine(lineStart, lineEnd, newLine)
}

/**
 * Toggles a list item on the current line using [bullet]. An existing item written with any
 * bullet (-, * or +) is recognized, so the button can also remove it; on a task item it
 * removes just the checkbox.
 */
fun TextFieldValue.toggleListPrefix(bullet: String, task: Boolean): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', selection.min - 1) + 1
    val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    if (!task) taskBox.find(line)?.let { m ->
        return replaceLine(lineStart, lineEnd, m.groupValues[1] + line.substring(m.value.length))
    }
    val pattern = if (task) Regex("^[-*+] \\[[ xX]] ") else Regex("^[-*+] (?!\\[[ xX]] )")
    val existing = pattern.find(line)?.value
    if (existing != null) {
        val newText = text.removeRange(lineStart, lineStart + existing.length)
        val shift = existing.length
        return TextFieldValue(newText, TextRange(
            (selection.start - shift).coerceAtLeast(lineStart),
            (selection.end - shift).coerceAtLeast(lineStart),
        ))
    }
    val prefix = if (task) "$bullet [ ] " else "$bullet "
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return TextFieldValue(newText, TextRange(selection.start + prefix.length, selection.end + prefix.length))
}

private val listItem = Regex("""^(\s*)([-*+]|\d+[.)])(\s+)(\[[ xX]]\s+)?""")

/**
 * When Enter is typed at the end of (or inside) a list item, starts the next item of the
 * same kind: same indent and bullet, same number for numbered items, and an unchecked box
 * for task items. Enter on an empty item ends the list instead.
 */
fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    val pos = old.selection.min
    if (!old.selection.collapsed || new.text.length != old.text.length + 1) return new
    if (new.selection != TextRange(pos + 1) || new.text[pos] != '\n') return new
    if (new.text.removeRange(pos, pos + 1) != old.text) return new

    val lineStart = old.text.lastIndexOf('\n', pos - 1) + 1
    val line = old.text.substring(lineStart, pos)
    val m = listItem.find(line) ?: return new
    val (indent, marker, gap) = m.destructured
    val box = m.groups[4]?.value
    val prefixLen = m.value.length

    // Empty item: Enter removes the marker and ends the list.
    val restOfLine = old.text.substring(pos).substringBefore('\n')
    if (line.length == prefixLen && restOfLine.isBlank()) {
        val text = old.text.removeRange(lineStart, pos)
        return TextFieldValue(text, TextRange(lineStart))
    }
    val next = indent + marker + gap + (if (box != null) "[ ] " else "")
    val text = new.text.substring(0, pos + 1) + next + new.text.substring(pos + 1)
    val c = pos + 1 + next.length
    return TextFieldValue(text, TextRange(c))
}

/** The selection, or the current line (with its newline) when nothing is selected. */
fun TextFieldValue.selectionOrLine(): TextRange {
    if (!selection.collapsed) return TextRange(selection.min, selection.max)
    val start = text.lastIndexOf('\n', selection.min - 1) + 1
    val nl = text.indexOf('\n', selection.min)
    val end = if (nl < 0) text.length else nl + 1
    return TextRange(start, end)
}

fun TextFieldValue.insertBlock(open: String, close: String, placeholder: String): TextFieldValue {
    val s = selection.min
    val lead = if (s == 0 || text[s - 1] == '\n') "" else "\n"
    val wrapped = wrap(lead + open, close + "\n", placeholder)
    return wrapped
}

fun TextFieldValue.addFootnote(): TextFieldValue {
    val n = (Regex("\\[\\^(\\d+)]").findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0) + 1
    val ref = "[^$n]"
    val c = selection.max
    var newText = text.substring(0, c) + ref + text.substring(c)
    newText = newText.trimEnd('\n') + "\n\n[^$n]: "
    return TextFieldValue(newText, TextRange(newText.length))
}

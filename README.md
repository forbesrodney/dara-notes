# Foam for Android

An Android app for Markdown notes kept in a Git repository, compatible with
[Foam](https://foambubble.github.io/foam/) workspaces.

## Features

- **Git auto-sync** (JGit, HTTPS + personal access token): commit → fetch → merge → push.
  - on app open, ~30 s after you stop editing, on pull-to-refresh, and in the
    background every N minutes (WorkManager, 15 min minimum).
  - Merge conflicts never lose data: conflicting files are committed with conflict
    markers and shown in the note list so you can tidy them up.
- **Index.md maintenance**: every new note (from the *New note* button or by following a
  dangling wikilink) gets `- [[note]]` added at the end of the index's list of links, using
  that list's bullet. If the index has Foam's link reference definitions at the bottom, the
  entry goes above them and a matching definition is added. Deleting a note removes both. Settings has *Add missing notes to Index.md* for existing repos. Daily
  notes are left out unless you switch them on.
- **List bullet setting**: choose `-` (default) or `*` for the bullets the app writes in
  Index.md and with the editor's list and checkbox buttons. The buttons remove existing
  items written with either character.
- **Checklists**: tap a checkbox in the preview, or use the ☐ button in the editor
  (plain line → `- [ ]` → `- [x]` → `- [ ]`). Optional settings, off by default: strike
  through checked items (`~~text~~`) and append the completion date in brackets, e.g.
  `- [x] ~~Buy milk~~ [2026-11-08]`. Date formats: yyyy-mm-dd, mm-dd-yyyy, dd-mm-yyyy,
  d mmm yyyy, mmm d, yyyy, yyyy-mmm-dd; separator `-`, `/` or `.` (not used by the two
  formats with spaces). Both default to the phone's locale. Unchecking removes the
  strike-through and the bracketed date again.
- **markdownlint** (Settings → Editor, off by default): checks the note while you edit,
  using the repository's `.markdownlint.json` / `.jsonc` / `.yaml` or `.markdownlint-cli2.*`
  (or `markdownlint.config` in `.vscode/settings.json`), like the VS Code extension. Problems
  are highlighted; the *issues* chip lists them (tap one to jump to it) and offers *Fix all*
  for the rules markdownlint can fix.
- **Graph** (graph button on the note list, or *Show in graph* in a note's ⋮ menu): Foam's
  springy graph of notes, tags (front matter and inline `#tags`) and missing notes, linked
  by wikilinks, embeds and relative Markdown links. Drag nodes (they spring back), pinch to
  zoom, tap a node to highlight its neighbours and open it (or create a missing note).
  Filters for tags and missing notes; the layout is kept while the app runs.
- **Rename and move notes** (long-press a note, or the editor's ⋮ menu): every link is
  fixed — wikilinks (`[[note]]`, aliases, headings, embeds), relative Markdown links and
  Foam's link reference definitions — as are the moved note's own relative links. If a
  new name clashes with another note, links are path-qualified so each still points at
  the right note. Links inside code are left alone.
- **Daily notes**: calendar button opens/creates today's note. Reads Foam's
  `.vscode/settings.json` (`foam.openDailyNote.directory`, `filenameFormat`,
  `fileExtension`, `titleFormat`) and `.foam/templates/daily-note.md` (incl.
  `foam_template.filepath` and `$FOAM_DATE_*` variables). Default: `journal/yyyy-mm-dd.md`.
  `.foam/templates/new-note.md` is used for new notes too.
- **Preview** (markdown-it, bundled offline):
  - Wikilinks `[[note]]`, `[[note|alias]]`, `[[note#heading]]`, `[[folder/note]]`;
    unresolved links are highlighted and tapping one creates the note, like Foam.
  - Embeds `![[note]]` and `![[image.png]]`
  - Mermaid ` ```mermaid ` blocks (light/dark theme)
  - Footnotes `[^1]` and inline footnotes `^[text]`
  - Math via KaTeX: `$inline$`, `$$display$$`, `\( \)`, `\[ \]`
  - Superscript `x^2^` and subscript `H~2~O`
  - Checkboxes `- [ ]` / `- [x]` — tap in the preview to toggle and save
  - Relative links `[x](other.md)` and relative images
- **Editor**: monospace editor with autosave, *All / Cut / Copy / Paste* buttons (with nothing
  selected, Cut and Copy take the current line), a formatting toolbar (heading, bold,
  italic, checkbox, list, wikilink, sup, sub, math, footnote, mermaid, code, link) and
  wikilink autocomplete as soon as you type `[[`. Enter in a list starts the next item of
  the same kind (bullet, numbered with the same number, or unchecked checkbox); Enter on an
  empty item ends the list.

## Setup in the app

1. Create a personal access token with read/write access to the repo
   (GitHub: fine-grained token → *Contents: Read and write*).
2. Open the app → enter the HTTPS clone URL, branch, username and token → *Clone repository*.
   Leave the URL blank for a local-only repository.

## Building

Needs JDK 17 or 21 (Gradle 8.10 does not run on newer JDKs) and the Android SDK
(platform 35).

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install with
`adb install app/build/outputs/apk/debug/foam-for-android-debug.apk`, or open the folder in Android Studio.

Alternatively push this folder to GitHub: `.github/workflows/build.yml` builds the APK, runs the
unit tests (`./gradlew testDebugUnitTest`) and attaches the APK to the workflow run as an artifact.

To refresh the bundled JavaScript libraries: `scripts/fetch-vendor.sh`. markdownlint is
bundled separately with `scripts/build-markdownlint.sh` (needs Node.js; versions are at the
top of the script).

## Layout

| File | Purpose |
| --- | --- |
| `GitSync.kt` | clone / commit / fetch / merge / push, conflict handling |
| `SyncWorker.kt` | periodic and after-edit background sync |
| `NoteRepository.kt` | listing, search, rename/move, Index.md, daily notes, task toggling |
| `Links.kt` | wikilink resolution and link fixing for rename/move (unit-tested) |
| `Tasks.kt` | checking/unchecking items, completion dates |
| `Graph.kt`, `ui/GraphScreen.kt`, `assets/graph.html` | graph data (unit-tested) and the force-graph view |
| `Lint.kt`, `assets/lint.html` | markdownlint in a background WebView, config lookup |
| `Foam.kt` | Foam settings, templates, `$FOAM_*` variables, `dateformat` masks |
| `ui/` | Compose screens and the WebView preview |
| `assets/preview.*` | markdown-it pipeline with wikilink, task-list and mermaid plugins |

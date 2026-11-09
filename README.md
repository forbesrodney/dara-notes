# Foam for Android

An Android app for managing Markdown notes kept in a Git repository, compatible with
[Foam](https://foambubble.github.io/foam/) workspaces.

## Features

- **Git auto-sync** (JGit, HTTPS + personal access token): commit, fetch, merge, push.
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
- **Checklists**: tap a checkbox in the preview, or use the checkbox button in the editor
  (plain line, then `- [ ]`, then `- [x]`, then back to `- [ ]`). Optional settings, off by default: strike
  through checked items (`~~text~~`) and append the completion date in brackets, e.g.
  `- [x] ~~Buy milk~~ [2026-11-08]`. Date formats: yyyy-mm-dd, mm-dd-yyyy, dd-mm-yyyy,
  d mmm yyyy, mmm d, yyyy, yyyy-mmm-dd; separator `-`, `/` or `.` (not used by the two
  formats with spaces). Both default to the phone's locale. Unchecking removes the
  strike-through and the bracketed date again.
- **markdownlint** (Settings > Editing, off by default): checks the note while you edit,
  using the repository's `.markdownlint.json` / `.jsonc` / `.yaml` or `.markdownlint-cli2.*`
  (or `markdownlint.config` in `.vscode/settings.json`), like the VS Code extension. Problems
  are highlighted; the *issues* chip lists them (tap one to jump to it) and offers *Fix all*
  for the rules markdownlint can fix.
- **Graph** (graph button on the note list, or *Show in graph* in a note's menu): Foam's
  springy graph of notes, tags (front matter and inline `#tags`) and missing notes, linked
  by wikilinks, embeds and relative Markdown links. Drag nodes (they spring back), pinch to
  zoom, tap a node to highlight its neighbors and open it (or create a missing note).
  Filters for tags and missing notes; the layout is kept while the app runs.
- **Connections** (a note's menu > *Connections*): like Foam's Connections panel. *Links
  out* lists every link in the note (wikilinks, embeds, Markdown links and images, URLs)
  with its line and what it points to: a note (and heading), a file, a web address, a
  heading in the same note, or nothing (missing notes get a *Create* button). *Links in*
  lists the notes that link here, with the line. Tap an entry to open it. Links in code
  and link reference definitions are skipped.
- **Tags** (note list menu > *Tags*): like Foam's Tag Explorer, every tag (front matter and
  inline `#tags`) as a tree, so `dev/git` sits under `dev`, with a note count for each. Tap
  a tag to expand it and see its notes; tap a note to open it. A filter box narrows the
  list. In the graph, selecting a tag offers *Notes* to jump here.
- **Orphaned notes** (note list menu > *Orphaned notes*): like Foam's Orphans panel, the
  notes with no links to or from another note (tags don't count). Tap one to open it, or
  *Add to index* to link it from Index.md. Respects Foam's `foam.orphans.exclude` setting.
- **Placeholders** (note list menu > *Placeholders*): like Foam's Placeholders panel, the
  wikilinks that point to notes that don't exist yet, each with the notes that link to it.
  *Create* makes the note; tap a linking note to open it. Respects Foam's
  `foam.placeholders.exclude` setting.
- **Rename and move notes** (long-press a note, or the editor's menu): every link is
  fixed: wikilinks (`[[note]]`, aliases, headings, embeds), relative Markdown links and
  Foam's link reference definitions, as are the moved note's own relative links. If a
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
  - Checkboxes `- [ ]` / `- [x]`: tap in the preview to toggle and save
  - Relative links `[x](other.md)` and relative images
- **Editor**: monospace editor with autosave, *All / Cut / Copy / Paste* buttons (with nothing
  selected, Cut and Copy take the current line), a formatting toolbar (heading, bold,
  italic, checkbox, list, wikilink, sup, sub, math, footnote, mermaid, code, link) and
  wikilink autocomplete as soon as you type `[[`. Enter in a list starts the next item of
  the same kind (bullet, numbered with the same number, or unchecked checkbox); Enter on an
  empty item ends the list.

## Repository settings

Some behavior is configured in the notes repository itself, in the same files Foam and the
VS Code markdownlint extension use, so the app and VS Code follow the same rules.

### `.vscode/settings.json`

The app reads these keys from `.vscode/settings.json` at the root of the notes repository.
Any other keys are ignored.

| Setting | Used for | Default |
| --- | --- | --- |
| `foam.openDailyNote.directory` | Folder for daily notes (Settings > Notes > *Daily notes folder* overrides it) | `journal` |
| `foam.openDailyNote.filenameFormat` | Daily note file name, as a `dateformat` mask | `isoDate` (`yyyy-mm-dd`) |
| `foam.openDailyNote.fileExtension` | Daily note file extension | `md` |
| `foam.openDailyNote.titleFormat` | Daily note title, as a `dateformat` mask | `isoDate` |
| `foam.orphans.exclude` | Glob patterns for notes left out of *Orphaned notes* | none |
| `foam.placeholders.exclude` | Glob patterns for notes whose links are left out of *Placeholders* | none |
| `markdownlint.config` | markdownlint rules, used when the repository has no markdownlint config file | none |

Glob patterns are relative to the repository root: `*` matches within one folder, `**`
across folders, `?` one character and `{a,b}` either name. A plain folder name such as
`journal` also covers everything inside it. Comments and trailing commas are allowed, as in
VS Code.

```jsonc
{
  "foam.openDailyNote.directory": "journal",
  // Daily notes and templates aren't expected to be linked
  "foam.orphans.exclude": ["journal/**", "templates/**"],
  // Example links in templates aren't real placeholders
  "foam.placeholders.exclude": ["templates/**"]
}
```

### Other files

- `.foam/templates/new-note.md`: template for new notes.
- `.foam/templates/daily-note.md`: template for daily notes.
  Both templates support `foam_template` front matter (including `filepath`) and the `$FOAM_*`
  variables.
- markdownlint configuration, first one found: `.markdownlint-cli2.jsonc`,
  `.markdownlint-cli2.yaml`, `.markdownlint.jsonc`, `.markdownlint.json`, `.markdownlint.yaml`,
  `.markdownlint.yml`, then `markdownlint.config` in `.vscode/settings.json`.

### Things to check

- **Commit and push these files.** The app only sees what is in the repository. A global
  gitignore that excludes `.vscode/` hides `settings.json` from Git; to check, run
  `git check-ignore -v .vscode/settings.json` in the notes repository. If it is ignored, add
  `!/.vscode/settings.json` to the notes repository's `.gitignore`, then commit and push the
  file and sync in the app.
- **`.code-workspace` settings aren't read.** If you open your notes through a
  `.code-workspace` file, VS Code also uses the `"settings"` in it, but the app doesn't. Put
  settings the app should follow in `.vscode/settings.json`.

## Setup in the app

1. Create a personal access token with read/write access to the repo
   (GitHub: a fine-grained token with *Contents: Read and write*).
2. Open the app, enter the HTTPS clone URL, branch, username and token, then tap *Clone repository*.
   Leave the URL blank for a local-only repository.

## Building

Needs JDK 17 or 21 (Gradle 8.10 does not run on newer JDKs) and the Android SDK
(platform 35).

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install with
`adb install app/build/outputs/apk/debug/foamforandroid-debug.apk`, or open the folder in Android Studio.

Alternatively push this folder to GitHub: `.github/workflows/build.yml` builds the APK, runs the
unit tests (`./gradlew :shared:test testDebugUnitTest`) and attaches the APK to the workflow run as an artifact.

To refresh the bundled JavaScript libraries: `scripts/fetch-vendor.sh`. markdownlint is
bundled separately with `scripts/build-markdownlint.sh` (needs Node.js; versions are at the
top of the script).

## Layout

There are two Gradle modules:

- **`shared/`**: the note logic (package `app.foamforandroid.core`), in plain Kotlin with no
  Android dependencies, so a desktop app can reuse it later. All unit tests are here (`./gradlew :shared:test`).
- **`app/`**: the Android app: Compose screens, WebViews, settings storage and background sync.

| File | Purpose |
| --- | --- |
| `shared/.../GitSync.kt` | clone / commit / fetch / merge / push, conflict handling |
| `shared/.../GitErrors.kt` | plain-language hints for common Git errors |
| `shared/.../NoteRepository.kt` | listing, search, rename/move, Index.md, daily notes, task toggling |
| `shared/.../Links.kt` | wikilink resolution and link fixing for rename/move |
| `shared/.../IndexEditor.kt` | adding and removing Index.md entries |
| `shared/.../Tasks.kt` | checking/unchecking items, completion dates |
| `shared/.../Graph.kt` | graph data: notes, links and tags; orphans and placeholders |
| `shared/.../Connections.kt` | every link in a note and what it points to; backlinks |
| `shared/.../Tags.kt` | the tag tree for the Tag Explorer |
| `shared/.../Globs.kt` | VS Code-style glob matching for Foam settings |
| `shared/.../LintModel.kt` | markdownlint results and config lookup |
| `shared/.../Foam.kt` | Foam settings, templates, `$FOAM_*` variables, `dateformat` masks |
| `shared/.../Settings.kt` | the settings each platform must store |
| `app/.../AndroidSettings.kt` | settings in SharedPreferences; the token in encrypted preferences |
| `app/.../SyncWorker.kt` | periodic and after-edit background sync |
| `app/.../Lint.kt`, `assets/lint.html` | markdownlint in a background WebView |
| `app/.../ui/GraphScreen.kt`, `assets/graph.html` | the force-graph view |
| `app/.../ui/` | Compose screens and the WebView preview |
| `assets/preview.*` | markdown-it pipeline with wikilink, task-list and mermaid plugins |

`...` is `src/main/kotlin/app/foamforandroid/core` in `shared/` and `src/main/java/app/foamforandroid`
in `app/`; `assets/` is `app/src/main/assets`.

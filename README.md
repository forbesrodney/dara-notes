# Foam for Android

An Android app for managing Markdown notes kept in a Git repository, compatible with
[Foam](https://foambubble.github.io/foam/) workspaces. A desktop version (macOS for now; the
same code runs on Windows and Linux) shares the screens and note logic.

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
- **Note list, flat or by folder**: the button beside the search box switches between
  every note newest first (like Foam's flat view) and a folder tree (like Foam's
  hierarchical view) with a note count per folder. Tap a folder to open or close it; while
  searching, folders with matches open automatically. The choice is remembered.
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
  `.foam/templates/daily-note.md` (incl. `foam_template.filepath` and `$FOAM_DATE_*`
  variables). Default: `journal/yyyy-mm-dd.md`.
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
| `foam.orphans.exclude` | Glob patterns for notes left out of *Orphaned notes* | none |
| `foam.placeholders.exclude` | Glob patterns for notes whose links are left out of *Placeholders* | none |
| `markdownlint.config` | markdownlint rules, used when the repository has no markdownlint config file | none |

Glob patterns are relative to the repository root: `*` matches within one folder, `**`
across folders, `?` one character and `{a,b}` either name. A plain folder name such as
`journal` also covers everything inside it. Comments and trailing commas are allowed, as in
VS Code.

Templates never need excluding: `.foam` (and `.git`, `.vscode`, `.github`,
`node_modules`, `_site`, `_layouts`) is never scanned for notes, so templates and their
example links never show up in any view.

```jsonc
{
  // Daily notes aren't expected to be linked
  "foam.orphans.exclude": ["journal/**"],
  // Drafts link to notes that haven't been written yet
  "foam.placeholders.exclude": ["drafts/**"]
}
```

### Other files

- `.foam/templates/new-note.md`: template for new notes.
- `.foam/templates/daily-note.md`: template for daily notes. Set its `filepath` to choose
  where daily notes go (for example `journal/$FOAM_DATE_YEAR-$FOAM_DATE_MONTH-$FOAM_DATE_DATE.md`);
  without one they go in `journal`, or Settings > Notes > *Daily notes folder*.
  Both templates support `foam_template` front matter (including `filepath`) and the `$FOAM_*`
  variables.
- markdownlint configuration, first one found: `.markdownlint-cli2.jsonc`,
  `.markdownlint-cli2.yaml`, `.markdownlint.jsonc`, `.markdownlint.json`, `.markdownlint.yaml`,
  `.markdownlint.yml`, then `markdownlint.config` in `.vscode/settings.json`.

### Things to check

- **Commit and push these files.** The app only sees what is in the repository. A global
  gitignore that excludes `.vscode/` hides `settings.json` from Git; to check, run
  `git check-ignore -v .vscode/settings.json` in the notes repository (no output means it
  isn't ignored). If it is, add these lines to the notes repository's `.gitignore`, then commit
  and push the file and sync in the app. Git can't re-include a file inside an ignored folder,
  so the folder is re-included first and its other files ignored again:

  ```gitignore
  !/.vscode/
  /.vscode/*
  !/.vscode/settings.json
  ```
- **`.code-workspace` settings aren't read.** If you open your notes through a
  `.code-workspace` file, VS Code also uses the `"settings"` in it, but the app doesn't. Put
  settings the app should follow in `.vscode/settings.json`.

## Setup in the app

1. Create a personal access token with read/write access to the repo
   (GitHub: a fine-grained token with *Contents: Read and write*).
2. Open the app, enter the HTTPS clone URL, branch, username and token, then tap *Clone repository*.
   Leave the URL blank for a local-only repository.

## Building

Needs JDK 17 or newer (JDK 21 recommended) and the Android SDK (platform 37, build tools
36.0.0).

### Android Studio

1. Install Android Studio (`brew install --cask android-studio`) and choose the *Standard*
   setup, which puts the SDK in `~/Library/Android/sdk`.
2. In *SDK Manager*, install *Android SDK Platform 37* and, for the emulator, the API 35
   *Google APIs ARM 64 v8a System Image*; under *SDK Tools*, *Build-Tools 36.0.0* and
   *Command-line Tools (latest)*.
3. *Open* this folder. Android Studio's own JDK works; to build with the same JDK as the
   command line, set *Settings > Build, Execution, Deployment > Build Tools > Gradle >
   Gradle JDK* to JDK 21 (for example Temurin, `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`).
4. Run the *app* configuration on an emulator or device.

### Command line

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install with
`adb install app/build/outputs/apk/debug/foamforandroid-debug.apk`. The scripts below look for
the SDK in `$ANDROID_HOME`, else `~/Library/Android/sdk`.

Alternatively push this folder to GitHub: `.github/workflows/build.yml` builds the APK, runs the
unit tests (`./gradlew :shared:jvmTest testDebugUnitTest`) and attaches the APK to the workflow run as an artifact.

### Desktop app

The desktop app is the `desktop/` module: the same screens in a Compose Desktop window, named
for its system (*Foam for macOS*, *Foam for Windows*, *Foam for Linux*). Run it
with the *Run desktop app* configuration, or:

```bash
./gradlew :desktop:run
```

*Package desktop app* (`./gradlew :desktop:packageDistributionForCurrentOS`) builds an installer
for the computer you run it on: a `.dmg` on macOS, `.msi` on Windows, `.deb` on Linux, in
`desktop/build/compose/binaries/main/`. Each must be built on its own system, because the bundled
Java runtime and JavaFX are native. The package isn't signed yet, so macOS asks for confirmation
the first time it opens (right-click, *Open*).

Packaging needs a self-contained JDK 21, because the app bundles a Java runtime made from it.
Homebrew's `openjdk` formulas link to other Homebrew libraries, so an app packaged with it only runs on
Macs that have them. Install Eclipse Temurin 21, which the build finds by default:

```bash
brew install --cask temurin@21
```

To use another JDK, set `foam.packagingJdk=/path/to/jdk/Contents/Home` in
`~/.gradle/gradle.properties`. Building and running don't need it.

How it differs from Android:

- **Storage:** the repository is cloned into `~/Library/Application Support/Foam for macOS/repo`
  (Windows: `%APPDATA%\Foam for Windows\repo`; Linux: `~/.local/share/foam-for-linux/repo`).
  Settings are in Java preferences, and the access token in the system keychain (macOS
  Keychain, Windows Credential Manager, or the Secret Service on Linux).
- **Sync:** *Sync every* runs on a timer while the app is open, with no 15-minute minimum; the
  app also syncs when its window comes to the front. Commits say "from macOS".
- **Preview, graph and markdownlint:** the same web pages as on Android, in JavaFX's WebView.

### Scripts

Each script also has a shared Android Studio run configuration (in `.run/`), so it can be
picked from the run configuration list in the toolbar. *Run unit tests*, *Build release APK*
and *Build release AAB* run the matching Gradle tasks.

| Script | What it does |
| --- | --- |
| `scripts/fetch-deps.sh` | Downloads everything needed to build: Android SDK packages (you accept any new licenses), the Gradle distribution and every Gradle dependency, then runs one full build for the tools Gradle fetches during a build. Afterward, builds work offline (`./gradlew --offline ...`). |
| `scripts/clean.sh` | Deletes everything generated by building: `build/` folders, `.gradle/` and `.kotlin/`. Keeps committed files (including the JavaScript libraries), `local.properties` and the shared downloads in `~/.gradle`. |
| `scripts/fetch-vendor.sh` | Re-downloads the JavaScript libraries in `app/src/main/assets/vendor` at the versions pinned in the script. |
| `scripts/build-markdownlint.sh` | Rebuilds `vendor/markdownlint.min.js` (needs Node.js). Versions are pinned in `scripts/markdownlint/package.json`, and all sub-dependencies in its `package-lock.json`, so a rebuild gives the same file. |
| `scripts/refresh-all.sh` | Runs `fetch-deps.sh`, `fetch-vendor.sh` and `build-markdownlint.sh`, then reports whether any committed library changed. |
| `scripts/check-updates.sh` | Lists newer versions of the pinned JavaScript libraries. Changes nothing. |
| `scripts/desktop-icons.py` | Draws the desktop app icons (`desktop/icons/`) from `art/foam_icon.svg`. Rerun it after changing the artwork. |
| `scripts/changelog.sh` | Regenerates `CHANGELOG.md` with git-cliff (`.cliff.toml`) and lists commits it skipped. |

The JavaScript libraries are committed, so a checkout builds without Node.js or network
access to the CDN. To upgrade one, change its version in `fetch-vendor.sh` (or in
`scripts/markdownlint/package.json`, then run `npm install --package-lock-only` in that
folder), run the script, test, and commit the result.

## Layout

There are three Gradle modules. `shared` and `ui` are Kotlin Multiplatform modules, so the same
code can later build the desktop and iOS apps; `app` is the Android app around them.

- **`shared/`**: the note logic (package `app.foamforandroid.core`), with no UI and no Android
  dependencies. Nearly all of it is in `src/commonMain`, using multiplatform libraries: Okio for
  files, kotlinx-datetime and kotlinx.serialization. `src/jvmMain` holds the JVM pieces: Git
  with JGit, and month names and date formats from `java.time`. All unit tests are here, in
  `src/commonTest` and `src/jvmTest` (`./gradlew :shared:jvmTest`).
- **`ui/`**: every screen and the view model, in Compose Multiplatform (`src/commonMain`). The
  few platform pieces are `expect` declarations with Android versions in `src/androidMain` (the
  preview and graph WebViews, wallpaper colors) and placeholders in `src/jvmMain` for the
  desktop app. Other platform services come through the `AppPlatform` interface.
- **`app/`**: the Android app: `MainActivity`, settings storage, background sync, markdownlint
  in a WebView, and the web assets (`app/src/main/assets`).

| File | Purpose |
| --- | --- |
| `shared/.../GitSync.kt` | one sync at a time, sync status; `GitEngine`, what each platform's Git provides |
| `shared/jvmMain/.../JGitEngine.kt` | clone / commit / fetch / merge / push with JGit, conflict handling |
| `shared/.../GitErrors.kt` | plain-language hints for common Git errors |
| `shared/.../NoteRepository.kt` | listing, search, rename/move, Index.md, daily notes, task toggling |
| `shared/.../Links.kt` | wikilink resolution and link fixing for rename/move |
| `shared/.../IndexEditor.kt` | adding and removing Index.md entries |
| `shared/.../Tasks.kt` | checking/unchecking items, completion dates |
| `shared/.../Graph.kt` | graph data: notes, links and tags; orphans and placeholders |
| `shared/.../Connections.kt` | every link in a note and what it points to; backlinks |
| `shared/.../NoteTree.kt` | notes arranged by folder for the folder view |
| `shared/.../Tags.kt` | the tag tree for the Tag Explorer |
| `shared/.../Globs.kt` | VS Code-style glob matching for Foam settings |
| `shared/.../LintModel.kt` | markdownlint results and config lookup |
| `shared/.../Foam.kt` | Foam settings, templates, `$FOAM_*` variables |
| `shared/.../Settings.kt` | the settings each platform must store |
| `shared/.../Platform.kt` | what each platform supplies: Git, locks, month names, date formats |
| `ui/.../MainViewModel.kt` | navigation, note actions, sync; `AppPlatform.kt`, what each app supplies |
| `ui/.../ui/App.kt` | theme, back navigation and the current screen |
| `ui/.../ui/*Screen.kt` | the screens |
| `ui/.../ui/PlatformViews.kt` | the preview and graph web views each platform provides |
| `app/.../AndroidPlatform.kt` | the Android `AppPlatform`: storage, WorkManager sync, markdownlint |
| `app/.../AndroidSettings.kt` | settings in SharedPreferences; the token in encrypted preferences |
| `app/.../SyncWorker.kt` | periodic and after-edit background sync |
| `app/.../Lint.kt`, `assets/lint.html` | markdownlint in a background WebView |
| `assets/graph.html` | the force-graph page |
| `assets/preview.*` | markdown-it pipeline with wikilink, task-list and mermaid plugins |

`...` is `src/commonMain/kotlin/app/foamforandroid/core` in `shared/` (or `jvmMain` where
shown), `src/commonMain/kotlin/app/foamforandroid` in `ui/`, and
`src/main/java/app/foamforandroid` in `app/`; `assets/` is `app/src/main/assets`.

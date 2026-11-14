# Dara Notes

An app for Markdown notes kept in a Git repository, for Android, iOS and the desktop (macOS
for now; the same code runs on Windows and Linux). It works with
[Foam](https://foambubble.github.io/foam/) workspaces: it reads Foam's settings and
templates and follows its conventions for links, tags and daily notes. Dara Notes is an
independent project, not made by or affiliated with the Foam developers.

The name and icon come from the Dara knot (Irish *doire*, oak), a Celtic knot whose
interlacing is read as an oak's roots: notes tied together by links.

## Using the app

The help is in [docs/](docs/index.md), and the same pages are built into the app (*Help*
in the note list's menu, or on the Settings screen; on a Mac, also the Help menu):

- [Getting started](docs/getting-started.md): connecting a repository, and syncing
- [Writing notes](docs/writing.md)
- [Finding and organizing notes](docs/organizing.md)
- [Templates](docs/templates.md), with every template variable
- [Foam and repository settings](docs/foam-settings.md)
- [Troubleshooting](docs/troubleshooting.md)

## Privacy

The app keeps your notes and settings on your device and sends them only to the Git host you
set up; it has no analytics, ads or tracking. See [PRIVACY.md](PRIVACY.md).

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

The APK lands in `androidApp/build/outputs/apk/debug/`. Install with
`adb install androidApp/build/outputs/apk/debug/daranotes-debug.apk`. The scripts below look for
the SDK in `$ANDROID_HOME`, else `~/Library/Android/sdk`.

Alternatively push this folder to GitHub: `.github/workflows/build.yml` builds the APK, runs the
unit tests (`./gradlew :shared:jvmTest testDebugUnitTest`) and attaches the APK to the workflow run as an artifact.

### Desktop app

The desktop app is the `desktopApp/` module: the same screens in a Compose Desktop window. Run it
with the *Run desktop app* configuration, or:

```bash
./gradlew :desktopApp:run
```

*Package desktop app* (`./gradlew :desktopApp:packageDistributionForCurrentOS`) builds an installer
for the computer you run it on: a `.dmg` on macOS, `.msi` on Windows, `.deb` on Linux, in
`desktopApp/build/compose/binaries/main/`. Each must be built on its own system, because the bundled
Java runtime and JavaFX are native.

On macOS, the `.dmg` is signed with your *Developer ID Application* certificate when
`local.properties` (not committed) has your Apple Developer team ID:

```
apple.teamId=ABCDE12345
```

*Build macOS release* (`scripts/macos-release.sh`) builds the signed app, puts it in a `.dmg`
with `scripts/macos-dmg.sh` (a window with the app, a link to Applications and an arrow
between them, and the app's disk icon), has Apple notarize it and staples the ticket, so it
opens on any Mac without a warning. That `.dmg` is the one to share: the one *Package desktop
app* makes on macOS is jpackage's plainer version, for trying the installer locally. It needs
notarization credentials saved once in the keychain under the profile `foam-notary`, with an
App Store Connect API key (or an Apple ID and app-specific password; see the script):

```bash
xcrun notarytool store-credentials foam-notary --key /path/AuthKey_XXXXXXXXXX.p8 --key-id XXXXXXXXXX --issuer <issuer UUID>
```

Without a team ID the package is unsigned, and macOS asks for confirmation the first time it
opens (right-click, *Open*).

Packaging needs a self-contained JDK 21, because the app bundles a Java runtime made from it.
Homebrew's `openjdk` formulas link to other Homebrew libraries, so an app packaged with it only runs on
Macs that have them. Install Eclipse Temurin 21, which the build finds by default:

```bash
brew install --cask temurin@21
```

To use another JDK, set `daranotes.packagingJdk=/path/to/jdk/Contents/Home` in
`~/.gradle/gradle.properties`. Building and running don't need it.

How it differs from Android:

- **Storage:** the repository is cloned into `~/Library/Application Support/Dara Notes/repo`
  (Windows: `%APPDATA%\Dara Notes\repo`; Linux: `~/.local/share/dara-notes/repo`).
  Settings are in Java preferences, and the access token in the system keychain (macOS
  Keychain, Windows Credential Manager, or the Secret Service on Linux).
- **Sync:** *Sync every* runs on a timer while the app is open, with no 15-minute minimum; the
  app also syncs when its window comes to the front, and before it quits if edits are waiting
  to be pushed (the window closes first; it gives up after 20 seconds). The toolbar keeps a
  sync button. Commits say "from macOS".
- **Preview, graph and markdownlint:** the same web pages as on Android, in JavaFX's WebView.

### iOS app

`iosApp/` is a small Xcode project around the shared screens: `DaraNotesApp.swift` shows the
Compose UI from the `DaraUI` framework, which its *Compile Kotlin framework* build phase
(`iosApp/build-kotlin.sh`) builds with Gradle; the `web/` pages are bundled as resources. Needs
Xcode with an iOS simulator runtime (*Xcode > Settings > Components*) and a JDK 21. Run it in
the simulator with the *Run iOS app (simulator)* configuration, or:

```bash
scripts/ios-run.sh
```

or open `iosApp/iosApp.xcodeproj` in Xcode. A device or release build needs your Apple
Developer team ID in `iosApp/Configuration/Local.xcconfig` (not committed):

```
TEAM_ID = ABCDE12345
```

*Build iOS release* (`scripts/ios-release.sh`) archives and exports a signed `.ipa` for
TestFlight and the App Store into `iosApp/build/release/export/` (`scripts/ios-release.sh
release-testing` for an ad hoc build). Signing is automatic, with the Apple ID in *Xcode >
Settings > Accounts* or an App Store Connect API key (see the script).

How it differs from Android:

- **Storage:** the repository is cloned into the app's Application Support folder; settings are
  in `NSUserDefaults` and the access token in the Keychain (this device only, readable after
  the first unlock so background sync can use it).
- **Sync:** *Sync every* asks iOS for a background refresh no sooner than that; iOS decides when
  it runs (usually while the phone is idle and online). The app also syncs when it opens, 30
  seconds after an edit while it's open, and when it goes to the background if edits are
  waiting to be pushed (iOS allows about 30 seconds for that). Commits say "from iOS".
- **Preview, graph and markdownlint:** the same pages in WKWebView, served under a private
  `dara:` scheme. The pages' synchronous calls (resolving wikilinks, reading embedded notes)
  are answered through that scheme too.

Git on iOS uses [libgit2](https://libgit2.org), built from source by
`scripts/build-libgit2.sh` (HTTPS through Apple's Secure Transport; no SSH). Gradle runs the
script the first time it's needed, which takes about two minutes and needs CMake
(`brew install cmake`); the libraries land in `shared/build/libgit2`.

### Scripts

Each script also has a shared Android Studio run configuration (in `.run/`), so it can be
picked from the run configuration list in the toolbar. *Run unit tests*, *Build release APK*
and *Build release AAB* run the matching Gradle tasks.

| Script | What it does |
| --- | --- |
| `scripts/fetch-deps.sh` | Downloads everything needed to build: Android SDK packages (you accept any new licenses), the Gradle distribution and every Gradle dependency, then runs one full build for the tools Gradle fetches during a build. Afterward, builds work offline (`./gradlew --offline ...`). |
| `scripts/clean.sh` | Deletes everything generated by building: `build/` folders, `.gradle/` and `.kotlin/`. Keeps committed files (including the JavaScript libraries), `local.properties` and the shared downloads in `~/.gradle`. |
| `scripts/fetch-vendor.sh` | Re-downloads the JavaScript libraries in `web/vendor` at the versions pinned in the script. |
| `scripts/build-markdownlint.sh` | Rebuilds `vendor/markdownlint.min.js` (needs Node.js). Versions are pinned in `scripts/markdownlint/package.json`, and all sub-dependencies in its `package-lock.json`, so a rebuild gives the same file. |
| `scripts/refresh-all.sh` | Runs `fetch-deps.sh`, `fetch-vendor.sh` and `build-markdownlint.sh`, then reports whether any committed library changed. |
| `scripts/check-updates.sh` | Lists newer versions of the pinned JavaScript libraries. Changes nothing. |
| `scripts/app-icons.py` | Draws the app icons with `scripts/KnotIcon.java`, which holds the knot's design: the desktop icons (`desktopApp/icons/`), the iOS asset catalog, the Android adaptive icon and `art/dara_icon.svg`; also the `.dmg` window background. Rerun it after changing the design (needs a JDK). |
| `scripts/build-libgit2.sh` | Builds libgit2 for iPhone, the iOS Simulator and the Mac (Gradle runs it when needed). |
| `scripts/macos-release.sh` | Builds the signed macOS `.dmg`, notarizes it and staples the ticket; also zips it, for apps that won't send a `.dmg` (such as Signal). |
| `scripts/macos-dmg.sh` | Puts the packaged macOS app in a `.dmg` with a link to Applications, the arrow background and the app's disk icon (`desktopApp/packaging/`, `desktopApp/icons/app.icns`), using dmgbuild, which it installs into `desktopApp/build/dmgbuild`. |
| `scripts/ios-release.sh` | Archives the iOS app and exports a signed `.ipa` (TestFlight/App Store, or ad hoc). |
| `scripts/ios-run.sh` | Builds the iOS app and runs it in the iOS Simulator (optionally pass a simulator name or ID). |
| `scripts/bump-version.sh` | Raises the version of all the apps together: `micro` (1.2.3 to 1.2.4), `minor` (1.3.0), `major` (2.0.0), or an exact version such as `1.4.0`. Also adds 1 to the Android `versionCode` and the iOS build number, which `build` raises alone (for another upload of the same version). `--dry-run` shows the change without writing it. Doesn't commit or tag. |
| `scripts/changelog.sh` | Regenerates `CHANGELOG.md` with git-cliff (`.cliff.toml`) and lists commits it skipped. |

The JavaScript libraries are committed, so a checkout builds without Node.js or network
access to the CDN. To upgrade one, change its version in `fetch-vendor.sh` (or in
`scripts/markdownlint/package.json`, then run `npm install --package-lock-only` in that
folder), run the script, test, and commit the result.

## Layout

| Folder | What it is |
| --- | --- |
| `shared/` | The note logic, used by every app (package `app.daranotes.core`) |
| `ui/` | Every screen and the view model, used by every app |
| `web/` | The preview, graph and markdownlint pages and their JavaScript libraries, used by every app |
| `docs/` | The help pages, shown on GitHub and built into every app |
| `androidApp/` | The Android app |
| `desktopApp/` | The desktop app |
| `iosApp/` | The iOS app: the Xcode project around `ui` |

- **`shared/`** has no UI and no platform dependencies. Nearly all of it is in
  `src/commonMain`, using multiplatform libraries: Okio for files, kotlinx-datetime and
  kotlinx.serialization. `src/jvmMain` holds the JVM pieces (Git with JGit; month names and
  date formats from `java.time`) and `src/appleMain` the iOS ones (Git with libgit2, through
  `src/nativeInterop/cinterop/libgit2.def`; Foundation for the rest). All unit tests
  are here, in `src/commonTest` and `src/jvmTest` (`./gradlew :shared:jvmTest`; also
  `:shared:macosArm64Test` and `:shared:iosSimulatorArm64Test`).
- **`ui/`** is Compose Multiplatform (`src/commonMain`). The few platform pieces are `expect`
  declarations, with versions in `src/androidMain` (Android WebViews, wallpaper colors),
  `src/jvmMain` (JavaFX web views for the desktop) and `src/iosMain`. Other platform services
  come through the `AppPlatform` interface.
- **`web/`** is served to each app's web views at `/assets/` (Android bundles it as its assets
  folder; the desktop app as resources; iOS will bundle it too).
- **`docs/`** holds the help as plain Markdown, with relative links between pages so they
  work on GitHub. The `:ui:generateHelpPages` Gradle task compiles these files and
  `PRIVACY.md` into `HelpPages.kt`, so each app shows the help for its own version, offline.
  A link to another page opens it in the app's help; a new page needs no other changes.
- **`androidApp/`**, **`desktopApp/`** and **`iosApp/`** are thin: each starts the shared UI and
  supplies its platform's settings storage, background sync and markdownlint.

| File | Purpose |
| --- | --- |
| `shared/.../GitSync.kt` | one sync at a time, sync status; `GitEngine`, what each platform's Git provides |
| `shared/jvmMain/.../JGitEngine.kt` | clone / commit / fetch / merge / push with JGit, conflict handling |
| `shared/appleMain/.../LibGit2Engine.kt` | the same with libgit2, for iOS |
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
| `ui/.../ui/HelpScreen.kt` | the help pages, their links and tables of contents |
| `ui/.../ui/TemplatesScreen.kt`, `TemplateEditing.kt` | the Templates screen; the editor's variable list and example preview |
| `androidApp/.../AndroidPlatform.kt` | the Android `AppPlatform`: storage, WorkManager sync, markdownlint |
| `androidApp/.../AndroidSettings.kt` | settings in SharedPreferences; the token in encrypted preferences |
| `androidApp/.../SyncWorker.kt` | periodic and after-edit background sync |
| `androidApp/.../Lint.kt`, `web/lint.html` | markdownlint in a background WebView |
| `web/graph.html` | the force-graph page |
| `web/preview.*` | markdown-it pipeline with wikilink, task-list and mermaid plugins |

`...` is `src/commonMain/kotlin/app/daranotes/core` in `shared/` (or `jvmMain` where
shown), `src/commonMain/kotlin/app/daranotes` in `ui/`, and
`src/main/java/app/daranotes` in `androidApp/`.

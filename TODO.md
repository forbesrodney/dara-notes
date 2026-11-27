<!-- cSpell:ignore markdownlint -->
# TODO

## Future Ideas/Work

Not all of these will become reality.

- Linux
  - [ ] add to appropriate package managers for Ubuntu, Debian, Fedora and RedHat
  - [ ] add to Linux Homebrew
- macOS
  - [ ] add to macOS Homebrew
- Windows
  - [ ] properly sign build
  - [ ] add to Microsoft Store
- General
  - [ ] ability to encrypt the repo with PGP keys, compatible with git-remote-gcrypt (roughly 4-6 weeks)
    - only the remote is encrypted (packs plus an encrypted manifest); the local repo stays plain,
      so merging, history and edit dates work as now
    - Foam still works for anyone who clones with `git clone gcrypt::<url>` (git-remote-gcrypt and GnuPG);
      the repo is unusable only without the key
    - per-file encryption (git-crypt) ruled out: leaks file names (note titles) and history,
      and every concurrent edit becomes a whole-file conflict
    - hard parts
      - reimplement gcrypt's protocol for JGit (Android, desktop) and libgit2 (iOS), matching it exactly
      - OpenPGP: Bouncy Castle on Android and desktop; on iOS GopenPGP, rPGP or ObjectivePGP
        (check ObjectivePGP's license for commercial use)
      - keys: import a private key, store it in the Keychain/Keystore/OS keychain, handle passphrases;
        no hardware keys (YubiKey) at first
      - every gcrypt push is effectively a force push: two devices syncing at once could lose a push;
        check the manifest before pushing and retry, and see how gcrypt handles it first
      - packs pile up and repacking re-uploads everything, noticeable for big repos on phones
      - setup and recovery: turn it on, clone on a new device, warn that losing the key loses the notes
    - not covered: notes on the device stay unencrypted (relies on device/disk encryption);
      the server still sees repo size and push times
    - order
      1. read gcrypt's source: pin down the format and how concurrent pushes are handled
      2. desktop-only proof of concept (Bouncy Castle, JGit), tested both ways against real git-remote-gcrypt
      3. port to Android (shares the JVM code)
      4. iOS crypto library last (riskiest choice)
  - [ ] ability to sync with multiple repos
    - Foam is often used for notes within a project repo, it would be nice to have access to these notes too  
      Cloning the whole repo of a software project to get access to notes is out of the question.  
      Command-line git can do it with a partial clone (saves the download) and a sparse checkout (saves disk):
      ```bash
      git clone --no-checkout --depth=1 --filter=tree:0 <repository-url>
      cd <repository-name>
      git sparse-checkout set --cone <path/to/directory>
      git checkout
      ```
    - Dara's git libraries can't do either (check their trackers first, findings were a few years old)
      - JGit (5.13.3) fails on files a partial clone left out; no sparse checkout support known
      - libgit2 (1.9.7) has no partial clone (issue #5564); sparse checkout PR #6394 seemed stalled
      - both support shallow clones (latest snapshot, no history)
    - option 1 (recommended, about 3-5 days): a "notes folder" repo setting plus shallow clones
      - Dara lists, searches, links and creates notes only under the folder (e.g. `docs/notes`)
      - root config still applies: `.foam/templates`, `.vscode/settings.json`, `cspell.json`, `.markdownlint.json`
      - optional depth-1 clone skips history, usually the bulk of a project's download;
        the current snapshot of everything is still downloaded and kept on disk
      - calendar edit dates come from history: in a shallow clone older notes all look edited on
        the clone date; show only later edits, or fetch more history in the background
    - option 2 (several weeks): host APIs (GitHub, GitLab, Gitea) to read and commit one folder;
      truly partial, but per host, no plain git/SSH, and sync and merging rebuilt around the API
    - option 3 (months, not realistic): add partial clone support to JGit and libgit2, or fork them
    - project repo issues, whichever option
      - Dara's automatic sync commits fill a team repo's history with small commits
      - protected `main` (pull requests required) rejects pushes; syncing to a `notes` branch
        avoids that, but someone has to merge it
      - wikilinks to files outside the folder break or show as missing
      - large files or Git LFS, and pushing may need write access the user doesn't have
    - do option 1 first, then see whether truly partial downloads are still needed
  - [ ] enhance template support
    - support missing variables
      - $FOAM_DATE_FORMAT (${FOAM_DATE_FORMAT:YYYY-MM-DD})
      - $FOAM_CURRENT_DIR (not sure why)
      - $FOAM_PREVIOUS_DAILY_NOTE (not sure how)
    - support missing VSCode style variables
      - ${1:placeholder} tab stops  
        supporting $1 might be enough, would allow text above and below where it leaves the cursor
      - ${VAR:default} (not sure how)
  - [ ] share to Dara: create a new note from text shared by another app
    - e.g. meeting transcripts (Voice Memos, Google Recorder), web clippings, emails
    - Android: intent filter for shared text (about a day)
    - iOS: Share Extension, sharing the repo with the app via an App Group (a few days)
    - desktop: drag and drop text or `.md` files onto the window, or an Import command
  - [ ] clear the Compose deprecation warnings (after launch; retest each platform afterwards)
    - `BackHandler` in `App.kt` and `SettingsScreen.kt`: use `NavigationEventHandler` (navigation-event library);
      test the Android back gesture and the unsaved-changes prompt in Settings
    - `LocalClipboardManager` in `EditorScreen.kt`: use `LocalClipboard` (suspend functions); test copy and paste
    - `SwingPanel(background = ...)` in `PlatformViews.jvm.kt` (desktop): use the overload without `background`,
      setting the color in the panel's factory; test the preview and graph web views
  - [ ] list the editor's shortcuts (Bold, Italic, Strike-through, check or uncheck task) in the
    system's shortcut lists; they work, but only New note, Today's note and Back are listed
    - Android (Meta+/): add an "Editor" group in `MainActivity.onProvideKeyboardShortcuts`;
      it only lists them, a few lines
    - iPad (hold Cmd): a `UIKeyCommand` would run the shortcut itself instead of the editor,
      so it needs a way to list them without taking the keys, or to pass them on to the editor
  - [ ] editor: never insert a hard tab (markdownlint's MD010 flags them, and tools disagree on
    their width); Tab should always type spaces
    - today Tab outside a list or selection types a tab character (`TextFieldValue.tab()` in
      `Indenting.kt`); indenting lines and list items already uses spaces
    - decide the rules later: how many spaces (the note's indent unit, 2 or 4, or to the next
      tab stop), and what Tab does mid-line
    - maybe also turn tabs into spaces in pasted text, and offer a fix for existing tabs
  - [ ] guess the Git username from the remote URL (about half a day)
    - show it as the Username field's hint, and use it when the field is left blank (today blank means `git`)
    - a username in the URL (`https://user@bitbucket.org/...`, as Bitbucket's clone URLs have) is the login
    - the repo owner (`github.com/owner/repo`) is only a guess: wrong for organization or team repos
    - GitHub and Azure DevOps ignore the username with a token, so keep sending `git` there (maybe show 'git' and make read-only);
      it matters for Bitbucket and maybe Gitea/Forgejo
    - a wrong guess needs a clear sync error ("check the username and token")
  - [ ] Kanban boards, compatible with Obsidian's Kanban plugin
    - a board is a note with `kanban-plugin` front matter: `##` headings are columns, list items are cards
      (check real files for the exact format, e.g. the Archive section and settings block)
    - open such notes as a board instead of the preview, with the raw Markdown still editable
    - read-only board (about a day), then add/check/move cards (about a week with drag and drop)
    - stays plain Markdown, so Foam can still read and edit boards
  - [ ] Dataview queries (a subset)
    - e.g. `LIST`/`TABLE` of notes by tag or folder, simple `WHERE` and `SORT`, rendered in the preview
    - needs a query parser and an index of front matter, tags, tasks and inline fields (`key:: value`)
    - a week or two for a small subset; full compatibility (including DataviewJS) isn't realistic
    - ask which queries people actually use before starting
    - "#wifi && #hacking WHERE note created Date > 2010" could maybe be working into the search box
    - carry forward of uncompleted tasks could maybe be added to template front matter
      - when checked/unchecked, it would need to search back and check/uncheck all old tasks of the same name
  - [ ] graph view on small phones: a pinch that starts on a node drags the node instead of zooming
    (each node's touch area is at least 14 points across, so on a crowded graph they cover a lot of the screen)
    - zoom in and out buttons (+ and -) in the graph's toolbar, beside the fit button (about half a day)
    - turn off node dragging on touch screens (`enableNodeDrag(false)` when the main pointer is coarse;
      a one-line change): tap to select, panning and pinching still work anywhere; dragged nodes spring back
      anyway, so little is lost, but tablets lose dragging too
    - harder: keep dragging, but cancel the drag and zoom instead when a second finger touches down;
      means working around force-graph's own touch handling, fiddly on both iOS and Android
  - [ ] forms: a template that asks for fields instead of just a title (about a week, native)
    - fields in a `dara_form` block in the template's front matter, beside `foam_template` (Foam ignores it)
      - types: text, multiline, number, date, choice, checkbox; label, required, default (e.g. `today`)
      - body uses `${FORM_field}` variables; check what Foam does with unknown variables first
    - answers saved as the new note's front matter (`form: <template>`, one key per field), also filled into the body
    - "Edit as form" on notes with `form:` updates only the front matter, never redoes the body
    - naming: the template's `filepath`, also accepting field variables
      - no `filepath`: a field marked as the title, else the first required text field
      - repeated forms: add time variables (`$FOAM_DATE_HOUR$FOAM_DATE_MINUTE`)
      - name taken: add `-2`, `-3`; clean unsafe characters like `FOAM_TITLE_SAFE`
    - native Compose form recommended over a JS library in the web view (SurveyJS, JSON Forms,
      react-jsonschema-form): no bridge, no web view keyboard quirks, matches the app;
      keep field definitions close to a JSON Schema subset so a library stays possible
    - Obsidian's Modal Form keeps forms in plugin settings, nothing to be compatible with;
      front matter values work with Obsidian properties and Dataview

## Known Issues

- iOS: with a hardware keyboard, a dialog with a text field (New note, Rename, Move, template
  names, Add a tag) takes two Esc presses to close; the first only drops the field's focus
  - the first Esc never reaches the app's code (no key event, no Back event); the second
    becomes Back, which closes the dialog
  - Compose shows dialogs in their own view controller (`ComposeLayersViewController`), and
    something in UIKit or Compose's iOS text input takes that first Esc there; in the editor,
    Esc goes Back on the first press
  - closing the dialog when its field loses focus would work, but would also close it at the
    wrong times (switching apps); check again after Compose updates

## Not Planned

- Vim key bindings: they'd only make sense with a hardware keyboard, mostly on the desktop,
  and the app aims to work the same on every platform; the notes are plain Markdown in a Git
  repository, so Vim itself can edit them alongside the app

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
  - [ ] handle markdownlint similar to spell checking
    - disable in note - '<!-- markdownlint-disable-file -->'
    - disable rule in note - '<!-- markdownlint-disable-file MD013 MD033 -->'
    - disable rule globally (.markdownlint.json) - '"MD013": false'
  - [ ] share to Dara: create a new note from text shared by another app
    - e.g. meeting transcripts (Voice Memos, Google Recorder), web clippings, emails
    - Android: intent filter for shared text (about a day)
    - iOS: Share Extension, sharing the repo with the app via an App Group (a few days)
    - desktop: drag and drop text or `.md` files onto the window, or an Import command
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

## Needs Further Testing

- General
  - [ ] right click brings up context menu
  - [ ] default action on popups (create/move/rename) show as active once field is changed and is activated with Enter
  - [ ] tab/shift+tab to indent/un-indent selected group of lines or list items
- [ ] Windows desktop app (msi)
- [ ] Linux desktop app (rpm, deb)
- macOS desktop app
  - [ ] preview gets weird with the font after a while, requires restarting app

## Known Issues

- None

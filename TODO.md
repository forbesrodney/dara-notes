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
  - [ ] ability to encrypt the repo (thinking something along the lines of git-remote-gcrypt)
    - this would break compatibility with Foam
  - [ ] ability to sync with multiple repos
    - Foam is often used for notes within a project repo, it would be nice to have access to these notes too  
      Cloning the whole repo of a software project to get access to notes is out of the question.  
      Technically, it's possible using a sparse checkout.
      ```bash
      git clone --no-checkout --depth=1 --filter=tree:0 <repository-url>
      cd <repository-name>
      git sparse-checkout set --cone <path/to/directory>
      git checkout
      ```
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

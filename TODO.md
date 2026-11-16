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
      - Foam is often used for notes within a project repo, it would be nice to have access to these notes to  
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
 
## Needs Testing

- [ ] Windows desktop app (msi)
- [ ] Linux desktop app (rpm, deb)

## Known Issues

- General
  - add disable in note option to markdownlint support

- Mac Desktop App
  - [ ] menu and picker jumps/flickers in desktop app
    - maybe hide until it settles
  - [ ] missing cursor in many popups
  - [x] right click doesn't work, still works like mobile app with long press
  - [ ] double keychain access prompt with debug build of the desktop app
    - does not seem to affect the release build
  - [x] preview gets weird with the font after a while, requires restarting app

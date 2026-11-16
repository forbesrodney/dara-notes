# TODO

## Future Ideas/Work (v1.1+) - not all will become reality

- Linux
  - [ ] add Linux packages to appropriate package managers for Ubuntu, Debian, Fedora and RedHat
  - [ ] add Linux packages to Homebrew
- macOS
  - [ ] add macOS packages to Homebrew
- Windows
  - [ ] get Windows build properly signed
  - [ ] add Windows packages to the Microsoft Store
- General
  - [ ] ability to encrypt the repo
    - this would break compatibility with Foam
  - [ ] ability to sync with multiple repos
    - Foam is often used for notes within a project repo, it would be nice to have access to these notes to, but I don't want to clone the whole repo
  - [ ] enhance template support
    - support missing variables - $FOAM_DATE_FORMAT, $FOAM_CURRENT_DIR (not sure why), $FOAM_PREVIOUS_DAILY_NOTE (not sure how)
    - support missing VSCode style variables - ${1:placeholder} tab stops, maybe ${VAR:default} (not sure how)

## Planned Tasks (v1.1) - if checked and still here, it needs testing

- [x] Windows desktop app
- [x] Linux desktop app
 
## Planned Tasks (v1.0)

- Nothing

## Known Issues

<!-- ### Android App -->
<!-- ### iOS App -->

### Mac Desktop App

- [ ] menu and picker jumps/flickers in desktop app
  - maybe hide until it settles
- [ ] missing cursor when naming note
- [ ] double keychain access prompt with debug build of the desktop app
  - does not seem to affect the release build

<!-- ### Linux Desktop App -->
<!-- ### Window Desktop App -->

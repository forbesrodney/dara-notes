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
  - [ ] ability to encrypt the repo
    - this would break compatibility with Foam
  - [ ] ability to sync with multiple repos
    - Foam is often used for notes within a project repo, it would be nice to have access to these notes to, but I don't want to clone the whole repo
  - [ ] enhance template support
    - support missing variables - $FOAM_DATE_FORMAT, $FOAM_CURRENT_DIR (not sure why), $FOAM_PREVIOUS_DAILY_NOTE (not sure how)
    - support missing VSCode style variables - ${1:placeholder} tab stops, maybe ${VAR:default} (not sure how)
 
## Needs Testing

- [ ] Windows desktop app (msi)
- [ ] Linux desktop app (rpm, deb)

## Known Issues

- Mac Desktop App
  - [ ] menu and picker jumps/flickers in desktop app
    - maybe hide until it settles
  - [ ] missing cursor when naming note
  - [ ] double keychain access prompt with debug build of the desktop app
    - does not seem to affect the release build

# TODO

# Ideas (v1.1)

- [ ] ability to encrypt the repo
  - this would break compatibility with Foam
- [ ] ability to sync with multiple repos
  - Foam is often used for notes within a project repo, it would be nice to have access to these notes to, but I don't want to clone the whole repo
- [ ] enhance template support
  - proved a means to edit templates in doc (can likely piggyback off existing functionality)
  - support missing variables - $FOAM_DATE_FORMAT, $FOAM_CURRENT_DIR (not sure why), $FOAM_PREVIOUS_DAILY_NOTE (not sure how)
  - support missing vscode style variables - ${1:placeholder} tab stops, maybe ${VAR:default} (not sure how)
- [ ] method to add/remove front matter tags, add/remove front matter block if needed

# Tasks (v1.0)

- [ ] enhance template concept
  - make sure that all variables that Foam understands are supported here
    - Already supported - $FOAM_TITLE, $FOAM_TITLE_SAFE, $FOAM_SLUG, $FOAM_SELECTED_TEXT (always empty since notes aren't created from a selection),
      $FOAM_DATE_YEAR, $FOAM_DATE_YEAR_SHORT, $FOAM_DATE_MONTH, $FOAM_DATE_MONTH_NAME, $FOAM_DATE_MONTH_NAME_SHORT, $FOAM_DATE_DATE, $FOAM_DATA_DAY_NAME,
      $FOAM_DATE_WEEK, $FOAM_DATE_HOUR, $FOAM_DATE_MINUTE, $FOAM_DATE_SECOND, $FOAM_DATE_SECOND_UNIX
    - Not yet supported - $FOAM_DATE_DAY_ISO, $FOAM_DATE_WEEK_YEAR
  - VSCode Style Variables - CURRENT_ -style variables
  - Add a help menu item, that shows a stripped down version of the README.md

# Issues (v1.0)

Mac Desktop App

- [ ] menu and picker jumps/flickers in desktop app
  - maybe hide until it settles
- [ ] missing cursor when naming note
- [ ] double keychain access prompt with debug build of the desktop app

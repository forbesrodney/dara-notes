# Privacy Policy

Effective November 14, 2026

This policy covers Dara Notes for Android, iOS and macOS (together, "the app"), made
by Snakepit Software. In short: your notes stay on your device and in the Git repository you
choose. The app has no accounts, no analytics, no advertising and no tracking, and the
developer never receives your notes or your settings.

## What the app stores, and where

Everything the app stores is kept on your device, in the app's own private storage:

- **Your notes:** a copy (clone) of the Git repository you set up, or a local-only repository
  if you don't connect one.
- **Your settings:** the repository URL, branch, username, commit author name and email, and
  your preferences in the app.
- **Spelling dictionaries** you download, if you switch on spell checking.
- **Your access token**, if you enter one: kept in your system's secure storage (Android's
  encrypted preferences, the iOS Keychain or the macOS Keychain), and used only to sign in to
  your Git host.

On Android, the app's data is excluded from system backups. Uninstalling the app deletes all of
it from the device.

## What the app sends, and to whom

- **Your Git host.** When the app syncs, it connects over HTTPS to the Git host in your
  repository URL (for example, GitHub) to download changes and upload yours. It sends your
  username and access token to sign in, and your notes and commits (which include the commit
  author name and email you set). What happens to that data there is covered by your Git
  host's own privacy policy.
- **Images from the web in your notes.** If a note includes an image by its web address, the
  preview downloads that image from the site hosting it, as any web page or Markdown viewer
  would. That site can see the request, like any other visit, including your IP address.
- **Links you open.** Web links in your notes open in your browser, and only when you tap
  them.
- **Spelling dictionaries.** If you switch on spell checking, the list of languages and the
  dictionaries you choose are downloaded from the app's GitHub repository
  (github.com/forbesrodney/dara-notes). These are plain file downloads: nothing about you or
  your notes is sent, though GitHub can see the request, like any other visit, including your
  IP address. Spell checking itself happens on your device.

The app sends nothing else. It contains no analytics, advertising, crash reporting or other
third-party services, and the developer receives no data from it.

## Permissions

On Android, the app asks only for internet access and to check whether a network connection is
available (to sync). On iOS, it uses background app refresh to sync, if your settings allow it.

## Children

The app doesn't collect personal information from anyone, including children.

## Your choices

You control what the app has: you can change or remove your token and settings in the app,
delete notes, disconnect the repository, or uninstall the app. Data in your Git repository is
managed through your Git host.

## Changes

If this policy changes, the updated version will be posted here with a new effective date.

## Contact

Questions about this policy: [support@snakepitsoftware.com](mailto:support@snakepitsoftware.com)

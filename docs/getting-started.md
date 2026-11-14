# Getting started

## Connecting a repository

Your notes live in a Git repository, such as one on GitHub. The app keeps a copy (a clone)
on your device and syncs it with the repository.

1. Create a personal access token with read and write access to the repository. On GitHub,
   use a fine-grained token with *Contents: Read and write*.
2. Open the app. In Settings > *Repo*, enter the repository's HTTPS clone URL (for example
   `https://github.com/you/notes.git`), the branch, your username and the token.
3. Tap *Clone repository*.

To keep notes on this device only, leave the URL blank and tap *Create local repository*.
You can connect it to a repository later.

*Commit author name* and *Commit author email* are what your commits are signed with.
*Discard local copy and re-clone* deletes the copy on this device and clones it again;
changes that haven't been pushed yet are lost.

## How syncing works

A sync commits your changes, fetches changes made elsewhere, merges them and pushes the
result. The app syncs:

- when it opens,
- about 30 seconds after you stop editing (Settings > *Sync* > *Sync shortly after editing*),
- when you pull down on the note list, or choose *Sync now* in its menu (on the desktop,
  the sync button in the toolbar),
- in the background, every so often (Settings > *Sync* > *Background sync every*).

If you and another device changed the same lines, nothing is lost: see
[Merge conflicts](troubleshooting.md).

### On Android

Background sync runs at most every 15 minutes, which is Android's minimum.

### On iPhone and iPad

*Background sync every* asks iOS to sync no sooner than that; iOS decides when it actually
runs, usually while the phone is idle and online. The app also syncs when it goes to the
background if edits are waiting to be pushed (iOS allows about 30 seconds for that).

### On the desktop

*Background sync every* runs while the app is open, with no minimum. The app also syncs
when its window comes to the front, and before it quits if edits are waiting to be pushed:
the window closes first, and the app gives up after 20 seconds.

In the desktop app, Cmd+N (Ctrl+N on Windows and Linux) starts a new note and Cmd+D
(Ctrl+D) opens today's note, from anywhere. The *Today* and *New note* buttons are in the
toolbar.

## Where things are kept

- **Notes:** in the app's private storage. On a Mac, that's
  `~/Library/Application Support/Dara Notes/repo` (Windows: `%APPDATA%\Dara Notes\repo`;
  Linux: `~/.local/share/dara-notes/repo`).
- **Settings:** in the app's preferences on the device.
- **Access token:** in the system's secure storage: Android's encrypted preferences, the
  iOS Keychain or the macOS Keychain (Windows Credential Manager, or the Secret Service on
  Linux).

See the [privacy policy](../PRIVACY.md) for more.

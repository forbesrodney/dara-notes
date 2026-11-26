# Getting started

## Connecting a repository

Your notes live in a Git repository, such as one on GitHub. The app keeps a copy (a clone)
on your device and syncs it with the repository. New to GitHub, or need a repository and a
token? See [Setting up GitHub](#setting-up-github) first.

1. Create a personal access token with read and write access to the repository. On GitHub,
   use a fine-grained token with *Contents: Read and write*.
2. Open the app. In Settings > *Repo*, enter the repository's HTTPS clone URL (for example
   `https://github.com/you/notes.git`) under *Remote URL*, the *Branch* (`main` if you leave
   it blank), your *Username* and the *Access token*.
3. Tap *Clone repository*. Your other devices connect the same way, with the same URL.

To keep notes on this device only, leave the URL blank and tap *Create local repository*.
You can connect it to a repository later.

*Commit author name* and *Commit author email* are what your commits are signed with.
*Discard local copy and re-clone* deletes the copy on this device and clones it again;
changes that haven't been pushed yet are lost.

## Setting up GitHub

GitHub is free for private repositories, and most people use it, so these steps use it.
GitLab, Bitbucket, Codeberg and other Git hosts work too; the steps are much the same, but
the menus differ. If you already have a repository, such as a Foam workspace, skip to
[Create an access token](#create-an-access-token).

### Create an account

1. Go to [github.com/signup](https://github.com/signup) and follow the prompts. You'll
   choose a username, which becomes part of your repository's address.
2. Verify your email address when GitHub asks.
3. Turn on two-factor authentication (your profile picture > *Settings* > *Password and
   authentication*). Your notes are only as safe as the account.

### Create a private repository

A private repository is one only you can see; that's what you want for personal notes.

1. On GitHub, click the *+* at the top right, then *New repository*.
2. Give it a name, such as `notes`.
3. Choose *Private*.
4. Turn on the option to add a README, so the repository starts with a `main` branch.
5. Click *Create repository*.

Its address is `https://github.com/` followed by your username, the name and `.git`: for
example `https://github.com/you/notes.git`. That's the *Remote URL* in the app.

### Create an access token

The app signs in to GitHub with a token instead of your password. A fine-grained token can
be limited to the one repository, so it can't touch anything else in your account.

1. Click your profile picture, then *Settings*.
2. At the bottom of the sidebar, click *Developer settings*, then *Personal access tokens*
   > *Fine-grained tokens*.
3. Click *Generate new token*.
4. Give it a name, such as `Dara Notes`, and pick an expiration.
5. Under *Repository access*, choose *Only select repositories* and pick your notes
   repository.
6. Under *Permissions*, give *Contents* *Read and write* access.
7. Click *Generate token*, and copy the token. GitHub shows it only once.
8. Paste it into the app's *Access token* field, along with your GitHub username.

Use the same token on each of your devices, or make one per device so you can revoke one
without affecting the others. When a token expires, syncing fails until you make a new one
and enter it in Settings > *Repo*. A longer expiration means fewer renewals, but more time
for a lost token to be misused.

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

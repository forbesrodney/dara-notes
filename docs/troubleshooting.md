# Troubleshooting

## Merge conflicts

When this device and another changed the same lines of a note between syncs, Git can't
combine them by itself. Nothing is lost: the app commits the note with both versions in
it, between Git's conflict markers, and names it at the top of the note list under
*Conflicts to resolve*.

```text
<<<<<<< (this device)
The line as this device has it.
=======
The line as the other device has it.
>>>>>>> (the other device)
```

Open the note, keep what you want, and delete the rest along with the three marker lines.
The next sync pushes the result.

To make conflicts rarer, let the app sync before you switch devices: open it, or pull down
on the note list.

## Sync fails

The error is shown at the top of the note list, often with a hint, and in Settings > *Repo*
as *Last error*. Common causes:

- **The token expired or lacks access.** Fine-grained GitHub tokens expire; create a new
  one with *Contents: Read and write* for the repository, and enter it in Settings.
- **The URL or branch is wrong.** Use the HTTPS clone URL (not the SSH one, which starts
  with `git@`) and check the branch name, often `main`.
- **No connection.** Changes stay committed on the device and are pushed at the next sync.

## A repository setting doesn't seem to apply

The app only sees what's in the repository, so commit and push `.vscode/settings.json`
and your markdownlint configuration, then sync in the app.

**Check that Git isn't ignoring `.vscode`.** A global gitignore that excludes `.vscode/`
hides `settings.json` from Git. To check, run this in the notes repository; no output means
it isn't ignored:

```bash
git check-ignore -v .vscode/settings.json
```

If it is ignored, add these lines to the repository's `.gitignore`, then commit and push the
file. Git can't include a file inside an ignored folder, so the folder is included first and
its other files ignored again:

```gitignore
!/.vscode/
/.vscode/*
!/.vscode/settings.json
```

**`.code-workspace` settings aren't read.** If you open your notes in VS Code through a
`.code-workspace` file, VS Code also uses the `"settings"` in it, but the app doesn't. Put
settings the app should follow in `.vscode/settings.json`.

## Background sync on iPhone seems slow

iOS decides when background work runs, usually while the phone is idle and online,
so *Background sync every* is a minimum, not a schedule. The app always syncs when it opens.

## The Mac asks to use the Keychain

The desktop app keeps your access token in the macOS Keychain. After you install or update
the app, macOS may ask once whether Dara Notes may use it. Choose *Always Allow* so it
doesn't ask again.

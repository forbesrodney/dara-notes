# Writing notes

## The editor

Notes are plain Markdown, saved automatically as you type. New to Markdown? See
[Markdown basics](markdown.md). Above the keyboard:

- *All*, *Cut*, *Copy* and *Paste*. With nothing selected, *Cut* and *Copy* take the
  current line.
- A formatting toolbar: heading, bold, italic, checkbox, list, wikilink, superscript,
  subscript, math, footnote, Mermaid diagram, code and link.

Typing `[[` suggests notes to link to. Pressing Enter in a list starts the next item of
the same kind (a bullet, the next number, or an unchecked checkbox); Enter on an empty
item ends the list.

Settings > *Notes* > *List bullet* picks `-` (the default) or `*` for the bullets the app
writes. The list and checkbox buttons recognize items written with either.

### Keyboard shortcuts

With a hardware keyboard:

| Shortcut | Does |
| --- | --- |
| Cmd+N or Ctrl+N | New note, from any screen |
| Cmd+D or Ctrl+D | Today's note, from any screen |
| Cmd+[ or Alt+Left, or Esc | Back, like the back arrow |
| Cmd+B or Ctrl+B | Bold |
| Cmd+I or Ctrl+I | Italic |
| Cmd+Shift+X or Ctrl+Shift+X | Strike-through |
| Cmd+Enter or Ctrl+Enter | Check or uncheck the task on the current line |
| Tab | Indent the selected lines, or the list item the cursor is on; elsewhere, type a tab |
| Shift+Tab | Outdent the selected lines, or the line the cursor is on |

Bold, italic and strike-through toggle, and with nothing selected apply to the word at the
cursor. Tab nests a list item under the one above it: 2 spaces under a bullet (`-` and a
space), 3 under a numbered item (`1.` and a space). Other selected lines move 4 spaces. markdownlint flags tab
characters by default (rule MD010); turn that rule off in `.markdownlint.json` if you use
them. On Android, use Ctrl: the system may claim some Cmd shortcuts.

Cmd+N and Cmd+D leave the note you're in (it's already saved), and Back from the note they
open goes to the note list. Cancel the New note dialog and you stay where you were. With
unsaved changes in Settings, the app asks first, as it does when you go back.

Back is Cmd+[ on a Mac or iPad and Alt+Left on Windows and Linux, as in web browsers. Esc
does the same everywhere, and so does a mouse's back button on the desktop. With a menu or
dialog open, Back closes it first. Editing a note and reading its preview are the same
screen, so Back from either goes where the back arrow does: the screen before the note,
usually the note list.

On an iPad, hold Cmd to see the shortcuts listed; on Android, Meta+/.

## The preview

The preview shows the note as Foam and VS Code would, and works offline:

- Wikilinks: `[[note]]`, `[[note|alias]]`, `[[note#heading]]`, `[[folder/note]]`. A link
  to a note that doesn't exist yet is marked; tap it to create the note.
- Embeds: `![[note]]` and `![[image.png]]`.
- Relative links and images: `[text](other.md)`, `![alt](images/photo.jpg)`.
- Mermaid diagrams in ` ```mermaid ` blocks.
- Footnotes `[^1]` and inline footnotes `^[text]`.
- Math with KaTeX: `$inline$`, `$$display$$`, `\( \)` and `\[ \]`.
- Superscript `x^2^` and subscript `H~2~O`.
- Checkboxes: tap one to check or uncheck it.

Every heading can be linked to with `#id`, GitHub-style: lowercase, punctuation removed,
spaces as hyphens. So `## Snippet Syntax` is `#snippet-syntax`.

## Tags

A note can be tagged two ways, as in Foam: in its front matter, or with `#tags` in the text.

*Tags*, in a note's menu, lists the note's front matter tags. Tap a tag to remove it, or type
a new one (or tap one of the tags already in your notes) to add it. You don't need to know
the front matter format: the app adds the front matter block at the top of the note when it
needs one, and removes it again when the last tag goes and nothing else is in it.

```markdown
---
tags: [idea, dev/git]
---
```

Tags use letters, numbers, `-`, `_` and `/`, with no spaces; `/` nests one tag under another,
so `dev/git` sits under `dev`. If a note already lists its tags another way (`tags: a, b`, or
one per line under `tags:`), new tags are written the same way. `#tags` in the text are shown
in the dialog too, but you edit those in the note itself.

## Spelling

Settings > *Editing* > *Check spelling* (off by default) checks spelling as you edit, like
the Code Spell Checker extension (cSpell) in VS Code, and follows the same settings and
comments, so a word VS Code accepts passes here too.

### Languages

Spelling needs a dictionary. When you turn on *Check spelling* and save, Dara downloads one
for your device's language (English (US) if it doesn't offer yours); if that fails, say
offline, it tries again the next time you open a note. To choose others, under *Check
spelling*, tap *Languages...* (*Download a language...* before you have one) and pick one or
more. Each is downloaded once (English is about 0.3 MB; Spanish
and French about 2 MB) and kept in the app, not in your notes, so each device needs its own.
Software terms and company names, from cSpell, come with the first language, so words such
as GitHub pass. When a dictionary is updated, *Update* appears beside it.

### Fixing a word

Misspelled words are underlined, and the *spelling issues* chip counts them (tap it for the
list, and tap a word to go to it). With the cursor in an underlined word, a bar above the
note offers corrections; tap one to use it. Its menu (the three dots at its end) offers:

- *Ignore in this note*, which adds the word to a comment at the top of the note, as
  `<!-- cspell:ignore word -->`, for VS Code to read too.
- *Add to the repository's dictionary*, which adds it to `cspell.json` or, without one, to
  `cSpell.words` in `.vscode/settings.json`. It syncs with the rest of the repository.

For a note with too many words that aren't really words, such as a technical one, tap
*Disable in current note* in the spelling issues list. It puts `<!-- cspell:disable -->`
at the top of the note (below any front matter), so VS Code skips the note too. Delete that
line to check the note again.

### What's checked

As in cSpell: letter case and accents don't matter, words shorter than four letters aren't
checked, words with digits or underscores aren't either, and `camelCase` is checked part by
part, unless the whole word is ignored or added. A word you ignore or add covers its
possessive and contractions too (`NameCheap's`, `Dara'll`). Code, URLs, e-mail addresses, link and wikilink targets, front matter, HTML tags,
math, `#tags` and template variables are skipped. So are notes matched by `ignorePaths`.

These comments, anywhere in a note, work as in VS Code:

| Comment | Does |
| --- | --- |
| `<!-- cspell:disable -->` | Stop checking, to the end or a `cspell:enable` |
| `<!-- cspell:enable -->` | Start checking again |
| `<!-- cspell:disable-next-line -->` | Skip the next line |
| `<!-- cspell:disable-line -->` | Skip this line |
| `<!-- cspell:ignore word other -->` | Accept these words in this note (`cspell:words` too) |
| `<!-- cspell:flagWords word -->` | Always flag these in this note |

For the repository-wide settings, see [Foam and repository settings](foam-settings.md#cspell-settings).

## Checklists

Tap a checkbox in the preview, or use the checkbox button in the editor, which cycles a
line from plain text to `- [ ]` to `- [x]` and back to `- [ ]`.

Two optional settings, in Settings > *Editing* > *Checklists*:

- *Strike through checked items* writes `~~text~~`.
- *Add completion date to checked items* appends the date in brackets, as in
  `- [x] ~~Buy milk~~ [2026-11-08]`. Pick the date format and separator below it; both
  start out matching your device's language and region.

Unchecking an item removes the strike-through and the date again.

## Daily notes

The *Today* button opens today's note, creating it if needed. By default it's
`journal/yyyy-mm-dd.md`; Settings > *Notes* > *Daily notes folder* changes the folder.
A [daily-note template](templates.md) can set both the folder and the file name, and what
a new daily note starts with.

## Table of contents

*Add table of contents*, in a note's menu while you're editing it, puts a list of links
to the note's sections (`##` to `######` headings, nested by level) under its title:

```markdown
- [Snippet Syntax](#snippet-syntax)
  - [Tab Stops](#tab-stops)
```

After that, the menu item reads *Update table of contents*, and the list updates itself
when you switch to the preview or leave the note (never while you type). A heading with
`<!-- omit from toc -->` at its end, or on the line above, is left out. Tables of contents
made elsewhere, such as in VS Code, work too.

## Renaming and moving notes

Long-press a note in the list (right-click with a mouse), or use the editor's menu. Every link
to the note is fixed: wikilinks (with aliases, headings and embeds), relative Markdown links
and Foam's link reference definitions, and the moved note's own relative links. If the new
name clashes with another note's, links get the folder too, so each still points at the
right note. Links inside code are left alone.

## Exporting and sharing

From a note's menu:

- **On a phone:** *Share Markdown file*, *Share as PDF* and *Share as text* open the share
  sheet (Mail, Gmail, Drive, Files, Messages and so on).
- **On the desktop:** *Save Markdown file...*, *Print or save as PDF...*, *Email as text*
  and *Copy as text*. On a Mac, the Print dialog's PDF menu saves the PDF or sends it in
  Mail.

PDFs are laid out for paper (Letter in the US and nearby, A4 elsewhere), with math,
diagrams and images. Links that can't work outside the app, such as wikilinks and a table
of contents, become plain text; web links stay. Shared text leaves out the front matter and
Foam's link definitions.

## Checking Markdown with markdownlint

Settings > *Editing* > *Check Markdown with markdownlint* (off by default) checks the note
as you edit, using your repository's markdownlint configuration, like the VS Code
extension. Problems are highlighted; the *issues* chip lists them (tap one to jump to it)
and offers *Fix all* for the rules markdownlint can fix. See
[Foam and repository settings](foam-settings.md) for where the configuration comes from.

As with spelling, you can turn a rule off rather than fix it. Each issue's menu (the three
dots at its end) offers:

- *Ignore MD013 in this note* (with the issue's rule), which adds the rule to a comment at
  the top of the note, below any front matter: `<!-- markdownlint-disable-file MD013 -->`.
  Ignoring another rule adds it to the same comment.
- *Turn off MD013 in .markdownlint.json* (or whichever config file the repository has),
  which adds `"MD013": false` there, or changes the rule's entry if it has one. Without a
  config file, the app creates `.markdownlint.json`. It syncs with the rest of the
  repository. In a YAML config, a rule with settings of its own is left for you to edit.

*Disable in current note*, in the issues list, puts `<!-- markdownlint-disable-file -->` at
the top instead, so markdownlint skips the note. Delete that line to check the note again.
These are markdownlint's own comments, so VS Code follows them too, and any you type
yourself work the same way.

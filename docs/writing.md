# Writing notes

## The editor

Notes are plain Markdown, saved automatically as you type. Above the keyboard:

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
| Cmd+B or Ctrl+B | Bold |
| Cmd+I or Ctrl+I | Italic |
| Cmd+Shift+X or Ctrl+Shift+X | Strike-through |
| Cmd+Enter or Ctrl+Enter | Check or uncheck the task on the current line |

Bold, italic and strike-through toggle, and with nothing selected apply to the word at the
cursor. On Android, use Ctrl: the system may claim some Cmd shortcuts.

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

*Add table of contents*, in a note's menu, puts a list of links to the note's sections
(`##` to `######` headings, nested by level) under its title:

```markdown
- [Snippet Syntax](#snippet-syntax)
  - [Tab Stops](#tab-stops)
```

After that, the menu item reads *Update table of contents*, and the list updates itself
when you switch to the preview or leave the note (never while you type). A heading with
`<!-- omit from toc -->` at its end, or on the line above, is left out. Tables of contents
made elsewhere, such as in VS Code, work too.

## Renaming and moving notes

Long-press a note in the list, or use the editor's menu. Every link to the note is fixed:
wikilinks (with aliases, headings and embeds), relative Markdown links and Foam's link
reference definitions, and the moved note's own relative links. If the new name clashes
with another note's, links get the folder too, so each still points at the right note.
Links inside code are left alone.

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

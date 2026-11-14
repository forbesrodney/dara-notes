# Templates

Templates are Markdown files in your repository's `.foam/templates` folder, the same ones
Foam uses. The app fills in their variables (such as `$FOAM_TITLE` or `$FOAM_DATE_YEAR`)
when it makes a note from one. You can write and edit them in the app or in VS Code.

## The two default templates

As in Foam, two templates are used automatically when they exist:

- **`.foam/templates/new-note.md`** for every new note: from the *New note* button, from
  following a link to a note that doesn't exist yet, and for a new index note.
- **`.foam/templates/daily-note.md`** for daily notes, from the *Today* button.

Without them, a new note starts with its title as a heading (`# Shopping List`), and a
daily note with its date (`# 2026-11-13`).

## Editing templates in the app

*Templates*, in the note list's menu, lists your templates:

- **Used automatically:** `new-note.md` and `daily-note.md`. If one doesn't exist yet,
  *Create* makes it, starting out the same as a note without it, so nothing changes until
  you edit it.
- **For Create note from template:** the others. *New template* asks for a name and starts
  one for you. The menu button beside a template renames its file or deletes it.

Tap a template to open it. While editing, the *$ Variable* button in the toolbar lists every
variable with what it becomes, and inserts the one you tap at the cursor.

The preview shows an example: a note made from the template right now, titled "Example
Note" (a daily note is today's), with every variable filled in. The bar above it shows where
that note would be saved, so you can check a `filepath` too. Links in the example don't
open, and its checkboxes can't be checked.

Templates sync like notes. They never show up in the note list, the graph or search.

## Creating a note from another template

*Create note from template*, in the note list's menu, lists the other templates in
`.foam/templates` and its subfolders, by their name and description, and makes a note from
the one you pick. It's grayed out when there are no other templates. The two default
templates aren't listed, because Foam already uses them by itself. *Edit templates*, at the
bottom of the list, opens the Templates screen.

If the template uses the title (or doesn't set a file name), the app asks for one. A
template whose `filepath` doesn't involve the title, such as one named by the date, needs
no title: picking it opens that note, creating it first if it doesn't exist yet.

## Template settings

A template can start with a `foam_template` block in its front matter. The block is
removed from the note; any other front matter (such as `tags`) is kept.

```markdown
---
foam_template:
  name: Meeting notes
  description: Agenda, attendees and actions
  filepath: meetings/$FOAM_DATE_YEAR-$FOAM_DATE_MONTH-$FOAM_DATE_DATE-$FOAM_SLUG.md
tags: meeting
---

# $FOAM_TITLE

## Agenda
```

| Setting | What it does |
| --- | --- |
| `name` | The name shown in *Create note from template*. Without it, the file name is shown. |
| `description` | A line shown under the name. |
| `filepath` | Where the note goes, relative to the repository root. It can use the variables below. A leading `/`, or `/` and your repository's name, also means the repository root. |

Without a `filepath`, a note goes in Settings > *Notes* > *Folder for new notes* (the
repository root if blank), named after its title, such as `shopping-list.md`. A daily note
goes in Settings > *Notes* > *Daily notes folder* (`journal` if blank), named by its date.

## Variables

Write a variable as `$NAME` or `${NAME}`. `${NAME:text}` uses `text` when the variable has
no value here. Each variable is filled in once, so a title that happens to contain `$` is
kept as typed.

### Title and file

The examples are for a note titled "Shopping List: Week 2".

| Variable | Becomes |
| --- | --- |
| `$FOAM_TITLE` | The title you enter: `Shopping List: Week 2` |
| `$FOAM_TITLE_SAFE` | The title, safe as a file name: `Shopping List- Week 2` |
| `$FOAM_SLUG` | The title for a file name or link: `shopping-list-week-2` |
| `$TM_FILENAME_BASE` | The note's file name without `.md` |
| `$FOAM_SELECTED_TEXT` | Nothing: notes aren't made from selected text here |

`$FOAM_TITLE_SAFE` replaces each character that file names can't have with `-`. Those are
`\ / : * ? " < > | # ^ [ ]`. `$FOAM_SLUG` is in lowercase, without punctuation, with hyphens
for spaces. `$TM_FILENAME_BASE` can't be used in `filepath`, since `filepath` is what
decides the file name.

### The note's date

For a daily note, these are the day it's for; otherwise, the moment the note is made. Names
of months and days follow your device's language.

| Variable | Example |
| --- | --- |
| `$FOAM_DATE_YEAR` | `2026` |
| `$FOAM_DATE_YEAR_SHORT` | `26` |
| `$FOAM_DATE_MONTH` | `11` |
| `$FOAM_DATE_MONTH_NAME` | `November` |
| `$FOAM_DATE_MONTH_NAME_SHORT` | `Nov` |
| `$FOAM_DATE_DATE` | `13` (the day of the month) |
| `$FOAM_DATE_DAY_NAME` | `Friday` |
| `$FOAM_DATE_DAY_NAME_SHORT` | `Fri` |
| `$FOAM_DATE_DAY_ISO` | `5` (Monday is 1, Sunday is 7) |
| `$FOAM_DATE_WEEK` | `46` (the ISO week number) |
| `$FOAM_DATE_WEEK_YEAR` | `2026` (the year that week belongs to; see below) |
| `$FOAM_DATE_HOUR` | `14` (24-hour clock) |
| `$FOAM_DATE_MINUTE` | `05` |
| `$FOAM_DATE_SECOND` | `09` |
| `$FOAM_DATE_SECONDS_UNIX` | `1794596709` (seconds since 1970) |

ISO weeks start on Monday, and week 1 is the one with the year's first Thursday. So the
first days of January can belong to the previous year's last week: January 1, 2027 is in
week 53 of 2026. Use `$FOAM_DATE_WEEK_YEAR` with `$FOAM_DATE_WEEK` to name weekly notes,
as in `$FOAM_DATE_WEEK_YEAR-W$FOAM_DATE_WEEK`, which gives `2026-W53`.

### The current time (VS Code's variables)

VS Code's own snippet variables work too. They're always the moment the note is made, even
for a daily note for another day.

| Variable | Example |
| --- | --- |
| `$CURRENT_YEAR` | `2026` |
| `$CURRENT_YEAR_SHORT` | `26` |
| `$CURRENT_MONTH` | `11` |
| `$CURRENT_MONTH_NAME` | `November` |
| `$CURRENT_MONTH_NAME_SHORT` | `Nov` |
| `$CURRENT_DATE` | `13` |
| `$CURRENT_DAY_NAME` | `Friday` |
| `$CURRENT_DAY_NAME_SHORT` | `Fri` |
| `$CURRENT_HOUR` | `14` |
| `$CURRENT_MINUTE` | `05` |
| `$CURRENT_SECOND` | `09` |
| `$CURRENT_SECONDS_UNIX` | `1794596709` |
| `$CURRENT_TIMEZONE_OFFSET` | `-05:00` |

### Random values

Each of these gets a new value every time it appears.

| Variable | Example |
| --- | --- |
| `$UUID` | `3f2b8c1e-9d4a-4e7b-a1c2-5d6e7f809a1b` |
| `$RANDOM` | `482915` (six digits) |
| `$RANDOM_HEX` | `a3f09c` (six hexadecimal digits) |

### Tab stops

VS Code's tab stops have no meaning outside VS Code, so `${1:text}` becomes `text`, and
`$1`, `${1}` and `$0` are removed. A template written for VS Code still makes a clean note.

### Not supported

- `$FOAM_DATE_FORMAT`, `$FOAM_CURRENT_DIR` and `$FOAM_PREVIOUS_DAILY_NOTE`.
- Foam's JavaScript templates (`.js` files in `.foam/templates`).

## Examples

A daily note in a folder per month, with a heading like *Friday, November 13*:

```markdown
---
foam_template:
  filepath: journal/$FOAM_DATE_YEAR/$FOAM_DATE_MONTH/$FOAM_DATE_YEAR-$FOAM_DATE_MONTH-$FOAM_DATE_DATE.md
---

# $FOAM_DATE_DAY_NAME, $FOAM_DATE_MONTH_NAME $FOAM_DATE_DATE

## Tasks

- [ ] 
```

A weekly review, picked from *Create note from template*. Its file name doesn't use the
title, so the app opens this week's review instead of asking for a title:

```markdown
---
foam_template:
  name: Weekly review
  description: One note per ISO week
  filepath: reviews/$FOAM_DATE_WEEK_YEAR-W$FOAM_DATE_WEEK.md
---

# Week $FOAM_DATE_WEEK, $FOAM_DATE_WEEK_YEAR
```

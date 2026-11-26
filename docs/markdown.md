# Markdown basics

Dara Notes saves notes as Markdown: plain text with a few symbols for formatting. The
symbols stay in the text, so a note reads fine anywhere, and the preview shows it formatted.
You don't need to learn it all at once; the formatting toolbar above the keyboard writes
most of these for you.

## Paragraphs and line breaks

Leave a blank line between paragraphs. A single line break inside a paragraph is ignored
in the preview, and the lines run together:

```markdown
These two lines
show as one line.

This is a new paragraph.
```

To break a line without starting a paragraph, end it with a backslash (`\`) or two spaces.

## Headings

Start a line with `#` and a space. More `#`s make smaller headings, down to six:

```markdown
# Note title
## Section
### Smaller section
```

## Bold, italic and strike-through

```markdown
**bold**, *italic*, ***both*** and ~~struck through~~
```

`__bold__` and `_italic_` work too. With a hardware keyboard, Cmd+B and Cmd+I (Ctrl on
Android, Windows and Linux) add them.

## Lists

Start each item with `-` (or `*`) and a space for bullets, or a number and a period for a
numbered list. Indent an item (Tab, or 2 spaces) to nest it under the one above:

```markdown
- Milk
- Bread
  - whole wheat
  - rye

1. Preheat the oven
2. Mix the batter
3. Bake for 30 minutes
```

The numbers don't have to be in order: the preview counts from the first one, so you can
write `1.` on every line. Pressing Enter at the end of an item starts the next one, and
Enter on an empty item ends the list.

## Checklists

A bullet with `[ ]` is a task; `[x]` checks it off. Tap the box in the preview to check
it. See [Checklists](writing.md#checklists) for the options.

```markdown
- [ ] Call the plumber
- [x] Pay the water bill
```

## Links

Link to another note by its name in double brackets. Typing `[[` suggests notes:

```markdown
See [[Meeting notes]] and [[Recipes|my recipes]].
```

The part after `|` is the text shown. A link to a note that doesn't exist yet is marked in
the preview; tap it to create the note.

For web pages, put the text in brackets and the address in parentheses. A bare address
becomes a link by itself:

```markdown
[Dara Notes](https://daranotes.app) or https://daranotes.app
```

## Images

Like a link, with `!` in front. The image file goes in your repository:

```markdown
![A red barn](images/barn.jpg)
```

## Quotes

Start a line with `>`:

```markdown
> The best time to plant a tree was twenty years ago.
```

## Code

Wrap a word or command in backticks, `` `like this` ``. For several lines, put three
backticks on the lines before and after; Markdown symbols inside code are shown as typed.

````markdown
```
git pull
git push
```
````

## Dividers

A line with only `---` draws a horizontal line. Leave a blank line above it: right under
text, `---` turns that text into a heading instead.

## Tables

```markdown
| Day | Miles |
| --- | --- |
| Mon | 3 |
| Wed | 5 |
```

The columns don't have to line up in the text.

## Showing a symbol as itself

Put a backslash before it: `\*not italic\*` shows the asterisks, and `\#` at the start of a
line isn't a heading.

## Learning more

- [Markdown Guide](https://www.markdownguide.org/basic-syntax/): every basic feature, with
  examples.
- [CommonMark tutorial](https://commonmark.org/help/tutorial/): a ten-minute interactive
  tutorial.
- [Writing notes](writing.md#the-preview): what else the preview shows, such as math,
  diagrams, footnotes and embedded notes.

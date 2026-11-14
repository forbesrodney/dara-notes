# Finding and organizing notes

## The note list

The button beside the search box switches between two views, and the app remembers your
choice:

- **Flat:** every note, newest first, like Foam's flat view.
- **By folder:** a folder tree, like Foam's hierarchical view, with a count of notes in
  each folder. Tap a folder to open or close it. While you search, folders with matches
  open by themselves.

## The index note

Foam gives no file a special role: notes are tied together by the links you write. The app
can keep one note as a home page, though. Settings > *Notes* > *Index note* (`Index.md` by
default) names the note the Home button opens.

To have the app maintain it, switch on *Link new notes from the index note*. Then every new
note, whether from the *New note* button or from following a link to a note that doesn't
exist yet, gets `- [[note]]` added at the end of the index's list of links. If the index
has Foam's link reference definitions at the bottom, the entry goes above them and a
matching definition is added. Deleting a note removes both.

- *Add daily notes to the index* includes daily notes, which are left out otherwise.
- *Add missing notes to Index.md* links every note that isn't linked yet, for a repository
  you already had.
- If the index note doesn't exist yet, the app creates it the way it creates a new note
  (from your [new-note template](templates.md), if you have one).

With linking off, the app never edits the index.

## Graph

The graph button on the note list, or *Show in graph* in a note's menu, opens Foam's
springy graph: notes, tags (in front matter and inline `#tags`) and missing notes, linked
by wikilinks, embeds and relative Markdown links.

Drag a node (it springs back), pinch to zoom, and tap a node to highlight its neighbors and
open it, or to create a missing note. Filters show or hide tags and missing notes. The
layout is kept while the app runs.

## Connections

A note's menu > *Connections* works like Foam's Connections panel:

- **Links out:** every link in the note (wikilinks, embeds, Markdown links and images, web
  addresses), with its line and what it points to: a note and heading, a file, a web
  address, a heading in the same note, or nothing. A missing note has a *Create* button.
- **Links in:** the notes that link here, with the line.

Tap an entry to open it. Links in code and link reference definitions are skipped.

## Tags

The note list's menu > *Tags* works like Foam's Tag Explorer: every tag, from front matter
and inline `#tags`, as a tree, so `dev/git` sits under `dev`, with a count of notes for
each. Tap a tag to see its notes, and tap a note to open it. The filter box narrows the
list. In the graph, selecting a tag offers *Notes* to jump here. To tag a note, use *Tags*
in its menu (see [Writing notes](writing.md)).

## Orphaned notes

The note list's menu > *Orphaned notes* works like Foam's Orphans panel: the notes with no
links to or from another note (tags don't count). Tap one to open it, or *Add to index*
(when index linking is on) to link it from the index note. Foam's `foam.orphans.exclude`
setting is respected; see [Foam and repository settings](foam-settings.md).

## Placeholders

The note list's menu > *Placeholders* works like Foam's Placeholders panel: the links to
notes that don't exist yet, each with the notes that link to it. *Create* makes the note;
tap a linking note to open it. Foam's `foam.placeholders.exclude` setting is respected.

# Foam and repository settings

Some behavior is set in the notes repository itself, in the same files Foam and the VS Code
markdownlint and Code Spell Checker extensions use, so the app and VS Code follow the same
rules.

## `.vscode/settings.json`

The app reads these settings from `.vscode/settings.json` at the root of the repository.
Other settings are ignored.

| Setting | Used for | Default |
| --- | --- | --- |
| `foam.orphans.exclude` | Patterns for notes left out of *Orphaned notes* | none |
| `foam.placeholders.exclude` | Patterns for notes whose links are left out of *Placeholders* | none |
| `markdownlint.config` | markdownlint rules, when the repository has no markdownlint configuration file | none |
| `cSpell.*` | Spelling (see [cSpell settings](#cspell-settings)) | none |

```jsonc
{
  // Daily notes aren't expected to be linked
  "foam.orphans.exclude": ["journal/**"],
  // Drafts link to notes that haven't been written yet
  "foam.placeholders.exclude": ["drafts/**"]
}
```

Patterns are relative to the repository root: `*` matches within one folder, `**` across
folders, `?` one character, and `{a,b}` either name. A plain folder name such as `journal`
also covers everything inside it. Comments and trailing commas are allowed, as in VS Code.

## Templates

`.foam/templates` holds your templates. See [Templates](templates.md).

## markdownlint configuration

When markdownlint is switched on (Settings > *Editing*), the app uses the first of these
it finds at the repository root:

1. `.markdownlint-cli2.jsonc`
2. `.markdownlint-cli2.yaml`
3. `.markdownlint.jsonc`
4. `.markdownlint.json`
5. `.markdownlint.yaml`
6. `.markdownlint.yml`
7. `markdownlint.config` in `.vscode/settings.json`

## cSpell settings

When spelling is switched on (see [Writing notes](writing.md#spelling)), the app reads these
from `cspell.json` at the repository root and from `.vscode/settings.json` (with the
`cSpell.` prefix there), and combines the two:

| Setting | Used for |
| --- | --- |
| `words`, `userWords`, `ignoreWords` | Words accepted everywhere |
| `flagWords` | Words always flagged; `"hte->the"` also suggests the fix |
| `ignorePaths` | Patterns for notes not checked; a pattern without a `/`, such as `*.draft.md`, matches in any folder |

```jsonc
// .vscode/settings.json
{
  "cSpell.words": ["Foam", "wikilinks"],
  "cSpell.flagWords": ["hte->the"],
  "cSpell.ignorePaths": ["journal/**"]
}
```

*Add to the repository's dictionary* writes to `words` in `cspell.json` if the repository has
one, and to `cSpell.words` in `.vscode/settings.json` otherwise, keeping the file's comments
and layout. Other cSpell settings, such as `language` (the app uses the languages you
download) and `minWordLength` (always 4), are ignored.

## Folders that aren't notes

`.foam`, `.git`, `.vscode`, `.github`, `node_modules`, `_site` and `_layouts` are never
scanned for notes. So templates, and the example links in them, never show up in the note
list, the graph or any other view, and never need excluding.

If a setting doesn't seem to apply, see [Troubleshooting](troubleshooting.md).

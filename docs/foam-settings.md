# Foam and repository settings

Some behavior is set in the notes repository itself, in the same files Foam and the VS Code
markdownlint extension use, so the app and VS Code follow the same rules.

## `.vscode/settings.json`

The app reads these settings from `.vscode/settings.json` at the root of the repository.
Other settings are ignored.

| Setting | Used for | Default |
| --- | --- | --- |
| `foam.orphans.exclude` | Patterns for notes left out of *Orphaned notes* | none |
| `foam.placeholders.exclude` | Patterns for notes whose links are left out of *Placeholders* | none |
| `markdownlint.config` | markdownlint rules, when the repository has no markdownlint configuration file | none |

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

## Folders that aren't notes

`.foam`, `.git`, `.vscode`, `.github`, `node_modules`, `_site` and `_layouts` are never
scanned for notes. So templates, and the example links in them, never show up in the note
list, the graph or any other view, and never need excluding.

If a setting doesn't seem to apply, see [Troubleshooting](troubleshooting.md).

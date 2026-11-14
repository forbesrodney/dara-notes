# Spelling dictionaries

The word lists the app checks spelling with. They're built here and published to the
`dictionaries` release; the app downloads the ones you pick in Settings.

- `languages.json`: the languages offered, each from a Hunspell dictionary published on npm
  (pinned to an exact version), plus the extras: cSpell's software terms and company names,
  so the app accepts the same technical words VS Code does.
- `expand.py`: turns a Hunspell dictionary into a plain, sorted word list, applying its
  prefixes and suffixes, so the app only has to look words up.
- `build.py`: downloads the sources, runs `expand.py`, and writes the gzipped lists, their
  licenses and `manifest.json` (sizes and SHA-256 checksums, which the app checks) to `out/`.

## Adding or updating a language

1. Edit `languages.json`: the code, the name to show, the npm package and version, and the
   license (from the package's `package.json`). The [wooorm/dictionaries](https://github.com/wooorm/dictionaries)
   packages (`dictionary-xx`) are UTF-8 and include their license.
2. Try it: `python3 dictionaries/build.py`, then look in `dictionaries/out/`.
3. Commit, push, and run the *Spelling dictionaries* workflow from the Actions tab. It
   replaces the files in the release, and the app offers *Update* for any that changed.

Only Python 3 is needed. Compound words (German's long nouns) aren't generated, so a
compound that isn't in the dictionary itself is flagged; ignoring it once fixes that.

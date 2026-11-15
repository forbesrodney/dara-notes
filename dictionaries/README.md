# Spelling dictionaries

The word lists the app checks spelling with. They're built here and published to the
`dictionaries` release; the app downloads the ones you pick in Settings.

- `languages.json`: the languages offered, each from two dictionaries published on npm
  (pinned to exact versions) whose words are combined: a Hunspell one, and cSpell's own for
  the language (`"cspell"`), so the app accepts the words VS Code does. cSpell's lists are
  often bigger: its English ones are SCOWL's larger lists, with words like "decrypt" that
  the standard Hunspell ones leave out. Then the extras: cSpell's software terms and company
  names, so the app accepts the same technical words VS Code does.
- `expand.py`: turns a Hunspell dictionary into a plain, sorted word list, applying its
  prefixes and suffixes, so the app only has to look words up.
- `cspell_trie.py`: reads cSpell's compiled dictionaries (`.trie.gz` in its `@cspell/dict-*`
  packages) into a list of words.
- `build.py`: downloads the sources, runs `expand.py` (and `cspell_trie.py`), and writes the gzipped lists, their
  licenses and `manifest.json` (sizes and SHA-256 checksums, which the app checks) to `out/`.

## Adding or updating a language

1. Edit `languages.json`: the code, the name to show, the two packages with their versions,
   and the license (from each package's `package.json`, joined with `+`).
   - `hunspell`: a [wooorm/dictionaries](https://github.com/wooorm/dictionaries) package
     (`dictionary-xx`). These are UTF-8 and include their license.
   - `cspell`: cSpell's package for the language (`@cspell/dict-xx`, listed at
     [cspell-dicts](https://github.com/streetsidesoftware/cspell-dicts)), with the
     `.trie.gz` file in it and its license file. The build warns about a language without one.
2. Try it: `python3 dictionaries/build.py`, then look in `dictionaries/out/`.
3. Commit, push, and run the *Spelling dictionaries* workflow from the Actions tab. It
   replaces the files in the release, and the app offers *Update* for any that changed.

Only Python 3 is needed. Compound words (German's long nouns) aren't generated, so a
compound that isn't in either dictionary is flagged; ignoring it once fixes that. Elided
forms in cSpell's lists (French `l'homme`) are left out when the word itself is listed,
since the app splits elisions off.

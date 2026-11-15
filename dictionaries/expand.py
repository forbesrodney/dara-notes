#!/usr/bin/env python3
"""
Expands a Hunspell dictionary (.dic and .aff) into the word list Dara Notes reads.

    expand.py index.dic index.aff out.txt

Every word form the dictionary allows is written, once, in lowercase, sorted. Hunspell's
prefixes and suffixes (two levels of suffixes, prefixes combined with suffixes, and the
NEEDAFFIX, CIRCUMFIX, FORBIDDENWORD and ONLYINCOMPOUND rules) are applied here, so the app
only has to look words up. Compound words (German's COMPOUNDBEGIN and so on) aren't
generated: there are too many.

The output is UTF-8 text:

    #dara-dictionary 1
    #elisions l' d' qu'      prefixes that join a word with an apostrophe (French)
    word                     the words, sorted by code point
    ...
    #accent-free
    cafe                     accent-free spellings of words that have accents, which
    ...                      cSpell also accepts; never suggested

Only words made of letters (and apostrophes inside them) are kept: the app skips anything
with digits, and splits words at hyphens, as cSpell does.
"""
import re
import sys
import unicodedata


class Affix:
    __slots__ = ("flag", "strip", "add", "cont", "cond", "cross", "prefix")

    def __init__(self, flag, strip, add, cont, cond, cross, prefix):
        self.flag, self.strip, self.add, self.cont = flag, strip, add, cont
        self.cond, self.cross, self.prefix = cond, cross, prefix

    def apply(self, word):
        """The word with this affix, or None if it doesn't apply."""
        if self.prefix:
            if not word.startswith(self.strip) or not self.cond.match(word):
                return None
            rest = word[len(self.strip):]
            return self.add + rest if rest or self.add else None
        if not word.endswith(self.strip) or not self.cond.search(word):
            return None
        rest = word[: len(word) - len(self.strip)] if self.strip else word
        return rest + self.add if rest or self.add else None


def condition(cond, prefix):
    """A Hunspell affix condition as a regular expression."""
    if cond in (".", ""):
        return re.compile("")
    out, i = [], 0
    while i < len(cond):
        c = cond[i]
        if c == "[":
            j = cond.index("]", i + 1)
            body = cond[i + 1:j]
            neg = body.startswith("^")
            if neg:
                body = body[1:]
            out.append("[" + ("^" if neg else "") + "".join(re.escape(ch) for ch in body) + "]")
            i = j + 1
            continue
        out.append("." if c == "." else re.escape(c))
        i += 1
    pattern = "".join(out)
    return re.compile("^" + pattern if prefix else pattern + "$")


class Aff:
    def __init__(self, path):
        self.mode = "char"
        self.aliases = []
        self.prefixes, self.suffixes = {}, {}
        self.flags = {}
        self.elisions = set()
        headers = {}
        with open(path, encoding="utf-8", errors="replace") as f:
            lines = f.read().splitlines()
        for line in lines:
            parts = line.split()
            if not parts or parts[0].startswith("#"):
                continue
            key = parts[0]
            if key == "FLAG" and len(parts) > 1:
                self.mode = {"long": "long", "num": "num", "UTF-8": "char"}.get(parts[1], "char")
            elif key == "AF" and len(parts) > 1 and not parts[1].isdigit():
                self.aliases.append(parts[1])
            elif key in ("NEEDAFFIX", "CIRCUMFIX", "FORBIDDENWORD", "ONLYINCOMPOUND") and len(parts) > 1:
                self.flags[key] = parts[1]
            elif key in ("PFX", "SFX") and len(parts) >= 4:
                flag = parts[1]
                ident = (key, flag)
                if ident not in headers:
                    headers[ident] = parts[2] == "Y"
                    continue
                strip = "" if parts[2] == "0" else parts[2]
                add, _, cont = parts[3].partition("/")
                add = "" if add == "0" else add
                cond = parts[4] if len(parts) > 4 else "."
                prefix = key == "PFX"
                affix = Affix(flag, strip, add, set(self.parse(cont)) if cont else set(),
                              condition(cond, prefix), headers[ident], prefix)
                if prefix and normalize(add).endswith("'"):
                    # An elision (French l', qu'…): the app splits these off instead, which
                    # saves listing every word once per elision.
                    self.elisions.add(normalize(add).lower())
                    continue
                (self.prefixes if prefix else self.suffixes).setdefault(flag, []).append(affix)

    def parse(self, s):
        """Flags as written in the .dic or an affix's continuation."""
        if self.aliases and s.isdigit():
            n = int(s)
            if 1 <= n <= len(self.aliases):
                s = self.aliases[n - 1]
        if self.mode == "long":
            return [s[i:i + 2] for i in range(0, len(s), 2)]
        if self.mode == "num":
            return [x for x in s.split(",") if x]
        return list(s)


def normalize(s):
    return s.replace("’", "'")


def expand(aff, word, flags, words, forbidden):
    needaffix = aff.flags.get("NEEDAFFIX")
    circumfix = aff.flags.get("CIRCUMFIX")
    forbid = aff.flags.get("FORBIDDENWORD")
    only_compound = aff.flags.get("ONLYINCOMPOUND")
    if forbid in flags:
        forbidden.add(word)
        return
    if only_compound in flags:
        return

    def emit(form, *conts):
        cont = set().union(*conts) if conts else set()
        if only_compound in cont:
            return
        if forbid in cont:
            forbidden.add(form)
            return
        words.add(form)

    if needaffix not in flags:
        emit(word)

    # Suffixes, up to two: (form, suffix, its continuation flags, needs more, circumfix).
    suffixed = []
    for f in flags:
        for s in aff.suffixes.get(f, ()):
            form = s.apply(word)
            if form is None:
                continue
            suffixed.append((form, s.cross, s.cont, circumfix in s.cont))
            for f2 in s.cont:
                for s2 in aff.suffixes.get(f2, ()):
                    form2 = s2.apply(form)
                    if form2 is not None:
                        suffixed.append((form2, s.cross and s2.cross, s2.cont, circumfix in s.cont or circumfix in s2.cont))
    for form, _, cont, circ in suffixed:
        if needaffix not in cont and not circ:
            emit(form, cont)

    # Prefixes, alone and with the suffixes.
    for f in set(flags):
        for p in aff.prefixes.get(f, ()):
            pcirc = circumfix in p.cont
            form = p.apply(word)
            if form is not None and needaffix not in p.cont and not pcirc:
                emit(form, p.cont)
            # Suffixes the prefix's continuation allows.
            for f2 in p.cont:
                for s in aff.suffixes.get(f2, ()):
                    sf = s.apply(word)
                    if sf is not None:
                        pf = p.apply(sf)
                        if pf is not None:
                            emit(pf, s.cont)
            if not p.cross:
                continue
            for sform, cross, cont, circ in suffixed:
                if not cross or circ != pcirc:
                    continue
                pf = p.apply(sform)
                if pf is not None:
                    emit(pf, cont, p.cont)
    # Prefixes a suffix's continuation allows.
    for sform, cross, cont, circ in suffixed:
        for f in cont:
            for p in aff.prefixes.get(f, ()):
                if circ != (circumfix in p.cont):
                    continue
                pf = p.apply(sform)
                if pf is not None:
                    emit(pf, cont, p.cont)


WORD = re.compile(r"^[^\W\d_]+(?:'[^\W\d_]+)*$")


def strip_accents(w):
    return unicodedata.normalize("NFC", "".join(
        c for c in unicodedata.normalize("NFD", w) if not unicodedata.combining(c)))


def main(dic_path, aff_path, out_path, more_words=()):
    aff = Aff(aff_path)
    words, forbidden = set(), set()
    with open(dic_path, encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    for i, line in enumerate(lines):
        if i == 0 and line.strip().isdigit():
            continue
        if not line.strip() or line.startswith(("\t", "#")):
            continue
        entry = line.split("\t")[0].strip()
        entry = re.split(r"\s+(?=[a-z][a-z]:)", entry)[0]  # morphology after the word
        # The word ends at the first unescaped slash; flags follow it.
        m = re.match(r"((?:\\/|[^/])+)(?:/(\S*))?", entry)
        if not m:
            continue
        word = m.group(1).replace("\\/", "/")
        flags = set(aff.parse(m.group(2))) if m.group(2) else set()
        expand(aff, word, flags, words, forbidden)

    kept = set()
    for w in words - forbidden:
        w = normalize(w).lower()
        if WORD.match(w):
            kept.add(w)
    # Words from elsewhere (cSpell's list for the language). An elided form (l'homme) whose
    # word is listed too is left out: the app splits elisions off itself.
    more = {normalize(w).lower() for w in more_words}
    more = {w for w in more if WORD.match(w)}
    for w in more:
        e = next((e for e in aff.elisions if w.startswith(e) and len(w) > len(e)), None)
        if e is None or (w[len(e):] not in more and w[len(e):] not in kept):
            kept.add(w)
    accent_free = {strip_accents(w) for w in kept} - kept
    with open(out_path, "w", encoding="utf-8", newline="\n") as out:
        out.write("#dara-dictionary 1\n")
        if aff.elisions:
            out.write("#elisions " + " ".join(sorted(aff.elisions)) + "\n")
        for w in sorted(kept):
            out.write(w + "\n")
        out.write("#accent-free\n")
        for w in sorted(accent_free):
            out.write(w + "\n")
    print(f"{out_path}: {len(kept)} words, {len(accent_free)} accent-free spellings", file=sys.stderr)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])

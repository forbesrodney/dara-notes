#!/usr/bin/env python3
"""
Builds the spelling dictionaries Dara Notes downloads, from languages.json.

    python3 dictionaries/build.py [out-dir]       (default: dictionaries/out)

For each language it downloads the pinned Hunspell dictionary (an npm package, through the
jsDelivr CDN), expands it into a word list with expand.py, and gzips it. The extras (cSpell's
software terms and company names) are plain word lists, merged into one. Each dictionary's
license is written next to it, and manifest.json lists them all with their sizes and SHA-256
checksums, which the app checks after downloading.

Needs only Python 3. The GitHub workflow (.github/workflows/dictionaries.yml) runs this and
publishes out-dir to the `dictionaries` release.
"""
import gzip
import hashlib
import io
import json
import os
import re
import sys
import tempfile
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import expand  # noqa: E402

CDN = "https://cdn.jsdelivr.net/npm/"


def fetch(package, path):
    url = CDN + package + "/" + path
    with urllib.request.urlopen(url, timeout=60) as r:
        data = r.read()
    return gzip.decompress(data) if path.endswith(".gz") else data


def write_gz(path, text):
    # mtime=0 so an unchanged dictionary builds to the same bytes (and checksum).
    with open(path, "wb") as f:
        with gzip.GzipFile(fileobj=f, mode="wb", compresslevel=9, mtime=0) as g:
            g.write(text.encode("utf-8"))


def entry(out, code, name, license_name, words, sources):
    gz = os.path.join(out, code + ".txt.gz")
    data = open(gz, "rb").read()
    return {
        "code": code,
        "name": name,
        "file": code + ".txt.gz",
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "words": words,
        "license": license_name,
        "licenseFile": code + ".LICENSE.txt",
        "source": sources,
    }


def build_language(lang, out, work):
    package = lang["hunspell"]
    code = lang["code"]
    print(f"{code}: {package}", file=sys.stderr)
    folder = os.path.join(work, code)
    os.makedirs(folder, exist_ok=True)
    for name in ("index.dic", "index.aff", "license"):
        with open(os.path.join(folder, name), "wb") as f:
            f.write(fetch(package, name))
    txt = os.path.join(folder, "words.txt")
    expand.main(os.path.join(folder, "index.dic"), os.path.join(folder, "index.aff"), txt)
    text = open(txt, encoding="utf-8").read()
    write_gz(os.path.join(out, code + ".txt.gz"), text)
    with open(os.path.join(out, code + ".LICENSE.txt"), "wb") as f:
        f.write(f"{lang['name']} spelling dictionary for Dara Notes, built from the npm package {package}.\n\n".encode())
        f.write(open(os.path.join(folder, "license"), "rb").read())
    words = text.split("#accent-free\n")[0].count("\n") - text.split("#accent-free\n")[0].count("#")
    return entry(out, code, lang["name"], lang["license"], words, CDN + package)


def build_extras(extras, out):
    code = extras["code"]
    words = set()
    licenses = []
    sources = []
    for item in extras["lists"]:
        package = item["package"]
        print(f"{code}: {package}", file=sys.stderr)
        sources.append(CDN + package)
        for path in item["files"]:
            for line in fetch(package, path).decode("utf-8").splitlines():
                line = line.split("#")[0].strip()
                # cSpell's markers: ! forbids a word, ~ and + mark case and compound rules.
                if not line or line.startswith("!"):
                    continue
                for w in re.split(r"[\s\-]+", line.strip("~+*")):
                    w = expand.normalize(w.strip("~+*")).lower()
                    if expand.WORD.match(w):
                        words.add(w)
        licenses.append(f"From the npm package {package}:\n\n" + fetch(package, item["licenseFile"]).decode("utf-8"))
    text = "#dara-dictionary 1\n" + "".join(w + "\n" for w in sorted(words)) + "#accent-free\n"
    write_gz(os.path.join(out, code + ".txt.gz"), text)
    with open(os.path.join(out, code + ".LICENSE.txt"), "w", encoding="utf-8") as f:
        f.write(f"{extras['name']} for Dara Notes, from cSpell's dictionaries.\n\n" + "\n\n".join(licenses))
    print(f"{code}: {len(words)} words", file=sys.stderr)
    return entry(out, code, extras["name"], extras["license"], len(words), " ".join(sources))


def main():
    out = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "out"))
    os.makedirs(out, exist_ok=True)
    config = json.load(open(os.path.join(HERE, "languages.json"), encoding="utf-8"))
    with tempfile.TemporaryDirectory() as work:
        languages = [build_language(lang, out, work) for lang in config["languages"]]
    extras = build_extras(config["extras"], out)
    manifest = {"version": 1, "languages": languages, "extras": extras}
    with open(os.path.join(out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"Wrote {out}", file=sys.stderr)


if __name__ == "__main__":
    main()

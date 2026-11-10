#!/usr/bin/env bash
# Bundles markdownlint (and js-yaml, for YAML configs) into a single offline script for the
# editor's Markdown checks: web/vendor/markdownlint.min.js
#
# Versions are pinned in scripts/markdownlint/package.json, and every sub-dependency in
# scripts/markdownlint/package-lock.json, so a rebuild produces the same file. To upgrade,
# change package.json, run `npm install --package-lock-only` in scripts/markdownlint, then
# run this script. Needs Node.js/npm.
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="$PWD/scripts/markdownlint"
OUT="$PWD/web/vendor/markdownlint.min.js"

# Build in a temporary copy so node_modules never lands in the repository.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp "$SRC/package.json" "$SRC/package-lock.json" "$SRC/entry.mjs" "$work/"
cd "$work"
npm ci --silent --no-audit --no-fund
npx esbuild entry.mjs --bundle --minify --format=iife --platform=browser --target=es2020 \
  --legal-comments=eof --outfile="$OUT"
version=$(node -p 'JSON.parse(require("fs").readFileSync("node_modules/markdownlint/package.json")).version')
echo "  $OUT ($(wc -c < "$OUT" | tr -d ' ') bytes, markdownlint $version)"

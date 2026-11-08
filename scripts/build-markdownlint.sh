#!/usr/bin/env bash
# Bundles markdownlint (and js-yaml, for YAML configs) into a single offline script for the
# editor's Markdown checks: app/src/main/assets/vendor/markdownlint.min.js
# Needs Node.js/npm. Re-run after changing the versions below.
set -euo pipefail
cd "$(dirname "$0")/.."
MARKDOWNLINT=0.42.1
JSYAML=4.1.0
ESBUILD=0.24.2
OUT="$PWD/app/src/main/assets/vendor/markdownlint.min.js"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work"
npm init -y >/dev/null
npm install --silent --no-audit --no-fund "markdownlint@$MARKDOWNLINT" "js-yaml@$JSYAML" "esbuild@$ESBUILD"
cat > entry.mjs <<'EOF'
import { lint } from "markdownlint/sync";
import { applyFixes, getVersion } from "markdownlint";
import yaml from "js-yaml";
globalThis.markdownlint = { lint, applyFixes, getVersion, parseYaml: (s) => yaml.load(s) };
EOF
npx esbuild entry.mjs --bundle --minify --format=iife --platform=browser --target=es2020 \
  --legal-comments=eof --outfile="$OUT"
echo "  $OUT ($(wc -c < "$OUT" | tr -d ' ') bytes, markdownlint $MARKDOWNLINT)"

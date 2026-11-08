#!/usr/bin/env bash
# Downloads the JavaScript libraries used by the Markdown preview into app assets.
set -euo pipefail
cd "$(dirname "$0")/.."
V=app/src/main/assets/vendor
CDN=https://cdn.jsdelivr.net/npm
mkdir -p "$V/katex/fonts"
get() { curl -fsSL "$1" -o "$2"; echo "  $2"; }
get "$CDN/markdown-it@14.1.0/dist/markdown-it.min.js"            "$V/markdown-it.min.js"
get "$CDN/markdown-it-footnote@4.0.0/dist/markdown-it-footnote.min.js" "$V/markdown-it-footnote.min.js"
get "$CDN/markdown-it-sup@2.0.0/dist/markdown-it-sup.min.js"     "$V/markdown-it-sup.min.js"
get "$CDN/markdown-it-sub@2.0.0/dist/markdown-it-sub.min.js"     "$V/markdown-it-sub.min.js"
get "$CDN/markdown-it-texmath@1.0.0/texmath.js"                  "$V/texmath.js"
get "$CDN/mermaid@11.4.1/dist/mermaid.min.js"                    "$V/mermaid.min.js"
get "$CDN/force-graph@1.51.5/dist/force-graph.min.js"           "$V/force-graph.min.js"
get "$CDN/katex@0.16.11/dist/katex.min.js"                       "$V/katex/katex.min.js"
get "$CDN/katex@0.16.11/dist/katex.min.css"                      "$V/katex/katex.min.css"
for f in $(grep -o 'fonts/KaTeX_[A-Za-z0-9_-]*\.woff2' "$V/katex/katex.min.css" | sort -u); do
  get "$CDN/katex@0.16.11/dist/$f" "$V/katex/$f"
done

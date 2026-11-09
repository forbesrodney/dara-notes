#!/usr/bin/env bash
# Reports newer versions of the pinned JavaScript libraries (scripts/fetch-vendor.sh and
# scripts/markdownlint/package.json). Changes nothing. Needs Node.js/npm.
#
# To upgrade one, change its version in the script, run it, test, and commit the result.
set -euo pipefail
cd "$(dirname "$0")/.."

pins=$(
  grep -oE '\$CDN/[A-Za-z0-9._-]+@[0-9][0-9A-Za-z.-]*' scripts/fetch-vendor.sh |
    sed -E 's#^\$CDN/##' | sort -u
  node -p 'Object.entries(require("./scripts/markdownlint/package.json").dependencies).map(([n, v]) => n + "@" + v).join("\n")'
)

outdated=0
printf '%-24s %-12s %s\n' "Library" "Pinned" "Latest"
for pin in $pins; do
  name=${pin%@*}
  pinned=${pin##*@}
  latest=$(npm view "$name" version 2>/dev/null || echo "?")
  mark=""
  if [ "$latest" != "$pinned" ] && [ "$latest" != "?" ]; then mark="  <- newer available"; outdated=1; fi
  printf '%-24s %-12s %s%s\n' "$name" "$pinned" "$latest" "$mark"
done
[ "$outdated" = 1 ] || echo "All pinned libraries are the latest versions."

#!/usr/bin/env bash
# Re-downloads everything: build dependencies (scripts/fetch-deps.sh), the JavaScript
# libraries (scripts/fetch-vendor.sh) and the markdownlint bundle
# (scripts/build-markdownlint.sh), then shows whether any committed library changed.
#
# This fetches the versions pinned in those scripts; it doesn't upgrade anything. To see
# whether newer versions exist, run scripts/check-updates.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

scripts/fetch-deps.sh
echo "== JavaScript libraries"
scripts/fetch-vendor.sh
echo "== markdownlint"
scripts/build-markdownlint.sh

echo "== Changes to committed libraries"
changes=$(git status --short -- app/src/main/assets/vendor)
if [ -z "$changes" ]; then
  echo "  none: the committed libraries match the pinned versions"
else
  echo "$changes"
  echo "  Review with: git diff --stat -- app/src/main/assets/vendor"
fi

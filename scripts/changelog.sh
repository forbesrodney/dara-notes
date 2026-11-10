#!/usr/bin/env bash
# Regenerates CHANGELOG.md from the commit history with git-cliff (config: .cliff.toml),
# printing only the commits git-cliff skipped because they don't match a group.
set -euo pipefail
cd "$(dirname "$0")/.."
git cliff -vv -o 2>&1 | grep 'Skipping commit' | sed -E 's/^.*Grouping error: //; s/`Skipping commit`/Skipping Commit :/' || true

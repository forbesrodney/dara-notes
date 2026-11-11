#!/usr/bin/env bash
# Builds the macOS .dmg from the packaged app: the app, a link to Applications, the arrow
# background and the Foam disk icon (desktopApp/packaging/). Called by macos-release.sh.
#   scripts/macos-dmg.sh            # after ./gradlew :desktopApp:createDistributable
#   scripts/macos-dmg.sh --setup    # only install dmgbuild (fetch-deps.sh does this)
# Output: desktopApp/build/compose/binaries/main/dmg/Dara Notes-<version>.dmg
#
# Uses dmgbuild (https://github.com/dmgbuild/dmgbuild), installed into a Python virtual
# environment under desktopApp/build. It writes the window layout itself, where jpackage's
# .dmg asks Finder to (which needs Automation permission and opens Finder windows).
set -euo pipefail
cd "$(dirname "$0")/.."

# 1.6.5 is the newest dmgbuild that runs on the Python 3.9 macOS ships.
DMGBUILD_VERSION=1.6.5
venv=desktopApp/build/dmgbuild
if [ ! -x "$venv/bin/dmgbuild" ] || [ "$("$venv/bin/pip" show dmgbuild 2>/dev/null | sed -n 's/^Version: //p')" != "$DMGBUILD_VERSION" ]; then
    echo "== Installing dmgbuild $DMGBUILD_VERSION"
    rm -rf "$venv"
    python3 -m venv "$venv"
    "$venv/bin/pip" install -q --disable-pip-version-check "dmgbuild==$DMGBUILD_VERSION"
fi
[ "${1:-}" = --setup ] && exit 0

name="Dara Notes"
app="desktopApp/build/compose/binaries/main/app/$name.app"
[ -d "$app" ] || { echo "No $app; run ./gradlew :desktopApp:createDistributable first." >&2; exit 1; }
version=$(perl -ne 'if (/packageVersion\s*=\s*"([^"]+)"/) { print $1; exit }' desktopApp/build.gradle.kts)
out=desktopApp/build/compose/binaries/main/dmg
dmg="$out/$name-$version.dmg"
mkdir -p "$out"
rm -f "$dmg"

"$venv/bin/dmgbuild" -s desktopApp/packaging/dmg-settings.py \
    -D app="$app" -D volume_icon=desktopApp/icons/app.icns \
    -D background=desktopApp/packaging/dmg-background.tiff \
    "$name" "$dmg" > /dev/null 2> >(grep -v "is deprecated" >&2)
echo "$dmg"

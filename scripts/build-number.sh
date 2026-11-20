#!/usr/bin/env bash
# Prints the build number that every app's release build uses (Android's versionCode, and
# CFBundleVersion for iOS and the Mac App Store): the minutes since 1970-01-01 UTC at the last
# commit's committer time, e.g. 29920211 for 2026-11-20 22:11 UTC. The same commit gives the
# same number on every platform, and each new commit a higher one. Play allows numbers up to
# 2100000000, which this reaches in the year 5962.
#
# A second upload of the same commit needs a higher number: set BUILD_NUMBER, which is printed
# instead (Gradle reads it too, for Android).
#
# To read one as a date:
#   macOS:  date -u -r $((29920211 * 60))
#   Linux:  date -u -d @$((29920211 * 60))
# (androidApp/build.gradle.kts works out the same number itself, since Gradle can't rely on bash.)
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "${BUILD_NUMBER:-}" ]; then
    echo "$BUILD_NUMBER"
    exit 0
fi
committed=$(git log -1 --format=%ct)
echo $((committed / 60))

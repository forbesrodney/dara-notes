#!/usr/bin/env bash
# Removes everything generated while building this project: Gradle and Kotlin build output,
# test and lint reports, and the project's Gradle and Kotlin state.
#
# Kept: everything committed to Git (including the JavaScript libraries in
# web/vendor), local.properties, and the downloads in ~/.gradle, which every
# Gradle project on this machine shares. Run scripts/fetch-deps.sh to download again.
set -euo pipefail
cd "$(dirname "$0")/.."

# Gradle and Kotlin daemons keep files in .gradle and .kotlin open.
./gradlew --stop >/dev/null 2>&1 || true

removed=0
for d in build androidApp/build desktopApp/build iosApp/build shared/build ui/build .gradle .kotlin; do
  if [ -e "$d" ]; then
    rm -rf -- "$d"
    echo "  removed $d"
    removed=1
  fi
done
[ "$removed" = 1 ] || echo "  nothing to clean"

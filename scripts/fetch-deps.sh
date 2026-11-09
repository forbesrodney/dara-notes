#!/usr/bin/env bash
# Downloads everything needed to build this project, so later builds can run offline:
#   - Android SDK packages (sdkmanager asks you to accept any licenses you haven't yet)
#   - the Gradle distribution (via the wrapper) and every Gradle dependency
#   - tools Gradle only fetches during a build (aapt2, lint), by running one full build
# The JavaScript libraries are committed, so they aren't downloaded here; see
# scripts/refresh-all.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

# Keep in step with compileSdk in app/build.gradle.kts and the build tools AGP uses.
SDK_PACKAGES=("platform-tools" "platforms;android-35" "build-tools;34.0.0")

: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK folder}"
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
[ -x "$sdkmanager" ] || sdkmanager="$(command -v sdkmanager || true)"
[ -n "$sdkmanager" ] || { echo "sdkmanager not found in \$ANDROID_HOME/cmdline-tools/latest/bin" >&2; exit 1; }

echo "== Android SDK packages"
"$sdkmanager" --install "${SDK_PACKAGES[@]}"

echo "== Gradle and project dependencies"
./gradlew downloadDependencies

echo "== One full build, for the tools Gradle downloads during a build"
./gradlew assembleDebug assembleRelease bundleRelease lintDebug testDebugUnitTest :shared:test

echo "Done. Builds can now run offline: ./gradlew --offline assembleDebug"

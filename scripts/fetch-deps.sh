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
SDK_PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;36.0.0")

# Android Studio's standard SDK location, unless ANDROID_HOME says otherwise.
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
[ -d "$ANDROID_HOME" ] || { echo "Android SDK not found at $ANDROID_HOME; set ANDROID_HOME" >&2; exit 1; }
export ANDROID_HOME
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
[ -x "$sdkmanager" ] || sdkmanager="$(command -v sdkmanager || true)"
[ -n "$sdkmanager" ] || { echo "sdkmanager not found in \$ANDROID_HOME/cmdline-tools/latest/bin" >&2; exit 1; }

echo "== Android SDK packages"
"$sdkmanager" --install "${SDK_PACKAGES[@]}"

echo "== Gradle and project dependencies"
./gradlew downloadDependencies

echo "== One full build, for the tools Gradle downloads during a build"
./gradlew assembleDebug assembleRelease bundleRelease lintDebug testDebugUnitTest :shared:jvmTest

echo "Done. Builds can now run offline: ./gradlew --offline assembleDebug"

#!/bin/sh
# Xcode build phase: compiles the shared Kotlin UI into DaraUI.framework for the platform and
# configuration Xcode is building, using Gradle. Xcode doesn't read your shell profile, so this
# finds a JDK 21 itself.
set -e
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
fi
if [ -z "$JAVA_HOME" ]; then
    echo "error: JDK 21 not found. Install one (brew install --cask temurin@21) or set JAVA_HOME." >&2
    exit 1
fi
export JAVA_HOME
cd "$SRCROOT/.."
./gradlew :ui:embedAndSignAppleFrameworkForXcode

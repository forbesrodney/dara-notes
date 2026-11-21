#!/usr/bin/env bash
# Builds the Mac App Store / TestFlight version of the desktop app: sandboxed, signed with the
# Mac App Distribution certificate, and packaged as a signed .pkg to upload with Transporter.
# (The notarized .dmg for downloads is scripts/macos-release.sh; the two don't mix.)
# Output: desktopApp/build/appstore/Dara Notes-<version>.pkg
# It ends by printing the version, the build number and the .pkg it made.
#
# The build number is the minutes since 1970 at the last commit (scripts/build-number.sh, or
# BUILD_NUMBER if set), the same as the Android and iOS builds of that commit; a second upload
# of the same commit needs BUILD_NUMBER.
#
# Needs, once:
#   - apple.teamId=<your team ID> in local.properties;
#   - in the login keychain, the "3rd Party Mac Developer Application" (Mac App Distribution)
#     and "3rd Party Mac Developer Installer" (Mac Installer Distribution) certificates;
#   - two Mac App Store provisioning profiles from developer.apple.com, for the App IDs
#     app.daranotes and com.oracle.java.app.daranotes (the bundled Java runtime), saved as
#     desktopApp/appstore/embedded.provisionprofile and runtime.provisionprofile (not committed).
set -euo pipefail
cd "$(dirname "$0")/.."

team=$(sed -nE 's/^apple\.teamId=([A-Z0-9]+).*/\1/p' local.properties 2>/dev/null | tail -1)
[ -n "$team" ] || { echo "Set apple.teamId in local.properties." >&2; exit 1; }
identity() { security find-identity -v "$@" | sed -nE "s/.*\"($kind: .*\($team\))\"/\1/p" | head -1; }
kind="3rd Party Mac Developer Application"; app_id=$(identity -p codesigning)
kind="3rd Party Mac Developer Installer"; installer_id=$(identity)
[ -n "$app_id" ] || { echo "No Mac App Distribution certificate for team $team in the keychain." >&2; exit 1; }
[ -n "$installer_id" ] || { echo "No Mac Installer Distribution certificate for team $team in the keychain." >&2; exit 1; }
for p in embedded runtime; do
    [ -f "desktopApp/appstore/$p.provisionprofile" ] || { echo "Missing desktopApp/appstore/$p.provisionprofile; see the top of $0." >&2; exit 1; }
done

# Profiles downloaded with a browser carry the quarantine attribute, and the build would copy
# it into the app, which App Store Connect refuses (ITMS-91109).
xattr -c desktopApp/appstore/*.provisionprofile

build=$(scripts/build-number.sh)
echo "== Building the sandboxed app (build $build)"
./gradlew -q :desktopApp:createDistributable -Pdaranotes.appStore=true -Pdaranotes.buildNumber="$build"
app="desktopApp/build/compose/binaries/main/app/Dara Notes.app"
contents="$app/Contents"

# A sandboxed app can't load native code it unpacks at run time, which is how JavaFX (the web
# views) and JNA (the Keychain) load theirs. JavaFX looks next to its jars first, and the build
# points JNA there (jna.boot.library.path), so their libraries go there, signed.
echo "== Adding the native libraries next to the jars"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
for jar in "$contents"/app/javafx-*-mac-aarch64-*.jar; do
    # javafx-base has none: unzip's "nothing matched" (11) is fine.
    unzip -q -o -j "$jar" '*.dylib' -d "$work/natives" 2>/dev/null || [ $? -eq 11 ]
done
unzip -q -o -j "$contents"/app/jna-[0-9]*.jar 'com/sun/jna/darwin-aarch64/libjnidispatch.jnilib' -d "$work/natives"
for lib in "$work"/natives/*; do
    codesign --force --sign "$app_id" --timestamp "$lib"
    cp "$lib" "$contents/app/"
done

# Adding files changed the app's sealed contents, so sign it again (the Java runtime inside is
# unchanged and keeps its own signature), with the entitlements the build wrote.
echo "== Signing the app"
# No extended attributes either (quarantine, or anything else picked up on the way).
xattr -cr "$app"
codesign --force --sign "$app_id" --timestamp --options runtime \
    --entitlements desktopApp/build/appstore/entitlements.plist "$app"
codesign --verify --deep --strict "$app"

version=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$contents/Info.plist")
out=desktopApp/build/appstore
pkg="$out/Dara Notes-$version.pkg"
mkdir -p "$out"
rm -f "$pkg"
echo "== Packaging"
productbuild --component "$app" /Applications --sign "$installer_id" "$pkg"
pkgutil --check-signature "$pkg" | sed -n '1,3p'

# Check the package the way App Store Connect will: nothing in it may be quarantined.
pkgutil --expand-full "$pkg" "$work/expanded"
if xattr -r "$work/expanded" 2>/dev/null | grep -q com.apple.quarantine; then
    echo "The package has quarantined files:" >&2
    xattr -r "$work/expanded" | grep com.apple.quarantine >&2
    exit 1
fi

echo "== Done (upload the .pkg with Transporter)"
echo "Version: $version"
echo "Build:   $(/usr/libexec/PlistBuddy -c "Print :CFBundleVersion" "$contents/Info.plist")"
echo "Output:  $pkg"

#!/usr/bin/env bash
# Builds the signed macOS .dmg (with scripts/macos-dmg.sh), has Apple notarize it, and staples the ticket, so it opens on
# any Mac without the "unidentified developer" warning.
# Output: desktopApp/build/compose/binaries/main/dmg/Dara Notes-<version>.dmg, and the same
# .dmg in a .zip next to it, for apps that won't send a .dmg.
#
# Needs, once:
#   - apple.teamId=<your team ID> in local.properties, and your "Developer ID Application"
#     certificate in the login keychain (the build signs with it);
#   - notarization credentials saved in the keychain under the profile "foam-notary"
#     (or set NOTARY_PROFILE), with an App Store Connect API key:
#       xcrun notarytool store-credentials foam-notary \
#           --key /path/AuthKey_XXXXXXXXXX.p8 --key-id XXXXXXXXXX --issuer <issuer UUID>
#     or with your Apple ID and an app-specific password (appleid.apple.com):
#       xcrun notarytool store-credentials foam-notary --apple-id you@example.com --team-id <team ID>
set -euo pipefail
cd "$(dirname "$0")/.."
profile="${NOTARY_PROFILE:-foam-notary}"

if ! xcrun notarytool history --keychain-profile "$profile" >/dev/null 2>&1; then
    echo "No notarization credentials in the keychain profile \"$profile\"; see the top of $0." >&2
    exit 1
fi

echo "== Building the signed app"
rm -rf desktopApp/build/compose/binaries/main/dmg
./gradlew -q :desktopApp:createDistributable
app="desktopApp/build/compose/binaries/main/app/Dara Notes.app"
codesign --verify --deep --strict "$app" || { echo "The app isn't signed: is apple.teamId set in local.properties?" >&2; exit 1; }

# Apple also checks native code inside the jars, whatever its file name; catch unsigned ones
# here rather than after the upload.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unsigned=0
for jar in "$app"/Contents/app/*.jar; do
    rm -rf "$work/x" && mkdir "$work/x" && unzip -q -o "$jar" -d "$work/x"
    while IFS= read -r -d '' f; do
        if file -b "$f" | grep -q "Mach-O" && ! codesign -v "$f" 2>/dev/null; then
            echo "Unsigned native code: $(basename "$jar") ${f#$work/x/}" >&2
            unsigned=$((unsigned + 1))
        fi
    done < <(find "$work/x" -type f -print0)
done
[ "$unsigned" -eq 0 ] || { echo "Notarization would fail: $unsigned unsigned native file(s)." >&2; exit 1; }

echo "== Building the .dmg"
dmg=$(scripts/macos-dmg.sh | tail -1)

# Compose signs the app but not the disk image; Gatekeeper checks the image when it's opened.
team=$(sed -nE 's/^[[:space:]]*apple\.teamId[[:space:]]*=[[:space:]]*([A-Z0-9]+).*/\1/p' local.properties | tail -1)
identity=$(security find-identity -v -p codesigning | sed -nE "s/.*\"(Developer ID Application: .*\($team\))\"/\1/p" | head -1)
[ -n "$identity" ] || { echo "No Developer ID Application certificate for team $team in the keychain." >&2; exit 1; }
codesign --force --sign "$identity" --timestamp "$dmg"

echo "== Notarizing (Apple usually takes a few minutes)"
result=$(xcrun notarytool submit "$dmg" --keychain-profile "$profile" --wait --output-format json)
id=$(printf '%s' "$result" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
status=$(printf '%s' "$result" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
echo "Submission $id: $status"
if [ "$status" != "Accepted" ]; then
    echo "Apple's report:" >&2
    xcrun notarytool log "$id" --keychain-profile "$profile" >&2
    exit 1
fi

echo "== Stapling the ticket"
xcrun stapler staple "$dmg"
spctl --assess --type open --context context:primary-signature --verbose "$dmg"

# A zip of the stapled .dmg, for sending through apps that won't take a .dmg (Signal, say).
zip="${dmg%.dmg}.zip"
rm -f "$zip"
ditto -c -k --norsrc --noextattr "$dmg" "$zip"
echo "Done: $dmg"
echo "Zip:  $zip"

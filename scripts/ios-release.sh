#!/usr/bin/env bash
# Builds a signed release of the iOS app: an archive, then an .ipa exported from it.
#   scripts/ios-release.sh                 # .ipa for TestFlight / the App Store
#   scripts/ios-release.sh release-testing # .ipa for registered devices (ad hoc)
# Output: iosApp/build/release/DaraNotes.xcarchive and iosApp/build/release/export/*.ipa
# It ends by printing the version, the build number and the .ipa it made.
#
# Needs your Apple Developer team ID as TEAM_ID in iosApp/Configuration/Local.xcconfig
# (not committed), e.g.  TEAM_ID = ABCDE12345
# The build number is the minutes since 1970 at the last commit (scripts/build-number.sh, or
# BUILD_NUMBER if set), the same as the Android and Mac App Store builds of that commit; a
# second upload of the same commit needs BUILD_NUMBER. The version shown is MARKETING_VERSION
# in Config.xcconfig.
# Signing is automatic. Xcode uses the Apple ID signed in under Xcode > Settings > Accounts,
# or an App Store Connect API key if these are set:
#   ASC_KEY_PATH=/path/AuthKey_XXXX.p8  ASC_KEY_ID=XXXX  ASC_ISSUER_ID=xxxxxxxx-...
set -euo pipefail
cd "$(dirname "$0")/.."

method="${1:-app-store-connect}"
local_config=iosApp/Configuration/Local.xcconfig
team=$(sed -nE 's/^[[:space:]]*TEAM_ID[[:space:]]*=[[:space:]]*([A-Z0-9]+).*/\1/p' "$local_config" 2>/dev/null | tail -1)
[ -n "$team" ] || { echo "Set TEAM_ID in $local_config first (your Apple Developer team ID)." >&2; exit 1; }

out=iosApp/build/release
auth=()
if [ -n "${ASC_KEY_PATH:-}" ]; then
    auth=(-authenticationKeyPath "$ASC_KEY_PATH" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")
fi

# The archive is left unsigned; the export signs it for its method (an App Store profile needs
# no registered devices, unlike the development profile Xcode would sign an archive with).
build=$(scripts/build-number.sh)
echo "== Archiving (Release, team $team, build $build)"
rm -rf "$out"
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$out/DaraNotes.xcarchive" \
    CURRENT_PROJECT_VERSION="$build" CODE_SIGNING_ALLOWED=NO archive -quiet

echo "== Exporting ($method)"
cat > "$out/ExportOptions.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key><string>$method</string>
    <key>teamID</key><string>$team</string>
    <key>signingStyle</key><string>automatic</string>
    <key>destination</key><string>export</string>
</dict>
</plist>
EOF
xcodebuild -exportArchive -archivePath "$out/DaraNotes.xcarchive" -exportPath "$out/export" \
    -exportOptionsPlist "$out/ExportOptions.plist" -allowProvisioningUpdates ${auth[@]+"${auth[@]}"} -quiet

archive_plist="$out/DaraNotes.xcarchive/Info.plist"
echo "== Done"
echo "Version: $(/usr/libexec/PlistBuddy -c "Print :ApplicationProperties:CFBundleShortVersionString" "$archive_plist")"
echo "Build:   $(/usr/libexec/PlistBuddy -c "Print :ApplicationProperties:CFBundleVersion" "$archive_plist")"
for ipa in "$out"/export/*.ipa; do echo "Output:  $ipa"; done
echo "Upload to App Store Connect with Xcode's Organizer (Window > Organizer), Transporter, or:"
echo "  xcrun altool --upload-app -t ios -f $out/export/*.ipa --apiKey ASC_KEY_ID --apiIssuer ASC_ISSUER_ID"

#!/usr/bin/env bash
# Builds the iOS app (Debug) and runs it in the iOS Simulator.
#   scripts/ios-run.sh                  # first available iPhone simulator
#   scripts/ios-run.sh "iPhone 17 Pro"  # a specific one (see: xcrun simctl list devices)
# Needs Xcode with an iOS simulator runtime (Xcode > Settings > Components).
set -euo pipefail
cd "$(dirname "$0")/.."

device="${1:-}"
if [ -z "$device" ]; then
    device=$(xcrun simctl list devices available | sed -nE 's/^ +(iPhone[^(]*[^ (]) \(([0-9A-F-]{36})\).*/\2/p' | head -1)
    [ -n "$device" ] || { echo "No iPhone simulator found. Install an iOS runtime in Xcode > Settings > Components." >&2; exit 1; }
fi

echo "== Building"
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
    -destination "platform=iOS Simulator,id=$device" -derivedDataPath iosApp/build -quiet build 2>&1 \
    | grep -vE "Metadata extraction skipped" || true
app=iosApp/build/Build/Products/Debug-iphonesimulator/FoamForiOS.app
[ -d "$app" ] || { echo "Build failed" >&2; exit 1; }

echo "== Starting the simulator"
xcrun simctl boot "$device" 2>/dev/null || true # already booted is fine
# Show the simulator window where the Simulator app exists (some Xcode versions show it in Xcode).
open -b com.apple.iphonesimulator --args -CurrentDeviceUDID "$device" 2>/dev/null || true
xcrun simctl bootstatus "$device" -b >/dev/null

echo "== Installing and launching"
bundle=$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$app/Info.plist")
xcrun simctl install "$device" "$app"
xcrun simctl launch "$device" "$bundle"

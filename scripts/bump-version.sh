#!/usr/bin/env bash
# Bumps the version of every app together, and their build numbers.
#   scripts/bump-version.sh micro     # 1.2.3 -> 1.2.4
#   scripts/bump-version.sh minor     # 1.2.3 -> 1.3.0
#   scripts/bump-version.sh major     # 1.2.3 -> 2.0.0
#   scripts/bump-version.sh build     # same version, next Android versionCode (another Play upload)
#   scripts/bump-version.sh 1.4.0     # a version of your choosing
# Add --dry-run to see the change without writing it.
#
# The version is written to:
#   androidApp/build.gradle.kts         versionName, and versionCode + 1
#   desktopApp/build.gradle.kts         packageVersion
#   iosApp/Configuration/Config.xcconfig  MARKETING_VERSION
# (The iOS and Mac App Store build numbers are the date and time, set by their release scripts.)
# Nothing is committed or tagged.
set -euo pipefail
cd "$(dirname "$0")/.."

usage() { sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }

part="" dry=false
for arg in "$@"; do
    case "$arg" in
        --dry-run|-n) dry=true ;;
        -h|--help) usage ;;
        *) [ -z "$part" ] || usage; part="$arg" ;;
    esac
done
[ -n "$part" ] || usage

android=androidApp/build.gradle.kts
desktop=desktopApp/build.gradle.kts
ios=iosApp/Configuration/Config.xcconfig

# Reads the first capture of a Perl regex from a file.
read_value() { perl -ne "if (/$2/) { print \$1; exit }" "$1"; }
# Pads 1.0 to 1.0.0.
normalize() { local v="$1"; while [ "$(tr -cd . <<<"$v" | wc -c)" -lt 2 ]; do v="$v.0"; done; echo "$v"; }

android_name=$(read_value "$android" 'versionName\s*=\s*"([^"]+)"')
android_code=$(read_value "$android" 'versionCode\s*=\s*(\d+)')
desktop_version=$(read_value "$desktop" 'packageVersion\s*=\s*"([^"]+)"')
ios_version=$(read_value "$ios" '^\s*MARKETING_VERSION\s*=\s*(\S+)')
for v in android_name android_code desktop_version ios_version; do
    [ -n "${!v}" ] || { echo "Couldn't find the current version ($v)." >&2; exit 1; }
done

current=$(normalize "$desktop_version")
if [ "$(normalize "$android_name")" != "$current" ] || [ "$(normalize "$ios_version")" != "$current" ]; then
    echo "The apps' versions differ: Android $android_name, desktop $desktop_version, iOS $ios_version." >&2
    echo "Pass the version to use, e.g.  scripts/bump-version.sh $current" >&2
    [[ "$part" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
fi

IFS=. read -r major minor micro <<<"$current"
case "$part" in
    major) next="$((major + 1)).0.0" ;;
    minor) next="$major.$((minor + 1)).0" ;;
    micro|patch) next="$major.$minor.$((micro + 1))" ;;
    build) next="$current" ;;
    *)
        [[ "$part" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || usage
        next="$part"
        ;;
esac
# macOS installers need a major version of at least 1.
[ "${next%%.*}" -ge 1 ] || { echo "The major version must be 1 or more (macOS requires it)." >&2; exit 1; }

next_code=$((android_code + 1))
echo "Version: $current -> $next"
echo "Android versionCode: $android_code -> $next_code"
$dry && { echo "(dry run: nothing written)"; exit 0; }

perl -pi -e "s/(versionName\s*=\s*\")[^\"]+/\${1}$next/; s/(versionCode\s*=\s*)\d+/\${1}$next_code/" "$android"
perl -pi -e "s/(packageVersion\s*=\s*\")[^\"]+/\${1}$next/" "$desktop"
perl -pi -e "s/^(\s*MARKETING_VERSION\s*=\s*)\S+/\${1}$next/" "$ios"

git --no-pager diff --stat -- "$android" "$desktop" "$ios" 2>/dev/null || true
[ "$part" = build ] || echo "When it's committed, tag it with:  git tag v$next"

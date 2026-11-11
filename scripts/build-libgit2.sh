#!/usr/bin/env bash
# Builds libgit2 (Git for the iOS app) as static libraries for iPhone, the iOS Simulator and
# the Mac (native tests), into shared/build/libgit2/<platform>/{include,lib}. Gradle runs this
# when the libraries are missing. Needs Xcode and CMake (brew install cmake).
#   HTTPS: Apple's Secure Transport (no OpenSSL); SSH: off (the apps use HTTPS tokens).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=1.9.7
SHA256=1a4fbe7589e814777ae76b64734ad80f4ecad22cd33a22682a2aaea4ae5375e7
OUT="$PWD/shared/build/libgit2"
IOS_MIN=18.5   # keep in step with IPHONEOS_DEPLOYMENT_TARGET in iosApp
MACOS_MIN=13.0

command -v cmake >/dev/null || { echo "CMake not found: brew install cmake" >&2; exit 1; }

src="$OUT/src/libgit2-$VERSION"
if [ ! -d "$src" ]; then
    mkdir -p "$OUT/src"
    tarball="$OUT/src/libgit2-$VERSION.tar.gz"
    if [ ! -f "$tarball" ]; then
        echo "== Downloading libgit2 $VERSION"
        curl -sfL "https://github.com/libgit2/libgit2/archive/refs/tags/v$VERSION.tar.gz" -o "$tarball"
    fi
    if ! echo "$SHA256  $tarball" | shasum -a 256 -c - >/dev/null; then
        echo "Checksum mismatch for $tarball" >&2
        rm -f -- "$tarball"
        exit 1
    fi
    tar -xzf "$tarball" -C "$OUT/src"
fi

build() { # name sysroot [cmake args...]
    local name=$1 sysroot=$2
    shift 2
    local prefix="$OUT/$name"
    if [ -f "$prefix/lib/libgit2.a" ]; then
        echo "== $name: up to date"
        return
    fi
    echo "== Building $name"
    local dir="$OUT/work/$name"
    rm -rf "$dir"
    cmake -S "$src" -B "$dir" -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_OSX_SYSROOT="$sysroot" -DCMAKE_OSX_ARCHITECTURES=arm64 \
        -DCMAKE_INSTALL_PREFIX="$prefix" \
        -DBUILD_SHARED_LIBS=OFF -DBUILD_TESTS=OFF -DBUILD_CLI=OFF -DBUILD_EXAMPLES=OFF \
        -DUSE_SSH=OFF -DUSE_HTTPS=SecureTransport -DUSE_SHA1=CollisionDetection -DUSE_SHA256=Builtin \
        -DREGEX_BACKEND=builtin -DUSE_BUNDLED_ZLIB=ON -DUSE_GSSAPI=OFF -DUSE_NTLMCLIENT=OFF \
        "$@" >"$dir.log" 2>&1 || { tail -30 "$dir.log" >&2; exit 1; }
    cmake --build "$dir" --target install -j "$(sysctl -n hw.ncpu)" >>"$dir.log" 2>&1 \
        || { tail -30 "$dir.log" >&2; exit 1; }
}

mkdir -p "$OUT/work"
build ios-arm64 iphoneos -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_DEPLOYMENT_TARGET=$IOS_MIN
build ios-simulator-arm64 iphonesimulator -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_DEPLOYMENT_TARGET=$IOS_MIN
build macos-arm64 macosx -DCMAKE_OSX_DEPLOYMENT_TARGET=$MACOS_MIN
echo "Done: $OUT"

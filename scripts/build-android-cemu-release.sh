#!/bin/bash
# Release Android build using CEmu backend - arm64 only, auto-installs
# Full optimizations, no debug overhead
# Usage: ./scripts/build-android-cemu-release.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Check if cemu-ref exists
if [ ! -d "cemu-ref/core" ]; then
    echo "Error: cemu-ref not found. Please clone CEmu first:"
    echo "  git clone https://github.com/CE-Programming/CEmu.git cemu-ref"
    exit 1
fi

echo "==> Building RELEASE Android APK with CEmu backend (arm64 only)..."

cd android

# Clean native build to ensure CEmu backend is used
rm -rf app/.cxx app/build/intermediates/cmake

# Build release with CEmu backend flag, arm64 only
./gradlew assembleRelease \
    -PuseCemu=true \
    -PabiFilters=arm64-v8a

echo "==> Installing APK..."
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk 2>/dev/null || \
adb install -r app/build/outputs/apk/release/app-release.apk

echo "==> Done!"

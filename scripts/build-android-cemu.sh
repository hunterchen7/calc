#!/bin/bash
# Build Android APK using CEmu backend (arm64 only, fast build)
# Usage: ./scripts/build-android-cemu.sh [--install]

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

echo "==> Building Android APK with CEmu backend (arm64 only)..."

cd android

# Clean native build to ensure CEmu backend is used
rm -rf app/.cxx app/build/intermediates/cmake

# Build with CEmu backend flag (arm64 only for fast builds)
./gradlew assembleDebug \
    -PuseCemu=true

if [ "$1" = "--install" ]; then
    echo "==> Installing APK..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk
fi

echo "==> Done! APK built with CEmu backend."

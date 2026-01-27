#!/bin/bash
# Auto-rebuild and install on file changes
# Requires: fswatch (brew install fswatch)

echo "Watching for changes in app/src/main..."
echo "Press Ctrl+C to stop"

# Initial build
./gradlew installDebug

# Watch for changes and rebuild
fswatch -o app/src/main | while read; do
    echo ""
    echo "=== Change detected, rebuilding... ==="
    ./gradlew installDebug --daemon
    if [ $? -eq 0 ]; then
        echo "=== Build successful, launching app ==="
        adb shell am start -n com.calc.emulator/.MainActivity
    else
        echo "=== Build failed ==="
    fi
done

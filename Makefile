# TI-84 CE Emulator Build Commands

.PHONY: android android-fast android-install android-cemu android-cemu-install \
        log-android test clean cemu cemu-test cemu-clean

# Build Android APK (all ABIs)
android:
	./scripts/build-android.sh

# Fast Android build (arm64 only) + install
android-fast:
	./scripts/build-android-fast.sh

# Build all ABIs and install
android-install:
	./scripts/build-android.sh --install

# Build Android APK using CEmu backend
android-cemu:
	./scripts/build-android-cemu.sh

# Build and install Android APK using CEmu backend
android-cemu-install:
	./scripts/build-android-cemu.sh --install

# Capture Android emulator logs to file
log-android:
	@echo "Capturing Android emulator logs..."
	@echo "Press Ctrl+C to stop logging"
	@adb logcat -c
	@adb logcat EmuCore:V EmuJNI:V MainActivity:D *:S | tee emulator_logs.txt

# Run Rust tests
test:
	cd core && cargo test --lib

# Clean build artifacts
clean:
	cd core && cargo clean
	cd android && ./gradlew clean

# CEmu backend (reference emulator)
cemu:
	@echo "Building CEmu core library..."
	$(MAKE) -C cemu-ref/core lib
	@echo "Building CEmu wrapper..."
	$(MAKE) -C cemu-ref/test cemu_wrapper.o
	@echo "CEmu library built: cemu-ref/core/libcemucore.a"

cemu-test: cemu
	@echo "Building CEmu test programs..."
	$(MAKE) -C cemu-ref/test all
	@echo ""
	@echo "Run tests with:"
	@echo "  cd cemu-ref/test && ./test_cemu 'path/to/TI-84 CE.rom'"
	@echo "  cd cemu-ref/test && ./test_wrapper 'path/to/TI-84 CE.rom'"

cemu-clean:
	$(MAKE) -C cemu-ref/core clean
	$(MAKE) -C cemu-ref/test clean

# Help
help:
	@echo "Available targets:"
	@echo "  make android             - Build APK for all Android ABIs (Rust)"
	@echo "  make android-fast        - Build arm64 only + install (Rust)"
	@echo "  make android-install     - Build all ABIs + install (Rust)"
	@echo "  make android-cemu        - Build APK using CEmu backend"
	@echo "  make android-cemu-install- Build + install using CEmu backend"
	@echo "  make log-android         - Capture emulator logs to emulator_logs.txt"
	@echo "  make test                - Run Rust tests"
	@echo "  make clean               - Clean all build artifacts"
	@echo ""
	@echo "CEmu backend (reference emulator for macOS):"
	@echo "  make cemu                - Build CEmu library for macOS"
	@echo "  make cemu-test           - Build CEmu test programs"
	@echo "  make cemu-clean          - Clean CEmu build artifacts"

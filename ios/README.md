# Calc iOS App

TI-84 Plus CE emulator for iOS, built with SwiftUI.

## Quick Start

From the project root:

```bash
# Build for device (Rust backend)
./scripts/build.sh ios

# Build for Simulator
./scripts/build.sh ios --sim

# Build with CEmu backend
./scripts/build.sh ios --cemu

# Debug build + open Xcode
./scripts/build.sh ios --debug --open
```

Or use make shortcuts:

```bash
make ios        # Release, device, Rust
make ios-sim    # Simulator, Rust
make ios-cemu   # Device, CEmu backend
```

## Prerequisites

- Xcode 15.0 or later
- iOS 16.0+ deployment target
- Rust toolchain with iOS target

## Manual Build

If not using `build.sh`:

**1. Build the Rust Core:**

```bash
rustup target add aarch64-apple-ios
cd core
cargo build --release --target aarch64-apple-ios
```

**2. Build the iOS App:**

```bash
cd ios
xcodebuild -project Calc.xcodeproj -scheme Calc build
```

Or open `ios/Calc.xcodeproj` in Xcode and build from there.

## Project Structure

```
ios/
├── Calc.xcodeproj/          # Xcode project
├── Calc/
│   ├── CalcApp.swift        # App entry point
│   ├── ContentView.swift    # Main view with state management
│   ├── Views/
│   │   ├── RomLoadingView.swift    # ROM picker screen
│   │   ├── EmulatorView.swift      # Main emulator screen
│   │   ├── KeypadView.swift        # Calculator keypad layout
│   │   ├── DPadView.swift          # D-pad navigation
│   │   ├── KeyButton.swift         # Individual key button
│   │   └── DebugOverlayView.swift  # Debug info panel
│   ├── Bridge/
│   │   ├── EmulatorBridge.swift    # Swift wrapper for C API
│   │   └── Calc-Bridging-Header.h  # C header imports
│   ├── Models/
│   │   └── KeyDef.swift            # Key definition model
│   └── Resources/
│       └── Assets.xcassets         # App icons, colors
└── include/
    └── emu.h                       # Symlink to core/include/emu.h
```

## Features

- Full TI-84 Plus CE keypad with accurate layout
- 320x240 LCD display at 60 FPS
- ROM file loading via document picker
- Pause/resume emulation
- Speed control (1x-10x)
- Debug overlay with cycle counts and logs
- Save/load state support

## Simulator

The build script auto-detects your Mac architecture:

```bash
./scripts/build.sh ios --sim    # Builds for correct simulator target
```

Manually:

```bash
# Apple Silicon Macs
rustup target add aarch64-apple-ios-sim
cargo build --release --target aarch64-apple-ios-sim

# Intel Macs
rustup target add x86_64-apple-ios
cargo build --release --target x86_64-apple-ios
```

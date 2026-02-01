//
//  EmulatorView.swift
//  Calc
//
//  Main emulator screen with LCD display, keypad, and sidebar menu.
//

import SwiftUI

/// Main emulator view with screen display and keypad
struct EmulatorView: View {
    @ObservedObject var state: EmulatorState
    @State private var showingSidebar = false
    @State private var showingRomPicker = false

    var body: some View {
        ZStack(alignment: .topLeading) {
            // Main content
            VStack(spacing: 8) {
                // Screen display
                screenDisplay
                    .aspectRatio(320.0 / 240.0, contentMode: .fit)
                    .background(Color.black)
                    .cornerRadius(4)
                    .padding(.horizontal, 8)
                    .padding(.top, 8)

                // Keypad
                KeypadView(
                    onKeyDown: { row, col in state.keyDown(row: row, col: col) },
                    onKeyUp: { row, col in state.keyUp(row: row, col: col) }
                )
            }

            // Sidebar toggle button
            Button(action: { withAnimation { showingSidebar.toggle() } }) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 24))
                    .foregroundColor(.white)
                    .padding(12)
            }

            // Sidebar overlay
            if showingSidebar {
                sidebarOverlay
            }

            // Debug overlay
            if state.showDebug {
                DebugOverlayView(state: state)
                    .padding(.top, 50)
            }
        }
        .sheet(isPresented: $showingRomPicker) {
            DocumentPicker { url in
                loadRom(from: url)
            }
        }
    }

    /// LCD screen display
    @ViewBuilder
    private var screenDisplay: some View {
        if state.isLcdOn, let image = state.screenImage {
            Image(decorative: image, scale: 1.0)
                .resizable()
                .interpolation(.none)
                .aspectRatio(contentMode: .fit)
        } else {
            // LCD off - show black
            Color.black
        }
    }

    /// Sidebar menu overlay
    private var sidebarOverlay: some View {
        HStack(spacing: 0) {
            // Sidebar content
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 24)

                Text("TI-84 Plus CE")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)

                Divider()
                    .background(Color(red: 0.2, green: 0.2, blue: 0.267))
                    .padding(.vertical, 8)

                // Load ROM
                sidebarButton(title: "Load ROM", color: .white) {
                    showingSidebar = false
                    showingRomPicker = true
                }

                // Pause/Run toggle
                sidebarButton(
                    title: state.isRunning ? "Pause Emulation" : "Run Emulation",
                    color: state.isRunning
                        ? Color(red: 1.0, green: 0.341, blue: 0.133)
                        : Color(red: 0.298, green: 0.686, blue: 0.314)
                ) {
                    state.isRunning.toggle()
                    showingSidebar = false
                }

                // Reset
                sidebarButton(title: "Reset", color: .white) {
                    state.reset()
                    showingSidebar = false
                }

                Divider()
                    .background(Color(red: 0.2, green: 0.2, blue: 0.267))
                    .padding(.vertical, 8)

                // Debug toggle
                sidebarButton(
                    title: state.showDebug ? "Hide Debug Info" : "Show Debug Info",
                    color: state.showDebug
                        ? Color(red: 0.612, green: 0.153, blue: 0.690)
                        : .white
                ) {
                    state.showDebug.toggle()
                }

                Divider()
                    .background(Color(red: 0.2, green: 0.2, blue: 0.267))
                    .padding(.vertical, 8)

                // Speed control
                VStack(alignment: .leading, spacing: 4) {
                    Text("Speed: \(Int(state.speedMultiplier))x")
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)

                    Slider(
                        value: $state.speedMultiplier,
                        in: 1...10,
                        step: 1
                    )
                    .tint(Color(red: 0.298, green: 0.686, blue: 0.314))
                    .padding(.horizontal, 16)
                }

                Spacer()

                // ROM info at bottom
                if let romName = state.romName {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("ROM: \(romName)")
                            .font(.system(size: 12))
                            .foregroundColor(.gray)
                        Text("Size: \(state.romSize / 1024) KB")
                            .font(.system(size: 12))
                            .foregroundColor(.gray)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
            .frame(width: 280)
            .background(Color(red: 0.102, green: 0.102, blue: 0.180)) // #1A1A2E

            // Tap outside to close
            Color.black.opacity(0.3)
                .onTapGesture {
                    withAnimation { showingSidebar = false }
                }
        }
        .edgesIgnoringSafeArea(.all)
    }

    /// Sidebar button helper
    private func sidebarButton(title: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16))
                .foregroundColor(color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
        }
    }

    /// Load ROM from URL
    private func loadRom(from url: URL) {
        do {
            guard url.startAccessingSecurityScopedResource() else {
                state.loadError = "Cannot access file"
                return
            }
            defer { url.stopAccessingSecurityScopedResource() }

            let data = try Data(contentsOf: url)
            let name = url.lastPathComponent
            state.loadRom(data, name: name)
        } catch {
            state.loadError = "Error: \(error.localizedDescription)"
        }
    }
}

#Preview {
    EmulatorView(state: EmulatorState())
        .preferredColorScheme(.dark)
}

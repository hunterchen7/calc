//
//  DebugOverlayView.swift
//  Calc
//
//  Draggable debug information overlay.
//

import SwiftUI

/// Draggable debug overlay showing emulator state
struct DebugOverlayView: View {
    @ObservedObject var state: EmulatorState
    @State private var offset: CGSize = CGSize(width: 6, height: 6)
    @State private var isDragging = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            // ROM info
            Text("ROM: \(state.romName ?? "Unknown") (\(state.romSize / 1024)KB)")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(Color(red: 0.310, green: 0.765, blue: 0.969)) // #4FC3F7

            // Frame and cycle counts
            Text("Frames: \(state.frameCounter) | Cycles: \(formatCycles(state.totalCyclesExecuted))")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(Color(red: 0.506, green: 0.780, blue: 0.518)) // #81C784

            // Speed info
            Text("Speed: 800K cycles/tick @ 60 FPS")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(Color(red: 1.0, green: 0.718, blue: 0.302)) // #FFB74D

            // Status
            Text("Status: \(state.isRunning ? "RUNNING" : "PAUSED")")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(state.isRunning
                    ? Color(red: 0.298, green: 0.686, blue: 0.314) // #4CAF50
                    : Color(red: 1.0, green: 0.341, blue: 0.133)) // #FF5722

            // Last key
            Text("Last Key: \(state.lastKeyPress)")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(Color(red: 0.882, green: 0.745, blue: 0.906)) // #E1BEE7

            // Logs
            if !state.logs.isEmpty {
                Spacer().frame(height: 3)
                Text("Logs:")
                    .font(.system(size: 9, design: .monospaced))
                    .foregroundColor(Color(red: 0.690, green: 0.745, blue: 0.773)) // #B0BEC5

                ForEach(state.logs.suffix(6), id: \.self) { line in
                    Text(line)
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(Color(red: 0.690, green: 0.745, blue: 0.773))
                        .lineLimit(1)
                }
            }
        }
        .padding(6)
        .background(Color(red: 0.102, green: 0.102, blue: 0.180).opacity(0.8)) // #1A1A2E
        .cornerRadius(4)
        .offset(offset)
        .gesture(
            DragGesture()
                .onChanged { value in
                    isDragging = true
                    offset = CGSize(
                        width: offset.width + value.translation.width,
                        height: offset.height + value.translation.height
                    )
                }
                .onEnded { _ in
                    isDragging = false
                }
        )
    }

    /// Format cycle count for display
    private func formatCycles(_ cycles: Int64) -> String {
        switch cycles {
        case 1_000_000_000...:
            return String(format: "%.2fG", Double(cycles) / 1_000_000_000)
        case 1_000_000...:
            return String(format: "%.2fM", Double(cycles) / 1_000_000)
        case 1_000...:
            return String(format: "%.1fK", Double(cycles) / 1_000)
        default:
            return "\(cycles)"
        }
    }
}

#Preview {
    let state = EmulatorState()
    state.romName = "TI84CE.rom"
    state.romSize = 4_194_304
    state.frameCounter = 1234
    state.totalCyclesExecuted = 45_200_000
    state.isRunning = true
    state.lastKeyPress = "(6,0) DOWN"
    state.logs = ["Boot complete", "LCD initialized", "Keypad ready"]

    return ZStack(alignment: .topLeading) {
        Color.black.ignoresSafeArea()
        DebugOverlayView(state: state)
    }
}

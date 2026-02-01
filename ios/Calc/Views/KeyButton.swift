//
//  KeyButton.swift
//  Calc
//
//  Individual calculator key button component.
//

import SwiftUI

/// Calculator key button with press animation and secondary labels
struct KeyButton: View {
    let keyDef: KeyDef
    let onDown: () -> Void
    let onUp: () -> Void

    @State private var isPressed = false

    static let labelHeight: CGFloat = 12
    private static let labelFontSize: CGFloat = 9

    var body: some View {
        VStack(spacing: 0) {
            // Secondary labels row (always reserve space for alignment)
            labelRow

            // Main key button
            mainButton
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var labelRow: some View {
        HStack(spacing: 0) {
            Text(keyDef.secondLabel ?? " ")
                .font(.system(size: Self.labelFontSize, weight: .semibold))
                .foregroundColor(keyDef.secondLabelColor ?? KeyDef.defaultSecondColor)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .opacity(keyDef.secondLabel == nil ? 0 : 1)

            Spacer()

            Text(keyDef.alphaLabel ?? " ")
                .font(.system(size: Self.labelFontSize, weight: .semibold))
                .foregroundColor(keyDef.alphaLabelColor ?? KeyDef.defaultAlphaColor)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .opacity(keyDef.alphaLabel == nil ? 0 : 1)
        }
        .padding(.horizontal, 3)
        .frame(height: Self.labelHeight)
    }

    private var mainButton: some View {
        let baseColor = keyDef.style.backgroundColor
        let borderColor = baseColor.blended(with: .black, ratio: keyDef.style.borderDarken)

        let topColor = isPressed
            ? baseColor.blended(with: .black, ratio: 0.22)
            : baseColor.blended(with: .white, ratio: 0.16)

        let bottomColor = isPressed
            ? baseColor.blended(with: .black, ratio: 0.32)
            : baseColor.blended(with: .black, ratio: 0.18)

        return ZStack {
            RoundedRectangle(cornerRadius: keyDef.style.cornerRadius)
                .fill(
                    LinearGradient(
                        colors: [topColor, bottomColor],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: keyDef.style.cornerRadius)
                        .stroke(borderColor, lineWidth: keyDef.style.borderWidth)
                )

            Text(keyDef.label)
                .font(.system(size: keyDef.isNumberKey ? 22 : 13, weight: keyDef.style == .white || keyDef.style == .blue ? .bold : .semibold))
                .foregroundColor(keyDef.style.textColor)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !isPressed {
                        isPressed = true
                        onDown()
                    }
                }
                .onEnded { _ in
                    isPressed = false
                    onUp()
                }
        )
    }
}

#Preview {
    VStack(spacing: 8) {
        HStack(spacing: 4) {
            KeyButton(
                keyDef: KeyDef("7", row: 3, col: 3, style: .white, secondLabel: "u", alphaLabel: "O"),
                onDown: {},
                onUp: {}
            )
            KeyButton(
                keyDef: KeyDef("8", row: 4, col: 3, style: .white, secondLabel: "v", alphaLabel: "P"),
                onDown: {},
                onUp: {}
            )
            KeyButton(
                keyDef: KeyDef("9", row: 5, col: 3, style: .white, secondLabel: "w", alphaLabel: "Q"),
                onDown: {},
                onUp: {}
            )
        }
        .frame(height: 55)

        HStack(spacing: 4) {
            KeyButton(
                keyDef: KeyDef("2nd", row: 1, col: 5, style: .yellow),
                onDown: {},
                onUp: {}
            )
            KeyButton(
                keyDef: KeyDef("alpha", row: 2, col: 7, style: .green, secondLabel: "A-lock"),
                onDown: {},
                onUp: {}
            )
            KeyButton(
                keyDef: KeyDef("enter", row: 6, col: 0, style: .blue, secondLabel: "entry"),
                onDown: {},
                onUp: {}
            )
        }
        .frame(height: 45)
    }
    .padding()
    .background(Color(red: 0.106, green: 0.106, blue: 0.106))
}

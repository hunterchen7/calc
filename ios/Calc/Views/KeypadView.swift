//
//  KeypadView.swift
//  Calc
//
//  Full TI-84 Plus CE keypad layout.
//

import SwiftUI

/// Complete keypad layout matching TI-84 Plus CE
struct KeypadView: View {
    let onKeyDown: (Int32, Int32) -> Void
    let onKeyUp: (Int32, Int32) -> Void

    // Row heights - uniform for non-numbers, taller for numbers
    private let standardRowHeight: CGFloat = 42
    private let funcRowHeight: CGFloat = 36
    private let controlRowHeight: CGFloat = 42
    private let numberButtonHeight: CGFloat = 56
    private let sideButtonHeight: CGFloat = 48

    // Spacing and sizing
    private let rowSpacing: CGFloat = 2
    private let columnSpacing: CGFloat = 16
    private let narrowColumnWeight: CGFloat = 1.0
    private let wideColumnWeight: CGFloat = 1.2
    private let dpadInset: CGFloat = 1

    var body: some View {
        VStack(spacing: rowSpacing) {
            // Row 1: Function keys (y=, window, zoom, trace, graph) - compact
            fiveKeyRow(
                keys: [
                    KeySpec("y=", row: 1, col: 4, style: .white, second: "stat plot", alpha: "f1"),
                    KeySpec("window", row: 1, col: 3, style: .white, second: "tblset", alpha: "f2"),
                    KeySpec("zoom", row: 1, col: 2, style: .white, second: "format", alpha: "f3"),
                    KeySpec("trace", row: 1, col: 1, style: .white, second: "calc", alpha: "f4"),
                    KeySpec("graph", row: 1, col: 0, style: .white, second: "table", alpha: "f5")
                ],
                rowHeight: funcRowHeight
            )

            // Rows 2-3: 2nd/mode/del + alpha/X,T,θ,n/stat on left, D-pad on right
            dpadRow

            // Row 4: math, apps, prgm, vars, clear
            fiveKeyRow(
                keys: [
                    KeySpec("math", row: 2, col: 6, second: "test", alpha: "A"),
                    KeySpec("apps", row: 3, col: 6, second: "angle", alpha: "B"),
                    KeySpec("prgm", row: 4, col: 6, second: "draw", alpha: "C"),
                    KeySpec("vars", row: 5, col: 6, second: "distr"),
                    KeySpec("clear", row: 6, col: 6)
                ],
                rowHeight: controlRowHeight
            )

            // Row 5: x⁻¹, sin, cos, tan, ^
            fiveKeyRow(
                keys: [
                    KeySpec("x⁻¹", row: 2, col: 5, second: "matrix"),
                    KeySpec("sin", row: 3, col: 5, second: "sin⁻¹", alpha: "E"),
                    KeySpec("cos", row: 4, col: 5, second: "cos⁻¹", alpha: "F"),
                    KeySpec("tan", row: 5, col: 5, second: "tan⁻¹", alpha: "G"),
                    KeySpec("^", row: 6, col: 5, second: "π", alpha: "H")
                ],
                rowHeight: controlRowHeight
            )

            // Row 6: x², ,, (, ), ÷
            fiveKeyRow(
                keys: [
                    KeySpec("x²", row: 2, col: 4, second: "√"),
                    KeySpec(",", row: 3, col: 4, second: "EE", alpha: "J"),
                    KeySpec("(", row: 4, col: 4, second: "{", alpha: "K"),
                    KeySpec(")", row: 5, col: 4, second: "}", alpha: "L"),
                    KeySpec("÷", row: 6, col: 4, style: .white, second: "e", alpha: "M")
                ],
                rowHeight: controlRowHeight
            )

            // Number block: side columns (log/ln/sto/on and ×/−/+/enter) with center number grid
            numericBlock
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 6)
        .background(Color(red: 0.106, green: 0.106, blue: 0.106))
    }

    // MARK: - Layout Helpers

    /// Key definition wrapper for five-column rows
    private struct KeySpec {
        let label: String
        let row: Int32
        let col: Int32
        let style: KeyStyle
        let second: String?
        let alpha: String?

        init(
            _ label: String,
            row: Int32,
            col: Int32,
            style: KeyStyle = .dark,
            second: String? = nil,
            alpha: String? = nil
        ) {
            self.label = label
            self.row = row
            self.col = col
            self.style = style
            self.second = second
            self.alpha = alpha
        }
    }

    private struct ColumnMetrics {
        let narrow: CGFloat
        let wide: CGFloat
    }

    private func columnMetrics(totalWidth: CGFloat) -> ColumnMetrics {
        let spacingTotal = columnSpacing * 4
        let available = max(0, totalWidth - spacingTotal)
        let totalWeight = narrowColumnWeight * 2 + wideColumnWeight * 3
        let narrow = available * (narrowColumnWeight / totalWeight)
        let wide = available * (wideColumnWeight / totalWeight)
        return ColumnMetrics(narrow: narrow, wide: wide)
    }

    @ViewBuilder
    private func withColumnMetrics<Content: View>(
        _ content: @escaping (ColumnMetrics) -> Content
    ) -> some View {
        GeometryReader { geo in
            let metrics = columnMetrics(totalWidth: geo.size.width)
            content(metrics)
                .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
        }
    }

    @ViewBuilder
    private func fiveKeyRow(keys: [KeySpec], rowHeight: CGFloat) -> some View {
        withColumnMetrics { metrics in
            HStack(spacing: columnSpacing) {
                columnKey(keys[0], width: metrics.narrow, height: rowHeight)
                columnKey(keys[1], width: metrics.wide, height: rowHeight)
                columnKey(keys[2], width: metrics.wide, height: rowHeight)
                columnKey(keys[3], width: metrics.wide, height: rowHeight)
                columnKey(keys[4], width: metrics.narrow, height: rowHeight)
            }
        }
        .frame(height: rowHeight)
    }

    private var dpadRow: some View {
        withColumnMetrics { metrics in
            let leftWidth = metrics.narrow + metrics.wide + metrics.wide + columnSpacing * 2
            let dpadWidth = metrics.wide + metrics.narrow + columnSpacing
            let totalHeight = controlRowHeight * 2 + rowSpacing
            let labelHeight = KeyButton.labelHeight
            let dpadHeight = max(0, totalHeight - labelHeight * 2)
            let dpadSize = max(0, min(dpadWidth, dpadHeight) - dpadInset * 2) * 1.1

            return HStack(spacing: columnSpacing) {
                VStack(spacing: rowSpacing) {
                    HStack(spacing: columnSpacing) {
                        columnKey(KeySpec("2nd", row: 1, col: 5, style: .yellow), width: metrics.narrow, height: controlRowHeight)
                        columnKey(KeySpec("mode", row: 1, col: 6, second: "quit"), width: metrics.wide, height: controlRowHeight)
                        columnKey(KeySpec("del", row: 1, col: 7, second: "ins"), width: metrics.wide, height: controlRowHeight)
                    }
                    HStack(spacing: columnSpacing) {
                        columnKey(KeySpec("alpha", row: 2, col: 7, style: .green, second: "A-lock"), width: metrics.narrow, height: controlRowHeight)
                        columnKey(KeySpec("X,T,θ,n", row: 3, col: 7, second: "link"), width: metrics.wide, height: controlRowHeight)
                        columnKey(KeySpec("stat", row: 4, col: 7, second: "list"), width: metrics.wide, height: controlRowHeight)
                    }
                }
                .frame(width: leftWidth, height: controlRowHeight * 2 + rowSpacing, alignment: .topLeading)

                VStack(spacing: 0) {
                    Spacer().frame(height: labelHeight)
                    DPadView(onKeyDown: onKeyDown, onKeyUp: onKeyUp)
                        .frame(width: dpadSize, height: dpadSize)
                        .frame(width: dpadWidth, height: dpadHeight, alignment: .center)
                }
                .frame(width: dpadWidth, height: totalHeight, alignment: .top)
            }
        }
        .frame(height: controlRowHeight * 2 + rowSpacing)
    }

    @ViewBuilder
    private func columnKey(_ spec: KeySpec, width: CGFloat, height: CGFloat) -> some View {
        keyButton(spec.label, row: spec.row, col: spec.col, style: spec.style, second: spec.second, alpha: spec.alpha)
            .frame(width: width, height: height)
    }

    // Numeric block: side columns with center number grid that extends past
    private var numericBlock: some View {
        let blockHeight = sideButtonHeight * 4 + rowSpacing * 3

        return withColumnMetrics { metrics in
            ZStack(alignment: .topLeading) {
                // Side columns: function keys (left) and operators (right)
                HStack(alignment: .top, spacing: columnSpacing) {
                    // Left column: log, ln, sto→, on
                    VStack(spacing: rowSpacing) {
                        keyButton("log", row: 2, col: 3, second: "10ˣ", alpha: "N")
                            .frame(width: metrics.narrow, height: sideButtonHeight)
                        keyButton("ln", row: 2, col: 2, second: "eˣ", alpha: "S")
                            .frame(width: metrics.narrow, height: sideButtonHeight)
                        keyButton("sto→", row: 2, col: 1, second: "rcl", alpha: "X")
                            .frame(width: metrics.narrow, height: sideButtonHeight)
                        keyButton("on", row: 2, col: 0, second: "off")
                            .frame(width: metrics.narrow, height: sideButtonHeight)
                    }

                    Spacer()

                    // Right column: ×, −, +, enter
                    VStack(spacing: rowSpacing) {
                        keyButton("×", row: 6, col: 3, style: .white, second: "[", alpha: "R")
                            .frame(width: metrics.narrow, height: sideButtonHeight)
                        keyButton("−", row: 6, col: 2, style: .white, second: "]", alpha: "W")
                            .frame(width: metrics.narrow, height: sideButtonHeight)
                        keyButton("+", row: 6, col: 1, style: .white, second: "mem", alpha: "\"")
                            .frame(width: metrics.narrow, height: sideButtonHeight)
                        keyButton("enter", row: 6, col: 0, style: .blue, second: "entry", alpha: "solve")
                            .frame(width: metrics.narrow, height: sideButtonHeight)
                    }
                }

                // Center number columns (offset and taller)
                HStack(spacing: columnSpacing) {
                    // Column: 7, 4, 1, 0
                    VStack(spacing: rowSpacing) {
                        keyButton("7", row: 3, col: 3, style: .white, second: "u", alpha: "O")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton("4", row: 3, col: 2, style: .white, second: "L4", alpha: "T")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton("1", row: 3, col: 1, style: .white, second: "L1", alpha: "Y")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton("0", row: 3, col: 0, style: .white, second: "catalog")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                    }

                    // Column: 8, 5, 2, .
                    VStack(spacing: rowSpacing) {
                        keyButton("8", row: 4, col: 3, style: .white, second: "v", alpha: "P")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton("5", row: 4, col: 2, style: .white, second: "L5", alpha: "U")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton("2", row: 4, col: 1, style: .white, second: "L2", alpha: "Z")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton(".", row: 4, col: 0, style: .white, second: "i", alpha: ":")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                    }

                    // Column: 9, 6, 3, (-)
                    VStack(spacing: rowSpacing) {
                        keyButton("9", row: 5, col: 3, style: .white, second: "w", alpha: "Q")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton("6", row: 5, col: 2, style: .white, second: "L6", alpha: "V")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton("3", row: 5, col: 1, style: .white, second: "L3", alpha: "θ")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                        keyButton("(−)", row: 5, col: 0, style: .white, second: "ans", alpha: "?")
                            .frame(width: metrics.wide, height: numberButtonHeight)
                    }
                }
                .padding(.leading, metrics.narrow + columnSpacing)
            }
        }
        .frame(height: blockHeight + (numberButtonHeight - sideButtonHeight) * 4)
    }

    // Number row with larger number keys and narrower operator
    @ViewBuilder
    private func numericRow(
        leftKey: (String, Int32, Int32, String?, String?),
        nums: [(String, Int32, Int32, String?, String?)],
        opKey: (String, Int32, Int32, String?, String?),
        rowHeight: CGFloat,
        leftHeight: CGFloat,
        numHeight: CGFloat,
        opHeight: CGFloat,
        leftStyle: KeyStyle = .dark,
        numStyle: KeyStyle = .white,
        opStyle: KeyStyle = .white
    ) -> some View {
        withColumnMetrics { metrics in
            HStack(alignment: .top, spacing: columnSpacing) {
                // Left function key (log, ln, sto→, on)
                keyButton(leftKey.0, row: leftKey.1, col: leftKey.2, style: leftStyle, second: leftKey.3, alpha: leftKey.4)
                    .frame(width: metrics.narrow, height: leftHeight)

                // Number keys - larger
                ForEach(nums, id: \.0) { num in
                    keyButton(num.0, row: num.1, col: num.2, style: numStyle, second: num.3, alpha: num.4)
                        .frame(width: metrics.wide, height: numHeight)
                }

                // Operator/enter key - narrower
                keyButton(opKey.0, row: opKey.1, col: opKey.2, style: opStyle, second: opKey.3, alpha: opKey.4)
                    .frame(width: metrics.narrow, height: opHeight)
            }
        }
        .frame(height: rowHeight)
    }

    // Key button helper
    @ViewBuilder
    private func keyButton(_ label: String, row: Int32, col: Int32, style: KeyStyle = .dark, second: String? = nil, alpha: String? = nil) -> some View {
        KeyButton(
            keyDef: KeyDef(label, row: row, col: col, style: style, secondLabel: second, alphaLabel: alpha),
            onDown: { onKeyDown(row, col) },
            onUp: { onKeyUp(row, col) }
        )
    }
}

#Preview {
    KeypadView(
        onKeyDown: { _, _ in },
        onKeyUp: { _, _ in }
    )
    .frame(height: 520)
}

//
//  KeyDef.swift
//  Calc
//
//  Key definition model for TI-84 Plus CE keypad.
//

import SwiftUI

/// Visual style for calculator keys
enum KeyStyle {
    case dark       // Dark gray - most control keys
    case yellow     // Blue - 2nd key
    case green      // Green - alpha key
    case white      // Light gray - number/function keys
    case blue       // Light gray - enter key
    case arrow      // Arrow keys (D-pad)

    /// Background color for key
    var backgroundColor: Color {
        switch self {
        case .yellow: return Color(red: 0.416, green: 0.714, blue: 0.902) // #6AB6E6
        case .green: return Color(red: 0.427, green: 0.745, blue: 0.271) // #6DBE45
        case .white: return Color(red: 0.902, green: 0.902, blue: 0.902) // #E6E6E6
        case .blue: return Color(red: 0.863, green: 0.863, blue: 0.863) // #DCDCDC
        case .arrow: return Color(red: 0.290, green: 0.290, blue: 0.290) // #4A4A4A
        case .dark: return Color(red: 0.176, green: 0.176, blue: 0.176) // #2D2D2D
        }
    }

    /// Text color for key label
    var textColor: Color {
        switch self {
        case .green, .white, .blue:
            return Color(red: 0.102, green: 0.102, blue: 0.102) // #1A1A1A
        default:
            return Color(red: 0.969, green: 0.969, blue: 0.969) // #F7F7F7
        }
    }

    /// Corner radius for key shape
    var cornerRadius: CGFloat {
        switch self {
        case .white, .blue: return 4
        case .yellow, .green: return 7
        default: return 6
        }
    }

    /// Border darkening factor
    var borderDarken: CGFloat {
        switch self {
        case .white, .blue: return 0.4
        case .dark: return 0.48
        default: return 0.35
        }
    }

    /// Border width
    var borderWidth: CGFloat {
        switch self {
        case .white, .blue: return 1.5
        default: return 1
        }
    }
}

/// Definition of a single calculator key
struct KeyDef: Identifiable {
    let id = UUID()
    let label: String
    let row: Int32
    let col: Int32
    let style: KeyStyle
    let secondLabel: String?       // Blue 2nd function label
    let alphaLabel: String?        // Green alpha label
    let secondLabelColor: Color?
    let alphaLabelColor: Color?

    /// Default secondary label color (blue)
    static let defaultSecondColor = Color(red: 0.475, green: 0.788, blue: 1.0) // #79C9FF

    /// Default alpha label color (green)
    static let defaultAlphaColor = Color(red: 0.494, green: 0.776, blue: 0.294) // #7EC64B

    init(
        _ label: String,
        row: Int32,
        col: Int32,
        style: KeyStyle = .dark,
        secondLabel: String? = nil,
        alphaLabel: String? = nil,
        secondLabelColor: Color? = nil,
        alphaLabelColor: Color? = nil
    ) {
        self.label = label
        self.row = row
        self.col = col
        self.style = style
        self.secondLabel = secondLabel
        self.alphaLabel = alphaLabel
        self.secondLabelColor = secondLabelColor
        self.alphaLabelColor = alphaLabelColor
    }

    /// Whether this is a number cluster key (0-9, ., (-))
    var isNumberKey: Bool {
        if label.count == 1, label.first?.isNumber == true {
            return true
        }
        return label == "." || label == "(-)"
    }
}

// MARK: - Color Utilities

extension Color {
    /// Blend this color with another color
    func blended(with overlay: Color, ratio: CGFloat) -> Color {
        let clamped = min(1, max(0, ratio))

        // Get RGB components
        let base = UIColor(self)
        let over = UIColor(overlay)

        var baseR: CGFloat = 0, baseG: CGFloat = 0, baseB: CGFloat = 0, baseA: CGFloat = 0
        var overR: CGFloat = 0, overG: CGFloat = 0, overB: CGFloat = 0, overA: CGFloat = 0

        base.getRed(&baseR, green: &baseG, blue: &baseB, alpha: &baseA)
        over.getRed(&overR, green: &overG, blue: &overB, alpha: &overA)

        return Color(
            red: baseR + (overR - baseR) * clamped,
            green: baseG + (overG - baseG) * clamped,
            blue: baseB + (overB - baseB) * clamped,
            opacity: baseA + (overA - baseA) * clamped
        )
    }
}

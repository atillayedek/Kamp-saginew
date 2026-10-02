import SwiftUI

/// Brand palette shared with the Android app (Material 3 theme), adapted to iOS system surfaces.
enum Brand {
    static let blue = Color("AccentColor")
    static let secondary = Color(light: 0x0EA5E9, dark: 0x38BDF8)
    static let danger = Color(light: 0xDC2626, dark: 0xF87171)
    static let success = Color(light: 0x16A34A, dark: 0x4ADE80)
    static let card = Color(uiColor: .secondarySystemGroupedBackground)
    static let softBlue = Color(light: 0xE3EEFF, dark: 0x0E3A7A)
}

enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
}

extension Color {
    /// A color that follows light/dark mode.
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                           blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
        })
    }
}

/// The large filled button used for the main action of a screen.
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 50)
            .foregroundStyle(.white)
            .background(Brand.blue.opacity(isEnabled ? (configuration.isPressed ? 0.8 : 1) : 0.4), in: RoundedRectangle(cornerRadius: 14))
    }
}

extension ButtonStyle where Self == PrimaryButtonStyle {
    static var primary: PrimaryButtonStyle { PrimaryButtonStyle() }
}

/// Initials in a colored circle; photos come with the profile-photo phase.
struct AvatarView: View {
    let name: String?
    var size: CGFloat = 40

    var body: some View {
        Text(Self.initials(name))
            .font(.system(size: size * 0.4, weight: .semibold))
            .foregroundStyle(Brand.blue)
            .frame(width: size, height: size)
            .background(Brand.softBlue, in: Circle())
            .accessibilityHidden(true)
    }

    static func initials(_ name: String?) -> String {
        let words = (name ?? "").replacingOccurrences(of: "@", with: "").split(separator: " ").prefix(2)
        let letters = words.compactMap { $0.first.map { String($0).uppercased(with: Locale(identifier: "tr_TR")) } }.joined()
        return letters.isEmpty ? "?" : letters
    }
}

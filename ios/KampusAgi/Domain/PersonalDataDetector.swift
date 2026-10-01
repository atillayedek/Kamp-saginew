import Foundation

/// Notices personal data a person is about to share in a post or comment so the app can warn them
/// first. Nothing is blocked; the text never leaves the device for this check. Same rules as the
/// Android app's PersonalDataDetector.kt.
enum PersonalDataDetector {
    enum Kind: CaseIterable {
        case tcIdentityNumber, iban, phoneNumber

        var label: String {
            switch self {
            case .tcIdentityNumber: return "T.C. kimlik numarası"
            case .iban: return "IBAN"
            case .phoneNumber: return "telefon numarası"
            }
        }
    }

    private static let elevenDigits = try! NSRegularExpression(pattern: "(?<!\\d)[1-9]\\d{10}(?!\\d)")
    private static let iban = try! NSRegularExpression(pattern: "(?<![A-Za-z0-9])[Tt][Rr]\\s?\\d{2}(?:\\s?\\d{4}){5}\\s?\\d{2}(?!\\d)")
    private static let phone = try! NSRegularExpression(pattern: "(?<!\\d)(?:\\+?90[\\s-]?|0)?\\(?5\\d{2}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}(?!\\d)")

    static func find(_ text: String) -> Set<Kind> {
        var found = Set<Kind>()
        if matches(elevenDigits, in: text).contains(where: isTcIdentityNumber) { found.insert(.tcIdentityNumber) }
        if matches(iban, in: text).contains(where: isValidTrIban) { found.insert(.iban) }
        var withoutNumbers = iban.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text), withTemplate: " ")
        for number in matches(elevenDigits, in: withoutNumbers) where isTcIdentityNumber(number) {
            withoutNumbers = withoutNumbers.replacingOccurrences(of: number, with: " ")
        }
        if phone.firstMatch(in: withoutNumbers, range: NSRange(withoutNumbers.startIndex..., in: withoutNumbers)) != nil {
            found.insert(.phoneNumber)
        }
        return found
    }

    /// The official checksum of a Turkish identity number (T.C. kimlik no).
    static func isTcIdentityNumber(_ value: String) -> Bool {
        let digits = value.compactMap(\.wholeNumberValue)
        guard value.count == 11, digits.count == 11, digits[0] != 0 else { return false }
        let odd = digits[0] + digits[2] + digits[4] + digits[6] + digits[8]
        let even = digits[1] + digits[3] + digits[5] + digits[7]
        let tenth = ((odd * 7 - even) % 10 + 10) % 10
        return digits[9] == tenth && digits[10] == digits.prefix(10).reduce(0, +) % 10
    }

    /// ISO 13616 mod-97 check of a Turkish IBAN (26 characters).
    static func isValidTrIban(_ value: String) -> Bool {
        let iban = value.filter { !$0.isWhitespace }.uppercased()
        guard iban.count == 26, iban.hasPrefix("TR") else { return false }
        let rearranged = iban.dropFirst(4) + iban.prefix(4)
        var remainder = 0
        for character in rearranged {
            let digits: String
            if let ascii = character.asciiValue, character.isLetter {
                digits = String(Int(ascii) - 55)
            } else if character.isNumber {
                digits = String(character)
            } else {
                return false
            }
            for digit in digits { remainder = (remainder * 10 + (digit.wholeNumberValue ?? 0)) % 97 }
        }
        return remainder == 1
    }

    private static func matches(_ regex: NSRegularExpression, in text: String) -> [String] {
        regex.matches(in: text, range: NSRange(text.startIndex..., in: text)).compactMap {
            Range($0.range, in: text).map { String(text[$0]) }
        }
    }
}

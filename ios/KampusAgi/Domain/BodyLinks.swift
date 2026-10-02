import Foundation

/// Finds @mentions and #tags in post and comment text so they can be shown as links.
///
/// The patterns match the database (`extract_usernames` / `extract_tags` in
/// `20261003100200_mentions_tags.sql`) and the Android app's BodyLinks.kt. An "@" or "#" glued to a
/// word (e-mail addresses, "C#") is not a link.
enum BodyLinks {

    enum Segment: Equatable {
        case plain(String)
        /// `username` is lower-case without "@", as typed; a sentence-ending "." is not part of `text`.
        case mention(text: String, username: String)
        /// `key` is the folded tag the server stores ("#Kampüs" -> "kampus").
        case tag(text: String, key: String)

        var text: String {
            switch self {
            case .plain(let t), .mention(let t, _), .tag(let t, _): return t
            }
        }
    }

    private static let letters = "A-Za-z0-9_çğıöşüÇĞİÖŞÜâîûÂÎÛ"
    private static let mentionRegex = try! NSRegularExpression(pattern: "(?<![A-Za-z0-9_.@])@([A-Za-z0-9_.]{3,31})")
    private static let tagRegex = try! NSRegularExpression(pattern: "(?<![\(letters)&#])#([\(letters)]{2,40})")
    private static let typingRegex = try! NSRegularExpression(pattern: "(?:^|[^A-Za-z0-9_.@])@([A-Za-z0-9_.]{0,30})$")

    /// Splits `text` into plain parts and links, in order; joining every segment's text gives `text` back.
    static func parse(_ text: String) -> [Segment] {
        let ns = text as NSString
        let full = NSRange(location: 0, length: ns.length)
        var links: [(NSRange, Segment)] = []

        for match in mentionRegex.matches(in: text, range: full) {
            let raw = ns.substring(with: match.range(at: 1)).lowercased()
            let trimmed = trimTrailingDots(raw)
            guard isUsername(raw) || isUsername(trimmed) else { continue }
            let range = NSRange(location: match.range.location, length: 1 + (trimmed as NSString).length)
            links.append((range, .mention(text: ns.substring(with: range), username: raw)))
        }
        for match in tagRegex.matches(in: text, range: full) {
            guard let key = tagKey(ns.substring(with: match.range(at: 1))) else { continue }
            links.append((match.range, .tag(text: ns.substring(with: match.range), key: key)))
        }
        links.sort { $0.0.location < $1.0.location }

        var segments: [Segment] = []
        var position = 0
        for (range, segment) in links where range.location >= position {
            if range.location > position {
                segments.append(.plain(ns.substring(with: NSRange(location: position, length: range.location - position))))
            }
            segments.append(segment)
            position = range.location + range.length
        }
        if position < ns.length {
            segments.append(.plain(ns.substring(from: position)))
        }
        return segments
    }

    /// Folded tag key as the server stores it, or nil when it is not a valid tag.
    static func tagKey(_ raw: String) -> String? {
        var value = raw.trimmingCharacters(in: .whitespaces)
        if value.hasPrefix("#") { value.removeFirst() }
        let key = fold(value)
        guard key.range(of: "^[a-z0-9_]{2,40}$", options: .regularExpression) != nil,
              key.contains(where: { $0.isLetter }) else { return nil }
        return key
    }

    /// The "@word" being typed at the end of `text` (without "@"), or nil; "" means "@" was just typed.
    static func mentionBeingTyped(_ text: String) -> String? {
        let ns = text as NSString
        guard let match = typingRegex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return ns.substring(with: match.range(at: 1)).lowercased()
    }

    /// Replaces the "@word" at the end of `text` with "@username ".
    static func completeMention(_ text: String, username: String) -> String {
        guard let typed = mentionBeingTyped(text) else { return text }
        return String(text.dropLast(typed.count + 1)) + "@" + username + " "
    }

    /// Same folding as the database's `search_fold`.
    static func fold(_ text: String) -> String {
        let map: [Character: Character] = [
            "İ": "i", "I": "i", "ı": "i", "Ç": "c", "ç": "c", "Ğ": "g", "ğ": "g", "Ö": "o", "ö": "o",
            "Ş": "s", "ş": "s", "Ü": "u", "ü": "u", "Â": "a", "â": "a", "Î": "i", "î": "i", "Û": "u", "û": "u",
        ]
        return String(text.map { map[$0] ?? Character($0.lowercased()) })
    }

    private static func isUsername(_ value: String) -> Bool {
        value.range(of: "^[a-z0-9_.]{3,30}$", options: .regularExpression) != nil
    }

    private static func trimTrailingDots(_ value: String) -> String {
        var result = value
        while result.hasSuffix(".") { result.removeLast() }
        return result
    }
}

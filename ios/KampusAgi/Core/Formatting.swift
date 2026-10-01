import Foundation

enum Formatting {
    private static let relative: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.unitsStyle = .short
        return formatter
    }()

    private static let dateTime: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()

    private static let day: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateStyle = .long
        formatter.timeStyle = .none
        return formatter
    }()

    /// "5 dk. önce"; "şimdi" under a minute.
    static func relative(_ date: Date, now: Date = Date()) -> String {
        if now.timeIntervalSince(date) < 60 { return "şimdi" }
        return relative.localizedString(for: date, relativeTo: now)
    }

    static func dateTime(_ date: Date) -> String { dateTime.string(from: date) }
    static func day(_ date: Date) -> String { day.string(from: date) }
}

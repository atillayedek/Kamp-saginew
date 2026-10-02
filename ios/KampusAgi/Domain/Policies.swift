import Foundation

enum AgePolicy {
    /// Whole years between `birthDate` and `today` (calendar dates, not 365-day blocks).
    static func age(birthDate: Date, today: Date, calendar: Calendar = .turkey) -> Int {
        calendar.dateComponents([.year], from: calendar.startOfDay(for: birthDate), to: calendar.startOfDay(for: today)).year ?? 0
    }

    static func isOldEnough(birthDate: Date, today: Date, minAge: Int, calendar: Calendar = .turkey) -> Bool {
        calendar.startOfDay(for: birthDate) <= calendar.startOfDay(for: today) && age(birthDate: birthDate, today: today, calendar: calendar) >= minAge
    }
}

/// Client-side checks that give instant feedback. Supabase Auth enforces its own rules; its answer wins.
enum AuthInputValidator {
    static let minPasswordLength = 8

    static func isValidEmail(_ email: String) -> Bool {
        email.trimmingCharacters(in: .whitespaces)
            .range(of: "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", options: .regularExpression) != nil
    }

    static func validateSignIn(email: String, password: String) -> Set<InputError> {
        var errors = Set<InputError>()
        if !isValidEmail(email) { errors.insert(.emailInvalid) }
        if password.isEmpty { errors.insert(.passwordRequired) }
        return errors
    }

    static func validateSignUp(email: String, password: String, confirm: String, birthDate: Date?,
                               minAge: Int, noticeRead: Bool, termsAccepted: Bool, today: Date = Date()) -> Set<InputError> {
        var errors = Set<InputError>()
        if !isValidEmail(email) { errors.insert(.emailInvalid) }
        if password.count < minPasswordLength { errors.insert(.passwordTooShort) }
        if password != confirm { errors.insert(.passwordsDoNotMatch) }
        if let birthDate {
            if !AgePolicy.isOldEnough(birthDate: birthDate, today: today, minAge: minAge) { errors.insert(.underage) }
        } else {
            errors.insert(.birthDateRequired)
        }
        if !noticeRead { errors.insert(.noticeNotRead) }
        if !termsAccepted { errors.insert(.termsNotAccepted) }
        return errors
    }
}

enum ProfileInputValidator {
    static func isValidFullName(_ value: String) -> Bool { (2...100).contains(value.trimmingCharacters(in: .whitespaces).count) }
    static func isValidUsername(_ value: String) -> Bool {
        value.range(of: "^[a-z0-9_.]{3,30}$", options: .regularExpression) != nil
    }
    static func isValidDepartment(_ value: String) -> Bool { (2...120).contains(value.trimmingCharacters(in: .whitespaces).count) }
    /// Usernames are typed in any case; the database stores lower-case ASCII.
    static func normalizeUsername(_ value: String) -> String { value.trimmingCharacters(in: .whitespaces).lowercased() }
}

enum PostTextValidator {
    static let maxPostLength = 2000
    static let maxCommentLength = 1000
    static func isValidPost(_ body: String) -> Bool { (1...maxPostLength).contains(body.trimmingCharacters(in: .whitespacesAndNewlines).count) }
    static func isValidComment(_ body: String) -> Bool { (1...maxCommentLength).contains(body.trimmingCharacters(in: .whitespacesAndNewlines).count) }
}

extension Calendar {
    /// Dates of birth and "today" are Turkish calendar dates.
    static var turkey: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Istanbul") ?? .current
        return calendar
    }
}

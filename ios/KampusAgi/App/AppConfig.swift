import Foundation
import UIKit

/// Build-time configuration from Info.plist (filled from Config/Secrets.xcconfig). Only client-safe values.
enum AppConfig {
    static let supabaseURL: String = value("KampusAgiSupabaseURL")
    static let supabaseAnonKey: String = value("KampusAgiSupabaseAnonKey")
    /// Website (legal pages, account deletion, FAQ). Empty when the build was made without it.
    static let websiteURL: String = {
        let url = value("KampusAgiWebsiteURL")
        return url.hasPrefix("https://") ? url : ""
    }()

    /// False when the build was made without Supabase credentials: the app shows "setup required".
    static var isBackendConfigured: Bool { supabaseURL.hasPrefix("https://") && !supabaseAnonKey.isEmpty }

    static let authRedirectURL = URL(string: "kampusagi://auth-callback")!
    static let passwordRecoveryRedirectURL = URL(string: "kampusagi://auth-callback?type=recovery")!

    static let studentDocumentsBucket = "student-documents"
    static let postMediaBucket = "post-media"
    static let submitStudentDocumentFunction = "submit-student-document"
    static let deleteAccountFunction = "delete-account"
    static let exportMyDataFunction = "export-my-data"
    static let deleteAccountConfirmation = "DELETE"

    static let platform = "ios"
    static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
    }

    /// "iPhone15,2; iOS 18.1" — model identifier and system version only, nothing personal.
    static var deviceInfo: String {
        var system = utsname()
        uname(&system)
        let model = withUnsafeBytes(of: &system.machine) { buffer in
            String(decoding: buffer.prefix(while: { $0 != 0 }), as: UTF8.self)
        }
        return "\(model); iOS \(UIDevice.current.systemVersion)"
    }

    static var userAgent: String { "KampusAgi/\(appVersion) (\(deviceInfo))" }

    static func websitePage(_ path: String) -> URL? {
        websiteURL.isEmpty ? nil : URL(string: websiteURL + path)
    }

    private static func value(_ key: String) -> String {
        (Bundle.main.object(forInfoDictionaryKey: key) as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
    }
}

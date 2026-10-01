import Foundation

enum AccountStatus: String, Decodable {
    case profileIncomplete = "PROFILE_INCOMPLETE"
    case documentRequired = "DOCUMENT_REQUIRED"
    case pendingReview = "PENDING_REVIEW"
    case approved = "APPROVED"
    case rejected = "REJECTED"
    case suspended = "SUSPENDED"
}

struct Profile: Equatable {
    let id: String
    let email: String
    let fullName: String?
    let username: String?
    let universityId: String?
    let universityName: String?
    let department: String?
    let status: AccountStatus
    let bio: String?
    let showFullName: Bool
}

struct University: Identifiable, Hashable, Decodable {
    let id: String
    let name: String
    let city: String
}

enum PostScope: String, CaseIterable, Codable, Identifiable {
    case general = "GENERAL"
    case university = "UNIVERSITY"
    var id: String { rawValue }
    var title: String { self == .general ? "Genel" : "Üniversitem" }
}

enum PostCategory: String, CaseIterable, Identifiable {
    case general = "GENERAL", question = "QUESTION", study = "STUDY", event = "EVENT",
         announcement = "ANNOUNCEMENT", marketplace = "MARKETPLACE", housing = "HOUSING",
         lostFound = "LOST_FOUND", career = "CAREER", sports = "SPORTS"
    var id: String { rawValue }

    var title: String {
        switch self {
        case .general: return "Genel"
        case .question: return "Soru"
        case .study: return "Ders & Sınav"
        case .event: return "Etkinlik"
        case .announcement: return "Duyuru"
        case .marketplace: return "Al-Sat"
        case .housing: return "Ev & Yurt"
        case .lostFound: return "Kayıp & Buluntu"
        case .career: return "Staj & Kariyer"
        case .sports: return "Spor"
        }
    }

    /// SF Symbol shown next to the category.
    var symbol: String {
        switch self {
        case .general: return "bubble.left"
        case .question: return "questionmark.circle"
        case .study: return "book"
        case .event: return "calendar"
        case .announcement: return "megaphone"
        case .marketplace: return "tag"
        case .housing: return "house"
        case .lostFound: return "magnifyingglass"
        case .career: return "briefcase"
        case .sports: return "sportscourt"
        }
    }
}

struct Author: Hashable {
    let id: String
    let fullName: String?
    let username: String?
    let university: String?

    var displayName: String { fullName ?? username.map { "@" + $0 } ?? "KampüsAğı öğrencisi" }
}

struct Post: Identifiable, Hashable {
    let id: String
    let scope: PostScope
    let category: PostCategory
    let body: String
    let createdAt: Date
    var likeCount: Int
    var commentCount: Int
    var likedByMe: Bool
    var savedByMe: Bool
    let isMine: Bool
    let author: Author
    /// Storage paths in the private post-media bucket.
    let media: [String]
    var poll: Poll?
    var event: PostEvent?
    var listing: Listing?
}

struct PollOption: Identifiable, Hashable {
    let id: String
    let label: String
    let votes: Int
}

struct Poll: Identifiable, Hashable {
    let id: String
    let closesAt: Date?
    let totalVotes: Int
    let myOptionId: String?
    let options: [PollOption]

    var isClosed: Bool { closesAt.map { $0 <= Date() } ?? false }
}

struct PostEvent: Hashable {
    let startsAt: Date
    let endsAt: Date?
    let location: String?
    var attendeeCount: Int
    var attending: Bool
}

struct Listing: Hashable {
    let priceKurus: Int
    var sold: Bool

    /// "1.250 ₺" / "Ücretsiz".
    var priceText: String {
        if priceKurus == 0 { return "Ücretsiz" }
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.currencyCode = "TRY"
        formatter.maximumFractionDigits = priceKurus % 100 == 0 ? 0 : 2
        return formatter.string(from: NSNumber(value: Double(priceKurus) / 100)) ?? "\(priceKurus / 100) ₺"
    }
}

struct FeedCursor: Equatable {
    let createdAt: String
    let id: String
}

struct FeedPage {
    let posts: [Post]
    let next: FeedCursor?
}

struct Comment: Identifiable, Hashable {
    let id: String
    let body: String
    let createdAt: Date
    let isMine: Bool
    let author: Author
}

struct PersonSummary: Identifiable, Hashable {
    let id: String
    let fullName: String?
    let username: String?
    let university: String?
    let department: String?
}

struct UserProfile: Equatable {
    let id: String
    let fullName: String?
    let username: String?
    let university: String?
    let department: String?
    let bio: String?
    let postCount: Int
    let isMe: Bool
}

struct MentionSuggestion: Identifiable, Hashable {
    let id: String
    let username: String
    let displayName: String?
    let university: String?
}

struct TagCount: Identifiable, Hashable {
    let tag: String
    let postCount: Int
    var id: String { tag }
}

enum NotificationKind: String {
    case newMessage = "NEW_MESSAGE"
    case newComment = "NEW_COMMENT"
    case mentioned = "MENTIONED"
    case verificationApproved = "VERIFICATION_APPROVED"
    case verificationRejected = "VERIFICATION_REJECTED"
    case contentRemoved = "CONTENT_REMOVED"
    case accountSuspended = "ACCOUNT_SUSPENDED"
    case appealDecided = "APPEAL_DECIDED"
    case dsrAnswered = "DSR_ANSWERED"
}

struct AppNotification: Identifiable, Hashable {
    let id: String
    let kind: NotificationKind
    let createdAt: Date
    let read: Bool
    let actorName: String?
    let conversationId: String?
    let postId: String?

    /// Message content is never part of a notification.
    var text: String {
        let who = actorName ?? "Bir öğrenci"
        switch kind {
        case .newMessage: return "\(who) sana mesaj gönderdi."
        case .newComment: return "\(who) gönderine yorum yaptı."
        case .mentioned: return "\(who) seni bir gönderide etiketledi."
        case .verificationApproved: return "Öğrenci doğrulaman onaylandı. Hoş geldin!"
        case .verificationRejected: return "Öğrenci belgen onaylanmadı. Nedenini görmek için dokun."
        case .contentRemoved: return "Bir içeriğin kaldırıldı. Gerekçesini ve itiraz yolunu görmek için dokun."
        case .accountSuspended: return "Hesabın askıya alındı. Gerekçesini görmek için dokun."
        case .appealDecided: return "İtirazın sonuçlandı."
        case .dsrAnswered: return "KVKK başvurun yanıtlandı."
        }
    }

    var symbol: String {
        switch kind {
        case .newMessage: return "bubble.left.and.bubble.right"
        case .newComment: return "text.bubble"
        case .mentioned: return "at"
        case .verificationApproved: return "checkmark.seal"
        case .verificationRejected: return "xmark.seal"
        case .contentRemoved, .accountSuspended, .appealDecided: return "shield"
        case .dsrAnswered: return "doc.text"
        }
    }
}

enum VerificationStatus: String, Decodable {
    case pending = "PENDING"
    case approved = "APPROVED"
    case rejected = "REJECTED"
}

struct Verification: Equatable {
    let status: VerificationStatus
    let rejectionReason: String?
}

// MARK: - Compliance

enum LegalKind: String, Decodable {
    case notice, agreement, consent, purchase
}

enum LegalDocTypes {
    static let privacyNotice = "aydinlatma_metni"
    static let privacyPolicy = "gizlilik_politikasi"
    static let terms = "kullanim_kosullari"
    static let communityRules = "topluluk_kurallari"
}

struct LegalDocumentInfo: Identifiable, Hashable {
    let docType: String
    let kind: LegalKind
    let version: Int
    let title: String
    var id: String { docType }
}

struct LegalDocument: Equatable {
    let docType: String
    let version: Int
    let title: String
    let content: String
    let publishedAt: String
}

struct ComplianceConfig: Equatable {
    let minAge: Int
    let deletionGraceDays: Int
    let registerLogNotice: String
    let loginLogNotice: String
    let documentUploadNotice: String
}

/// What must happen after sign-in before the app opens.
struct AccountGate: Equatable {
    let pending: [LegalDocumentInfo]
    let deletionScheduledFor: Date?
}

struct ConsentStatus: Identifiable, Hashable {
    let docType: String
    let title: String
    let version: Int
    let granted: Bool
    var id: String { docType }
}

/// Answers of the sign-up form; the database records them against the exact text versions.
struct SignUpConsents: Equatable {
    let birthDate: Date
    /// docType -> version
    let accepted: [String: Int]
    let informed: [String: Int]
    /// docType -> (version, granted)
    let consents: [String: ConsentChoice]
}

struct ConsentChoice: Equatable {
    let version: Int
    let granted: Bool
}

enum SignUpResult {
    case signedIn
    case verificationRequired
}

struct DataExport: Equatable {
    let htmlURL: URL
    let jsonURL: URL
    let expiresAt: Date
}

// MARK: - Moderation and KVKK requests

enum ReportTarget: String {
    case post = "POST", comment = "COMMENT", user = "USER"
}

/// Mirrors `public.report_reason`, in the order the report sheet lists them.
enum ReportReason: String, CaseIterable, Identifiable {
    case harassment = "HARASSMENT", hateSpeech = "HATE_SPEECH", personalDataLeak = "PERSONAL_DATA_LEAK",
         personalityRights = "PERSONALITY_RIGHTS", sexualContent = "SEXUAL_CONTENT", copyright = "COPYRIGHT",
         fakeProfile = "FAKE_PROFILE", spam = "SPAM", other = "OTHER"
    var id: String { rawValue }

    var title: String {
        switch self {
        case .harassment: return "Taciz veya zorbalık"
        case .hateSpeech: return "Nefret söylemi / ayrımcılık"
        case .personalDataLeak: return "Kişisel veri ifşası (numaram, adresim, belgem…)"
        case .personalityRights: return "Kişilik hakkı ihlali (hakaret, iftira)"
        case .sexualContent: return "Cinsel içerik"
        case .copyright: return "Telif ihlali"
        case .fakeProfile: return "Sahte profil"
        case .spam: return "Spam veya reklam"
        case .other: return "Diğer"
        }
    }
}

/// KVKK md.11 request types (API value = lower-case name).
enum DataRequestType: String, CaseIterable, Identifiable {
    case access, purpose, thirdParties = "third_parties", rectification, erasure,
         notifyThirdParties = "notify_third_parties", objectionAutomated = "objection_automated", compensation, other
    var id: String { rawValue }

    var title: String {
        switch self {
        case .access: return "Bilgi talebi"
        case .purpose: return "İşleme amacı"
        case .thirdParties: return "Aktarılan taraflar"
        case .rectification: return "Düzeltme"
        case .erasure: return "Silme"
        case .notifyThirdParties: return "Üçüncü kişilere bildirim"
        case .objectionAutomated: return "Otomatik analize itiraz"
        case .compensation: return "Zararın giderilmesi"
        case .other: return "Diğer"
        }
    }
}

struct DataSubjectRequest: Identifiable, Hashable {
    let requestNo: String
    let type: DataRequestType?
    let status: String
    let receivedAt: Date?
    let dueAt: Date?
    let response: String?
    var id: String { requestNo }

    var statusTitle: String {
        switch status {
        case "RECEIVED": return "Alındı"
        case "IN_PROGRESS": return "İnceleniyor"
        case "ANSWERED": return "Yanıtlandı"
        case "REJECTED": return "Reddedildi"
        default: return status
        }
    }
}

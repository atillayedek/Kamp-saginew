package com.kampusagi.android.domain.model

import java.time.LocalDate

/** Mirrors `legal_document_types.kind`. */
enum class LegalKind {
    /** Information only (aydınlatma); "read" is recorded, never consent. */
    NOTICE,

    /** Must be accepted to use the service (terms, community rules). */
    AGREEMENT,

    /** Optional explicit consent, separate from the notice; refusing never limits the service. */
    CONSENT,

    /** Accepted before buying Premium (pre-information, distance contract). */
    PURCHASE,
}

/** doc_type values of the published legal texts. */
object LegalDocTypes {
    const val PRIVACY_NOTICE = "aydinlatma_metni"
    const val PRIVACY_POLICY = "gizlilik_politikasi"
    const val TERMS = "kullanim_kosullari"
    const val COMMUNITY_RULES = "topluluk_kurallari"
    const val MARKETING_EMAIL = "acik_riza_pazarlama_eposta"
    const val MARKETING_PUSH = "acik_riza_pazarlama_bildirim"
    const val PREMIUM_PRE_INFO = "premium_on_bilgilendirme"
    const val DISTANCE_CONTRACT = "mesafeli_sozlesme"
    const val COPYRIGHT_POLICY = "telif_politikasi"
}

data class LegalDocumentInfo(
    val docType: String,
    val kind: LegalKind,
    val version: Int,
    val title: String,
    val sha256: String,
)

data class LegalDocument(
    val docType: String,
    val kind: LegalKind,
    val version: Int,
    val title: String,
    /** Markdown, exactly as published (its SHA-256 is [sha256]). */
    val content: String,
    val sha256: String,
    val publishedAt: String,
    val isActive: Boolean,
)

/** Durations and notices from `compliance_settings`; the app never hard-codes them. */
data class ComplianceConfig(
    val minAge: Int,
    val documentRetentionDays: Int,
    val deletionGraceDays: Int,
    val registerLogNotice: String,
    val loginLogNotice: String,
    val documentUploadNotice: String,
)

/** What the person agreed to on the sign-up form; the birth date is used for the age check only. */
data class SignUpConsents(
    val birthDate: LocalDate,
    /** Agreements accepted, by doc_type -> version. */
    val accepted: Map<String, Int>,
    /** Notices shown and read, by doc_type -> version. */
    val informed: Map<String, Int>,
    /** Optional consents, by doc_type -> (version, granted). */
    val consents: Map<String, Pair<Int, Boolean>>,
)

data class ConsentStatus(
    val docType: String,
    val title: String,
    val activeVersion: Int,
    val granted: Boolean,
    val changedAt: String?,
)

enum class ConsentAction { ACCEPTED, DECLINED, WITHDRAWN, INFORMED }

data class ConsentEvent(
    val docType: String,
    val version: Int?,
    val action: ConsentAction,
    val channel: String,
    val at: String,
)

/** KVKK md.11 request types (api value = lower-case name). */
enum class DataRequestType {
    ACCESS,
    PURPOSE,
    THIRD_PARTIES,
    RECTIFICATION,
    ERASURE,
    NOTIFY_THIRD_PARTIES,
    OBJECTION_AUTOMATED,
    COMPENSATION,
    OTHER,
}

enum class DataRequestStatus { RECEIVED, IN_PROGRESS, ANSWERED, REJECTED }

data class DataSubjectRequest(
    val requestNo: String,
    val type: DataRequestType,
    val details: String,
    val status: DataRequestStatus,
    val receivedAt: String,
    val dueAt: String,
    val response: String?,
)

data class AccessLogEntry(
    val event: String,
    val ip: String?,
    val device: String?,
    val occurredAt: String,
)

data class DataExport(val htmlUrl: String, val jsonUrl: String, val expiresAt: String)

data class ModerationDecision(
    val reportId: String,
    val target: ReportTarget,
    val reason: ReportReason,
    /** REMOVE_CONTENT or SUSPEND_USER. */
    val suspended: Boolean,
    val excerpt: String?,
    val decidedAt: String?,
    /** OPEN, UPHELD or REVERSED; null when not appealed. */
    val appealStatus: String?,
    val appealNote: String?,
)

/** What has to happen before the app can be used after signing in. */
data class AccountGate(
    /** Texts to accept (agreements) or read (changed notices). */
    val pending: List<LegalDocumentInfo>,
    /** ISO time the account will be deleted; null when no deletion is pending. */
    val deletionScheduledFor: String?,
)

/** Null in the repository while nobody is signed in. */
sealed interface AccountGateState {
    data object Loading : AccountGateState
    data class Ready(val gate: AccountGate) : AccountGateState
    data class Failed(val error: AppError) : AccountGateState
}

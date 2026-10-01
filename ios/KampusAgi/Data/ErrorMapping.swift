import Foundation

/// Maps any backend failure to a domain error. Details stay inside the data layer.
enum ErrorMapping {
    static func appError(_ error: Error) -> AppError {
        if let error = error as? AppError { return error }
        if error is URLError { return .network }
        if error is DecodingError { return .server }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain { return .network }
        let text = (String(describing: error) + " " + error.localizedDescription).lowercased()
        if let known = knownCode(text) { return known }
        if text.contains("payload too large") || text.contains("maximum allowed size") { return .documentTooLarge }
        if text.contains("invalid_mime_type") || text.contains("mime type") { return .documentNotPdf }
        if text.contains("row-level security") { return .verificationNotAllowed }
        if text.contains("email_not_confirmed") || text.contains("email not confirmed") { return .emailNotConfirmed }
        if text.contains("invalid_credentials") || text.contains("invalid login credentials") || text.contains("invalid_grant") {
            return .invalidCredentials
        }
        if text.contains("user_already_exists") || text.contains("email_exists") || text.contains("already registered") { return .emailInUse }
        if text.contains("same_password") { return .samePassword }
        if text.contains("weak_password") || text.contains("password should") { return .weakPassword }
        if text.contains("rate_limit") || text.contains("too many requests") { return .rateLimited }
        if text.contains("session_not_found") || text.contains("jwt expired") || text.contains("session missing") { return .sessionExpired }
        return .unknown
    }

    /// An Edge Function's `{"error": "<code>"}` answer.
    static func functionError(status: Int, body: String) -> AppError {
        if let known = knownCode(body.lowercased()) { return known }
        switch status {
        case 401: return .sessionExpired
        case 429: return .rateLimited
        case 500...: return .server
        default: return .unknown
        }
    }

    /// Stable snake_case codes raised by our database functions (SQLSTATE P0001) and Edge Functions.
    /// Same list as the Android app's ErrorMapping.kt.
    static func knownCode(_ text: String) -> AppError? {
        let rules: [([String], AppError)] = [
            (["username_taken"], .usernameTaken),
            (["university_not_found"], .universityNotFound),
            (["profile_locked"], .profileLocked),
            (["invalid_full_name", "invalid_username", "invalid_department"], .invalidInput),
            (["invalid_document_path", "invalid_request", "invalid_requirement"], .invalidInput),
            (["invalid_post_body", "invalid_comment_body", "invalid_scope"], .invalidInput),
            (["invalid_bio", "invalid_avatar_path", "avatar_not_found"], .invalidInput),
            (["not_authenticated"], .sessionExpired),
            (["approved_student_required"], .accountNotApproved),
            (["too_many_active_requirements"], .tooManyActiveRequirements),
            (["requirement_daily_limit"], .requirementDailyLimit),
            (["document_too_large"], .documentTooLarge),
            (["invalid_document"], .documentNotPdf),
            (["verification_not_allowed"], .verificationNotAllowed),
            (["admin_required"], .adminRequired),
            (["rejection_reason_required"], .rejectionReasonRequired),
            (["verification_not_pending"], .verificationNotPending),
            (["document_not_found", "profile_not_found"], .notFound),
            (["post_not_found", "comment_not_found", "requirement_not_found"], .notFound),
            (["recipient_not_available"], .recipientNotAvailable),
            (["conversation_not_found"], .notFound),
            (["invalid_message"], .invalidInput),
            (["billing_not_configured"], .billingNotConfigured),
            (["billing_unavailable"], .billingUnavailable),
            (["plan_not_available"], .planNotAvailable),
            (["purchase_not_active"], .purchaseNotActive),
            (["purchase_not_for_account", "purchase_belongs_to_another_account"], .purchaseNotForAccount),
            (["user_not_found", "report_target_not_found", "report_not_found"], .notFound),
            (["invalid_report", "invalid_action", "confirmation_required"], .invalidInput),
            (["account_deletion_failed"], .server),
            (["promo_code_used"], .promoCodeUsed),
            (["promo_code_exhausted"], .promoCodeExhausted),
            (["promo_code_invalid"], .promoCodeInvalid),
            (["premium_required"], .premiumRequired),
            (["too_many_groups"], .tooManyGroups),
            (["group_full"], .groupFull),
            (["owner_cannot_leave"], .ownerCannotLeave),
            (["group_not_allowed"], .groupNotAllowed),
            (["group_not_found", "message_not_found", "note_not_found"], .notFound),
            (["invalid_group", "invalid_note"], .invalidInput),
            (["poll_closed"], .pollClosed),
            (["event_ended"], .eventEnded),
            (["too_many_saved_posts"], .tooManySaved),
            (["invalid_media", "invalid_poll", "invalid_event", "invalid_price"], .invalidInput),
            (["invalid_query", "invalid_tag"], .invalidInput),
            (["poll_not_found", "event_not_found", "listing_not_found"], .notFound),
            (["rate_limited"], .rateLimited),
            (["sensitive_tag"], .sensitiveTag),
            (["rights_declaration_required"], .rightsDeclarationRequired),
            (["appeal_exists"], .appealExists),
            (["invalid_appeal", "invalid_channel"], .invalidInput),
            (["legal_document_not_found"], .notFound),
        ]
        return rules.first { codes, _ in codes.contains { text.contains($0) } }?.1
    }
}

/// Runs `body` and turns any failure into an AppError, never swallowing cancellation.
func attempt<T>(_ body: () async throws -> T) async -> Result<T, AppError> {
    do {
        return .success(try await body())
    } catch is CancellationError {
        return .failure(.unknown)
    } catch {
        return .failure(ErrorMapping.appError(error))
    }
}

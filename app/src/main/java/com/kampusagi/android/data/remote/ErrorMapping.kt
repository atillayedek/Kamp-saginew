package com.kampusagi.android.data.remote

import com.kampusagi.android.domain.model.AppError
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/** Maps any backend failure to a domain error. Details stay inside the data layer. */
internal fun Throwable.toAppError(): AppError = when (this) {
    is BackendNotConfiguredException -> AppError.NOT_CONFIGURED
    is HttpRequestException, is IOException -> AppError.NETWORK
    is RestException -> restError()
    is SerializationException -> AppError.SERVER
    is UnknownStatusException -> AppError.SERVER
    else -> AppError.UNKNOWN
}

private fun RestException.restError(): AppError {
    val text = listOfNotNull(error, message).joinToString(" ").lowercase()
    return when {
        // Stable messages raised by our database functions (SQLSTATE P0001).
        "username_taken" in text -> AppError.USERNAME_TAKEN
        "university_not_found" in text -> AppError.UNIVERSITY_NOT_FOUND
        "profile_locked" in text -> AppError.PROFILE_LOCKED
        "invalid_full_name" in text || "invalid_username" in text || "invalid_department" in text ->
            AppError.INVALID_INPUT
        "not_authenticated" in text -> AppError.SESSION_EXPIRED
        "approved_student_required" in text -> AppError.ACCOUNT_NOT_APPROVED
        "post_not_found" in text || "comment_not_found" in text -> AppError.NOT_FOUND
        "invalid_post_body" in text || "invalid_comment_body" in text || "invalid_scope" in text -> AppError.INVALID_INPUT
        "document_too_large" in text || statusCode == 413 || "payload too large" in text ||
            "maximum allowed size" in text -> AppError.DOCUMENT_TOO_LARGE
        "invalid_document" in text || "invalid_mime_type" in text || "mime type" in text -> AppError.DOCUMENT_NOT_PDF
        "verification_not_allowed" in text || "row-level security" in text -> AppError.VERIFICATION_NOT_ALLOWED
        "admin_required" in text -> AppError.ADMIN_REQUIRED
        "rejection_reason_required" in text -> AppError.REJECTION_REASON_REQUIRED
        "verification_not_pending" in text -> AppError.VERIFICATION_NOT_PENDING
        "document_not_found" in text -> AppError.NOT_FOUND
        // Supabase Auth.
        "email_not_confirmed" in text || "email not confirmed" in text -> AppError.EMAIL_NOT_CONFIRMED
        "invalid_credentials" in text || "invalid login credentials" in text || "invalid_grant" in text ->
            AppError.INVALID_CREDENTIALS
        "user_already_exists" in text || "email_exists" in text || "already registered" in text -> AppError.EMAIL_IN_USE
        "same_password" in text -> AppError.SAME_PASSWORD
        "weak_password" in text || "password should" in text -> AppError.WEAK_PASSWORD
        "rate_limit" in text || statusCode == 429 -> AppError.RATE_LIMITED
        "session_not_found" in text || "jwt expired" in text || statusCode == 401 -> AppError.SESSION_EXPIRED
        statusCode == 404 -> AppError.NOT_FOUND
        statusCode >= 500 -> AppError.SERVER
        else -> AppError.UNKNOWN
    }
}

/** runCatching that never swallows coroutine cancellation. */
internal inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/** The database returned a status value this app version does not know. */
class UnknownStatusException(value: String) : IllegalStateException("Unknown status: $value")

/** Maps an Edge Function's `{"error": "<code>"}` response to a domain error. */
internal fun functionError(status: Int, body: String): AppError {
    val text = body.lowercase()
    return when {
        "invalid_document_path" in text -> AppError.INVALID_INPUT
        "document_too_large" in text -> AppError.DOCUMENT_TOO_LARGE
        "invalid_document" in text -> AppError.DOCUMENT_NOT_PDF
        "verification_not_allowed" in text -> AppError.VERIFICATION_NOT_ALLOWED
        "document_not_found" in text || "profile_not_found" in text -> AppError.NOT_FOUND
        "not_authenticated" in text || status == 401 -> AppError.SESSION_EXPIRED
        status == 429 -> AppError.RATE_LIMITED
        status >= 500 -> AppError.SERVER
        else -> AppError.UNKNOWN
    }
}

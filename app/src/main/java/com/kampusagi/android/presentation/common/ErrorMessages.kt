package com.kampusagi.android.presentation.common

import androidx.annotation.StringRes
import com.kampusagi.android.R
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.usecase.AuthInputError
import com.kampusagi.android.domain.usecase.ProfileInputError

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.NETWORK -> R.string.error_network
    AppError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
    AppError.EMAIL_NOT_CONFIRMED -> R.string.error_email_not_confirmed
    AppError.EMAIL_IN_USE -> R.string.error_email_in_use
    AppError.WEAK_PASSWORD -> R.string.error_weak_password
    AppError.SAME_PASSWORD -> R.string.error_same_password
    AppError.RATE_LIMITED -> R.string.error_rate_limited
    AppError.SESSION_EXPIRED -> R.string.error_session_expired
    AppError.NOT_CONFIGURED -> R.string.error_not_configured
    AppError.USERNAME_TAKEN -> R.string.error_username_taken
    AppError.UNIVERSITY_NOT_FOUND -> R.string.error_university_not_found
    AppError.PROFILE_LOCKED -> R.string.error_profile_locked
    AppError.INVALID_INPUT -> R.string.error_invalid_input
    AppError.DOCUMENT_TOO_LARGE -> R.string.error_document_too_large
    AppError.DOCUMENT_NOT_PDF -> R.string.error_document_not_pdf
    AppError.DOCUMENT_UNREADABLE -> R.string.error_document_unreadable
    AppError.IMAGE_UNREADABLE -> R.string.error_image_unreadable
    AppError.VERIFICATION_NOT_ALLOWED -> R.string.error_verification_not_allowed
    AppError.ADMIN_REQUIRED -> R.string.error_admin_required
    AppError.REJECTION_REASON_REQUIRED -> R.string.error_rejection_reason_required
    AppError.VERIFICATION_NOT_PENDING -> R.string.error_verification_not_pending
    AppError.ACCOUNT_NOT_APPROVED -> R.string.error_account_not_approved
    AppError.TOO_MANY_ACTIVE_REQUIREMENTS -> R.string.error_too_many_active_requirements
    AppError.REQUIREMENT_DAILY_LIMIT -> R.string.error_requirement_daily_limit
    AppError.PUSH_NOT_CONFIGURED -> R.string.error_push_not_configured
    AppError.RECIPIENT_NOT_AVAILABLE -> R.string.error_recipient_not_available
    AppError.BILLING_UNAVAILABLE -> R.string.error_billing_unavailable
    AppError.BILLING_NOT_CONFIGURED -> R.string.error_billing_not_configured
    AppError.PLAN_NOT_AVAILABLE -> R.string.error_plan_not_available
    AppError.PURCHASE_NOT_ACTIVE -> R.string.error_purchase_not_active
    AppError.PURCHASE_NOT_FOR_ACCOUNT -> R.string.error_purchase_not_for_account
    AppError.PURCHASE_CANCELLED -> R.string.error_purchase_cancelled
    AppError.POLL_CLOSED -> R.string.error_poll_closed
    AppError.EVENT_ENDED -> R.string.error_event_ended
    AppError.TOO_MANY_SAVED -> R.string.error_too_many_saved
    AppError.PREMIUM_REQUIRED -> R.string.error_premium_required
    AppError.PROMO_CODE_INVALID -> R.string.error_promo_code_invalid
    AppError.PROMO_CODE_USED -> R.string.error_promo_code_used
    AppError.PROMO_CODE_EXHAUSTED -> R.string.error_promo_code_exhausted
    AppError.TOO_MANY_GROUPS -> R.string.error_too_many_groups
    AppError.GROUP_FULL -> R.string.error_group_full
    AppError.OWNER_CANNOT_LEAVE -> R.string.error_owner_cannot_leave
    AppError.GROUP_NOT_ALLOWED -> R.string.error_group_not_allowed
    AppError.NOT_FOUND -> R.string.error_not_found
    AppError.SERVER -> R.string.error_server
    AppError.UNKNOWN -> R.string.error_unknown
}

@StringRes
fun AuthInputError.messageRes(): Int = when (this) {
    AuthInputError.EMAIL_INVALID -> R.string.input_email_invalid
    AuthInputError.PASSWORD_REQUIRED -> R.string.input_password_required
    AuthInputError.PASSWORD_TOO_SHORT -> R.string.input_password_too_short
    AuthInputError.PASSWORDS_DO_NOT_MATCH -> R.string.input_passwords_do_not_match
}

@StringRes
fun ProfileInputError.messageRes(): Int = when (this) {
    ProfileInputError.FULL_NAME_INVALID -> R.string.input_full_name_invalid
    ProfileInputError.USERNAME_INVALID -> R.string.input_username_invalid
    ProfileInputError.UNIVERSITY_REQUIRED -> R.string.input_university_required
    ProfileInputError.DEPARTMENT_INVALID -> R.string.input_department_invalid
}

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

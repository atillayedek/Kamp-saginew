package com.kampusagi.android.domain.model

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String) : AuthState
}

enum class SignUpResult {
    /** The account exists; the person has to confirm their email first. */
    VERIFICATION_REQUIRED,

    /** Email confirmation is disabled for the project and the person is signed in. */
    SIGNED_IN,
}

sealed interface AuthRedirectResult {
    data object NotAnAuthLink : AuthRedirectResult
    data object SignedIn : AuthRedirectResult
    data object PasswordRecovery : AuthRedirectResult
    data object LinkExpired : AuthRedirectResult
    data class Failed(val error: AppError) : AuthRedirectResult
}

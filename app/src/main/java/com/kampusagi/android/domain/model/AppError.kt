package com.kampusagi.android.domain.model

/**
 * Errors the UI knows how to explain in plain language. Raw exceptions,
 * HTTP codes and SQL errors never leave the data layer.
 */
enum class AppError {
    NETWORK,
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    SAME_PASSWORD,
    RATE_LIMITED,
    SESSION_EXPIRED,
    NOT_CONFIGURED,
    USERNAME_TAKEN,
    UNIVERSITY_NOT_FOUND,
    PROFILE_LOCKED,
    INVALID_INPUT,
    NOT_FOUND,
    SERVER,
    UNKNOWN,
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

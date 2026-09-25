package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.repository.AuthRepository
import javax.inject.Inject

enum class AuthInputError { EMAIL_INVALID, PASSWORD_REQUIRED, PASSWORD_TOO_SHORT, PASSWORDS_DO_NOT_MATCH }

/**
 * Client-side checks that give instant feedback. Supabase Auth enforces its
 * own rules on the server; its answer always wins.
 */
object AuthInputValidator {
    const val MIN_PASSWORD_LENGTH = 8
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

    fun validateSignIn(email: String, password: String): Set<AuthInputError> = buildSet {
        if (!isValidEmail(email)) add(AuthInputError.EMAIL_INVALID)
        if (password.isEmpty()) add(AuthInputError.PASSWORD_REQUIRED)
    }

    fun validateNewPassword(password: String, confirmation: String): Set<AuthInputError> = buildSet {
        if (password.length < MIN_PASSWORD_LENGTH) add(AuthInputError.PASSWORD_TOO_SHORT)
        if (password != confirmation) add(AuthInputError.PASSWORDS_DO_NOT_MATCH)
    }

    fun validateSignUp(email: String, password: String, confirmation: String): Set<AuthInputError> =
        buildSet {
            if (!isValidEmail(email)) add(AuthInputError.EMAIL_INVALID)
            addAll(validateNewPassword(password, confirmation))
        }
}

private fun <T> AppResult<T>.toFormResult(): FormResult<T, AuthInputError> = when (this) {
    is AppResult.Success -> FormResult.Success(value)
    is AppResult.Failure -> FormResult.Failed(error)
}

class SignInUseCase @Inject constructor(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): FormResult<Unit, AuthInputError> {
        val errors = AuthInputValidator.validateSignIn(email, password)
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return authRepository.signIn(email, password).toFormResult()
    }
}

class SignUpUseCase @Inject constructor(private val authRepository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmation: String,
    ): FormResult<SignUpResult, AuthInputError> {
        val errors = AuthInputValidator.validateSignUp(email, password, confirmation)
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return authRepository.signUp(email, password).toFormResult()
    }
}

class SendPasswordResetUseCase @Inject constructor(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String): FormResult<Unit, AuthInputError> {
        if (!AuthInputValidator.isValidEmail(email)) return FormResult.Invalid(setOf(AuthInputError.EMAIL_INVALID))
        return authRepository.sendPasswordReset(email).toFormResult()
    }
}

class UpdatePasswordUseCase @Inject constructor(private val authRepository: AuthRepository) {
    suspend operator fun invoke(password: String, confirmation: String): FormResult<Unit, AuthInputError> {
        val errors = AuthInputValidator.validateNewPassword(password, confirmation)
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return authRepository.updatePassword(password).toFormResult()
    }
}

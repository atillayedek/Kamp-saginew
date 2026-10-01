package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.SignUpConsents
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.repository.AuthRepository
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

enum class AuthInputError {
    EMAIL_INVALID,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    PASSWORDS_DO_NOT_MATCH,
    BIRTH_DATE_REQUIRED,
    UNDERAGE,
    NOTICE_NOT_READ,
    TERMS_NOT_ACCEPTED,
}

/** The minimum age comes from the compliance settings; the birth date is not stored. */
object AgePolicy {
    fun age(birthDate: LocalDate, today: LocalDate): Int = Period.between(birthDate, today).years

    fun isOldEnough(birthDate: LocalDate, today: LocalDate, minAge: Int): Boolean =
        !birthDate.isAfter(today) && age(birthDate, today) >= minAge
}

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

/** What the sign-up form collected besides e-mail and password. */
data class SignUpForm(
    val birthDate: LocalDate?,
    val minAge: Int,
    val today: LocalDate,
    /** The privacy notice (aydınlatma) was shown and marked as read. */
    val noticeRead: Boolean,
    /** Terms of use and community rules accepted (never pre-ticked). */
    val termsAccepted: Boolean,
    val consents: (birthDate: LocalDate) -> SignUpConsents,
)

class SignUpUseCase @Inject constructor(private val authRepository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmation: String,
        form: SignUpForm,
    ): FormResult<SignUpResult, AuthInputError> {
        val errors = AuthInputValidator.validateSignUp(email, password, confirmation).toMutableSet()
        when {
            form.birthDate == null -> errors += AuthInputError.BIRTH_DATE_REQUIRED
            !AgePolicy.isOldEnough(form.birthDate, form.today, form.minAge) -> errors += AuthInputError.UNDERAGE
        }
        if (!form.noticeRead) errors += AuthInputError.NOTICE_NOT_READ
        if (!form.termsAccepted) errors += AuthInputError.TERMS_NOT_ACCEPTED
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return authRepository.signUp(email, password, form.consents(form.birthDate!!)).toFormResult()
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

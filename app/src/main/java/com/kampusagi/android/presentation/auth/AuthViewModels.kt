package com.kampusagi.android.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.usecase.AuthInputError
import com.kampusagi.android.domain.usecase.FormResult
import com.kampusagi.android.domain.usecase.SendPasswordResetUseCase
import com.kampusagi.android.domain.usecase.SignInUseCase
import com.kampusagi.android.domain.usecase.SignUpUseCase
import com.kampusagi.android.domain.usecase.UpdatePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Shared shape of every auth form: field errors, a server error and a busy flag. */
data class AuthFormState(
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val inputErrors: Set<AuthInputError> = emptySet(),
    val error: AppError? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class SignInViewModel @Inject constructor(private val signIn: SignInUseCase) : ViewModel() {
    var state by mutableStateOf(AuthFormState())
        private set

    fun onEmailChange(value: String) {
        state = state.copy(email = value, inputErrors = state.inputErrors - AuthInputError.EMAIL_INVALID, error = null)
    }

    fun onPasswordChange(value: String) {
        state = state.copy(password = value, inputErrors = state.inputErrors - AuthInputError.PASSWORD_REQUIRED, error = null)
    }

    /** On success the root screen switches away on its own (auth state changes). */
    fun submit() {
        if (state.isSubmitting) return
        state = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            state = when (val result = signIn(state.email, state.password)) {
                is FormResult.Invalid -> state.copy(isSubmitting = false, inputErrors = result.errors)
                is FormResult.Failed -> state.copy(isSubmitting = false, error = result.error)
                is FormResult.Success -> state.copy(isSubmitting = false)
            }
        }
    }
}

@HiltViewModel
class SignUpViewModel @Inject constructor(private val signUp: SignUpUseCase) : ViewModel() {
    var state by mutableStateOf(AuthFormState())
        private set

    /** Email the confirmation link was sent to; the screen navigates when set. */
    var verificationSentTo by mutableStateOf<String?>(null)
        private set

    /** The person confirms being 18+ and accepts the terms and privacy policy before an account is created. */
    var acceptedTerms by mutableStateOf(false)
        private set

    val termsUrl: String = AppConfig.termsUrl
    val privacyPolicyUrl: String = AppConfig.privacyPolicyUrl

    fun onAcceptedTermsChange(value: Boolean) {
        acceptedTerms = value
    }

    fun onEmailChange(value: String) {
        state = state.copy(email = value, inputErrors = state.inputErrors - AuthInputError.EMAIL_INVALID, error = null)
    }

    fun onPasswordChange(value: String) {
        state = state.copy(password = value, inputErrors = state.inputErrors - AuthInputError.PASSWORD_TOO_SHORT, error = null)
    }

    fun onConfirmationChange(value: String) {
        state = state.copy(
            confirmation = value,
            inputErrors = state.inputErrors - AuthInputError.PASSWORDS_DO_NOT_MATCH,
            error = null,
        )
    }

    fun submit() {
        if (state.isSubmitting || !acceptedTerms) return
        state = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val result = signUp(state.email, state.password, state.confirmation)) {
                is FormResult.Invalid -> state = state.copy(isSubmitting = false, inputErrors = result.errors)
                is FormResult.Failed -> state = state.copy(isSubmitting = false, error = result.error)
                is FormResult.Success -> {
                    state = state.copy(isSubmitting = false)
                    if (result.value == SignUpResult.VERIFICATION_REQUIRED) verificationSentTo = state.email.trim()
                }
            }
        }
    }

    fun onVerificationScreenShown() {
        verificationSentTo = null
    }
}

data class VerifyEmailState(
    val isSending: Boolean = false,
    val resent: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    var state by mutableStateOf(VerifyEmailState())
        private set

    fun resend(email: String) {
        if (state.isSending) return
        state = VerifyEmailState(isSending = true)
        viewModelScope.launch {
            state = when (val result = authRepository.resendVerificationEmail(email)) {
                is AppResult.Success -> VerifyEmailState(resent = true)
                is AppResult.Failure -> VerifyEmailState(error = result.error)
            }
        }
    }
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(private val sendReset: SendPasswordResetUseCase) : ViewModel() {
    var state by mutableStateOf(AuthFormState())
        private set

    var sent by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        state = state.copy(email = value, inputErrors = emptySet(), error = null)
    }

    fun submit() {
        if (state.isSubmitting) return
        state = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val result = sendReset(state.email)) {
                is FormResult.Invalid -> state = state.copy(isSubmitting = false, inputErrors = result.errors)
                is FormResult.Failed -> state = state.copy(isSubmitting = false, error = result.error)
                is FormResult.Success -> {
                    state = state.copy(isSubmitting = false)
                    sent = true
                }
            }
        }
    }
}

@HiltViewModel
class PasswordRecoveryViewModel @Inject constructor(
    private val updatePassword: UpdatePasswordUseCase,
) : ViewModel() {
    var state by mutableStateOf(AuthFormState())
        private set

    var completed by mutableStateOf(false)
        private set

    fun onPasswordChange(value: String) {
        state = state.copy(password = value, inputErrors = state.inputErrors - AuthInputError.PASSWORD_TOO_SHORT, error = null)
    }

    fun onConfirmationChange(value: String) {
        state = state.copy(
            confirmation = value,
            inputErrors = state.inputErrors - AuthInputError.PASSWORDS_DO_NOT_MATCH,
            error = null,
        )
    }

    fun submit() {
        if (state.isSubmitting) return
        state = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            when (val result = updatePassword(state.password, state.confirmation)) {
                is FormResult.Invalid -> state = state.copy(isSubmitting = false, inputErrors = result.errors)
                is FormResult.Failed -> state = state.copy(isSubmitting = false, error = result.error)
                is FormResult.Success -> {
                    state = state.copy(isSubmitting = false)
                    completed = true
                }
            }
        }
    }
}

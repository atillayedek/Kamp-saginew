package com.kampusagi.android.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.ComplianceConfig
import com.kampusagi.android.domain.model.LegalDocTypes
import com.kampusagi.android.domain.model.LegalDocumentInfo
import com.kampusagi.android.domain.model.LegalKind
import com.kampusagi.android.domain.model.SignUpConsents
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ComplianceRepository
import com.kampusagi.android.domain.usecase.AuthInputError
import com.kampusagi.android.domain.usecase.FormResult
import com.kampusagi.android.domain.usecase.SendPasswordResetUseCase
import com.kampusagi.android.domain.usecase.SignInUseCase
import com.kampusagi.android.domain.usecase.SignUpForm
import com.kampusagi.android.domain.usecase.SignUpUseCase
import com.kampusagi.android.domain.usecase.UpdatePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
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
class SignInViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val compliance: ComplianceRepository,
) : ViewModel() {
    var state by mutableStateOf(AuthFormState())
        private set

    /** The log notice under the sign-in button, from the compliance settings (null until loaded). */
    var loginNotice by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            when (val config = compliance.config()) {
                is AppResult.Success -> loginNotice = config.value.loginLogNotice
                is AppResult.Failure -> Log.w(TAG, "Compliance settings not loaded: ${config.error}")
            }
        }
    }

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
            val email = state.email
            state = when (val result = signIn(email, state.password)) {
                is FormResult.Invalid -> state.copy(isSubmitting = false, inputErrors = result.errors)
                is FormResult.Failed -> {
                    // 5651: failed sign-ins are kept with the IP (the address only as a hash).
                    if (result.error == AppError.INVALID_CREDENTIALS) {
                        val logged = compliance.logFailedSignIn(email)
                        if (logged is AppResult.Failure) Log.w(TAG, "Failed sign-in not logged: ${logged.error}")
                    }
                    state.copy(isSubmitting = false, error = result.error)
                }
                is FormResult.Success -> {
                    val logged = compliance.logSignIn()
                    if (logged is AppResult.Failure) Log.w(TAG, "Sign-in not logged: ${logged.error}")
                    state.copy(isSubmitting = false)
                }
            }
        }
    }

    private companion object {
        const val TAG = "SignIn"
    }
}

/** The sign-up form's legal part, loaded from the database (texts, versions, notices, minimum age). */
sealed interface SignUpLegalState {
    data object Loading : SignUpLegalState
    data class Ready(val config: ComplianceConfig, val documents: List<LegalDocumentInfo>) : SignUpLegalState
    data class Failed(val error: AppError) : SignUpLegalState
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
    private val compliance: ComplianceRepository,
) : ViewModel() {
    var state by mutableStateOf(AuthFormState())
        private set

    /** Email the confirmation link was sent to; the screen navigates when set. */
    var verificationSentTo by mutableStateOf<String?>(null)
        private set

    var legal by mutableStateOf<SignUpLegalState>(SignUpLegalState.Loading)
        private set

    /** Used only for the age check; never stored. */
    var birthDate by mutableStateOf<LocalDate?>(null)
        private set

    /** "I have read the privacy notice" (aydınlatma): information, not consent. */
    var noticeRead by mutableStateOf(false)
        private set

    /** Terms of use and community rules; required, never pre-ticked. */
    var termsAccepted by mutableStateOf(false)
        private set

    /** Optional explicit consents by doc_type; each separate, all off by default, none required. */
    var optionalConsents by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    init {
        loadLegal()
    }

    fun loadLegal() {
        legal = SignUpLegalState.Loading
        viewModelScope.launch {
            val config = compliance.config()
            val documents = compliance.legalDocuments()
            legal = when {
                config is AppResult.Failure -> SignUpLegalState.Failed(config.error)
                documents is AppResult.Failure -> SignUpLegalState.Failed(documents.error)
                else -> SignUpLegalState.Ready(
                    (config as AppResult.Success).value,
                    (documents as AppResult.Success).value,
                )
            }
        }
    }

    val consentDocuments: List<LegalDocumentInfo>
        get() = (legal as? SignUpLegalState.Ready)?.documents.orEmpty().filter { it.kind == LegalKind.CONSENT }

    fun onBirthDateChange(value: LocalDate?) {
        birthDate = value
        state = state.copy(inputErrors = state.inputErrors - AuthInputError.BIRTH_DATE_REQUIRED - AuthInputError.UNDERAGE)
    }

    fun onNoticeReadChange(value: Boolean) {
        noticeRead = value
        state = state.copy(inputErrors = state.inputErrors - AuthInputError.NOTICE_NOT_READ)
    }

    fun onTermsAcceptedChange(value: Boolean) {
        termsAccepted = value
        state = state.copy(inputErrors = state.inputErrors - AuthInputError.TERMS_NOT_ACCEPTED)
    }

    fun onConsentChange(docType: String, granted: Boolean) {
        optionalConsents = optionalConsents + (docType to granted)
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
        val ready = legal as? SignUpLegalState.Ready ?: return
        if (state.isSubmitting) return
        state = state.copy(isSubmitting = true, error = null)
        val versions = ready.documents.associate { it.docType to it.version }
        val form = SignUpForm(
            birthDate = birthDate,
            minAge = ready.config.minAge,
            today = LocalDate.now(ISTANBUL),
            noticeRead = noticeRead,
            termsAccepted = termsAccepted,
        ) { birth ->
            SignUpConsents(
                birthDate = birth,
                accepted = ready.documents.filter { it.kind == LegalKind.AGREEMENT }.associate { it.docType to it.version },
                // The privacy notice was opened from the form and marked as read; the privacy policy is linked next to it.
                informed = listOf(LegalDocTypes.PRIVACY_NOTICE, LegalDocTypes.PRIVACY_POLICY)
                    .mapNotNull { type -> versions[type]?.let { type to it } }
                    .toMap(),
                consents = ready.documents.filter { it.kind == LegalKind.CONSENT }
                    .associate { it.docType to (it.version to (optionalConsents[it.docType] == true)) },
            )
        }
        viewModelScope.launch {
            when (val result = signUp(state.email, state.password, state.confirmation, form)) {
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

    private companion object {
        val ISTANBUL: ZoneId = ZoneId.of("Europe/Istanbul")
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

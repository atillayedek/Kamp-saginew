package com.kampusagi.android.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AccountGate
import com.kampusagi.android.domain.model.AccountGateState
import com.kampusagi.android.domain.model.AccountStatus
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthRedirectResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ComplianceRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import com.kampusagi.android.domain.repository.PushRepository
import android.util.Log
import com.kampusagi.android.data.crash.CrashReporter
import com.kampusagi.android.presentation.common.avatar.AvatarLoader
import com.kampusagi.android.presentation.common.media.PostPhotoLoader
import com.kampusagi.android.presentation.notification.BackgroundNotifier
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RootDestination {
    LOADING,
    SETUP_REQUIRED,
    AUTH,
    PASSWORD_RECOVERY,
    PROFILE_ERROR,
    PROFILE_SETUP,
    ACCOUNT_STATUS,
    /** The KVKK checks after sign-in could not be loaded. */
    GATE_ERROR,
    /** The person asked for their account to be deleted; they can cancel until the date. */
    DELETION_PENDING,
    /** A changed agreement must be accepted (or a changed notice read) before continuing. */
    LEGAL_UPDATE,
    MAIN,
}

data class AppUiState(
    val destination: RootDestination = RootDestination.LOADING,
    val profile: Profile? = null,
    val profileError: AppError? = null,
    val isAdmin: Boolean = false,
    val gate: AccountGate? = null,
) {
    val isLoading: Boolean get() = destination == RootDestination.LOADING
}

enum class AppMessage { EMAIL_CONFIRMED, LINK_EXPIRED, NETWORK_ERROR, GENERIC_ERROR, PASSWORD_UPDATED, SIGN_OUT_FAILED }

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val backgroundNotifier: BackgroundNotifier,
    private val pushRepository: PushRepository,
    private val crashReporter: CrashReporter,
    private val compliance: ComplianceRepository,
    private val avatarLoader: AvatarLoader,
    private val photoLoader: PostPhotoLoader,
) : ViewModel() {

    private val passwordRecovery = MutableStateFlow(false)
    private val editingProfile = MutableStateFlow(false)

    /** Set when the app was opened from a system notification; the main screen shows the list. */
    private val openNotifications = MutableStateFlow(false)
    val openNotificationsRequested: StateFlow<Boolean> = openNotifications.asStateFlow()
    private val messageChannel = Channel<AppMessage>(Channel.BUFFERED)
    val messages: Flow<AppMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<AppUiState> = combine(
        authRepository.authState,
        profileRepository.profileState,
        passwordRecovery,
        editingProfile,
        compliance.accountGate,
    ) { auth, profileState, recovering, editing, gate ->
        resolve(auth, profileState, recovering, editing, gate)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    private fun resolve(
        auth: AuthState,
        profileState: ProfileState?,
        recovering: Boolean,
        editing: Boolean,
        gateState: AccountGateState?,
    ): AppUiState {
        if (!authRepository.isBackendConfigured) return AppUiState(RootDestination.SETUP_REQUIRED)
        return when (auth) {
            AuthState.Loading -> AppUiState(RootDestination.LOADING)
            AuthState.SignedOut -> AppUiState(RootDestination.AUTH)
            is AuthState.SignedIn -> when {
                recovering -> AppUiState(RootDestination.PASSWORD_RECOVERY)
                gateState == null || gateState is AccountGateState.Loading -> AppUiState(RootDestination.LOADING)
                gateState is AccountGateState.Failed ->
                    AppUiState(RootDestination.GATE_ERROR, profileError = gateState.error, isAdmin = auth.isAdmin)
                gateState is AccountGateState.Ready && gateState.gate.deletionScheduledFor != null ->
                    AppUiState(RootDestination.DELETION_PENDING, gate = gateState.gate)
                gateState is AccountGateState.Ready && gateState.gate.pending.isNotEmpty() ->
                    AppUiState(RootDestination.LEGAL_UPDATE, gate = gateState.gate, isAdmin = auth.isAdmin)
                profileState == null || profileState is ProfileState.Loading -> AppUiState(RootDestination.LOADING)
                profileState is ProfileState.Failed ->
                    AppUiState(RootDestination.PROFILE_ERROR, profileError = profileState.error, isAdmin = auth.isAdmin)
                profileState is ProfileState.Loaded -> {
                    val profile = profileState.profile
                    val canEdit = profile.status == AccountStatus.PROFILE_INCOMPLETE ||
                        profile.status == AccountStatus.DOCUMENT_REQUIRED
                    val destination = when {
                        canEdit && (editing || profile.status == AccountStatus.PROFILE_INCOMPLETE) ->
                            RootDestination.PROFILE_SETUP
                        profile.status == AccountStatus.APPROVED -> RootDestination.MAIN
                        else -> RootDestination.ACCOUNT_STATUS
                    }
                    AppUiState(destination, profile = profile, isAdmin = auth.isAdmin)
                }
                else -> AppUiState(RootDestination.LOADING)
            }
        }
    }

    init {
        // Every signed-in person hears about new notifications while the app runs
        // (verification results arrive before approval).
        viewModelScope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        compliance.refreshAccountGate()
                        // With FCM the system delivers notifications even when the app is closed;
                        // the Realtime notifier is only the fallback for builds without Firebase.
                        if (pushRepository.isConfigured) {
                            val result = pushRepository.registerCurrentDevice()
                            if (result is AppResult.Failure) Log.w(TAG, "Push registration failed: ${result.error}")
                        } else {
                            backgroundNotifier.start()
                        }
                        // Crashes recorded earlier on this device are sent under the signed-in account.
                        crashReporter.sendPending()
                    } else {
                        compliance.clearAccountGate()
                        backgroundNotifier.stop()
                        avatarLoader.clear()
                        photoLoader.clear()
                    }
                }
        }
    }

    fun onOpenNotificationsIntent() {
        openNotifications.value = true
    }

    fun onNotificationsOpened() {
        openNotifications.value = false
    }

    fun onAuthLink(url: String) {
        viewModelScope.launch {
            when (val result = authRepository.handleAuthRedirect(url)) {
                AuthRedirectResult.NotAnAuthLink -> Unit
                AuthRedirectResult.SignedIn -> messageChannel.send(AppMessage.EMAIL_CONFIRMED)
                AuthRedirectResult.PasswordRecovery -> passwordRecovery.value = true
                AuthRedirectResult.LinkExpired -> messageChannel.send(AppMessage.LINK_EXPIRED)
                is AuthRedirectResult.Failed -> messageChannel.send(
                    if (result.error == AppError.NETWORK) AppMessage.NETWORK_ERROR else AppMessage.GENERIC_ERROR,
                )
            }
        }
    }

    fun onPasswordRecoveryFinished(updated: Boolean) {
        passwordRecovery.value = false
        if (updated) viewModelScope.launch { messageChannel.send(AppMessage.PASSWORD_UPDATED) }
    }

    fun retryProfile() {
        viewModelScope.launch { profileRepository.refresh() }
    }

    fun editProfile() {
        editingProfile.value = true
    }

    fun onProfileEditFinished() {
        editingProfile.value = false
    }

    fun retryGate() {
        viewModelScope.launch { compliance.refreshAccountGate() }
    }

    fun cancelDeletion() {
        viewModelScope.launch {
            when (val result = compliance.cancelDeletion()) {
                is AppResult.Success -> compliance.refreshAccountGate()
                is AppResult.Failure -> messageChannel.send(
                    if (result.error == AppError.NETWORK) AppMessage.NETWORK_ERROR else AppMessage.GENERIC_ERROR,
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            editingProfile.value = false
            passwordRecovery.value = false
            // This device must stop receiving the account's notifications.
            val unregistered = pushRepository.unregisterCurrentDevice()
            if (unregistered is AppResult.Failure) Log.w(TAG, "Push token not removed: ${unregistered.error}")
            // 5651: the sign-out is recorded while the session still exists.
            val logged = compliance.logSignOut()
            if (logged is AppResult.Failure) Log.w(TAG, "Sign-out not logged: ${logged.error}")
            if (authRepository.signOut() is AppResult.Failure) messageChannel.send(AppMessage.SIGN_OUT_FAILED)
        }
    }

    private companion object {
        const val TAG = "Root"
    }
}

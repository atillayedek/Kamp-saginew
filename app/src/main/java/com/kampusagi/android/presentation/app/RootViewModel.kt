package com.kampusagi.android.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AccountStatus
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthRedirectResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import com.kampusagi.android.domain.repository.PushRepository
import android.util.Log
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
    ADMIN_REVIEW,
    MAIN,
}

data class AppUiState(
    val destination: RootDestination = RootDestination.LOADING,
    val profile: Profile? = null,
    val profileError: AppError? = null,
    val isAdmin: Boolean = false,
) {
    val isLoading: Boolean get() = destination == RootDestination.LOADING
}

enum class AppMessage { EMAIL_CONFIRMED, LINK_EXPIRED, NETWORK_ERROR, GENERIC_ERROR, PASSWORD_UPDATED, SIGN_OUT_FAILED }

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val pushRepository: PushRepository,
) : ViewModel() {

    private val passwordRecovery = MutableStateFlow(false)
    private val editingProfile = MutableStateFlow(false)
    private val adminOpen = MutableStateFlow(false)

    /** Set when the app was opened from a push notification; the main screen shows the list. */
    private val openNotifications = MutableStateFlow(false)
    val openNotificationsRequested: StateFlow<Boolean> = openNotifications.asStateFlow()
    private val messageChannel = Channel<AppMessage>(Channel.BUFFERED)
    val messages: Flow<AppMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<AppUiState> = combine(
        authRepository.authState,
        profileRepository.profileState,
        passwordRecovery,
        editingProfile,
        adminOpen,
    ) { auth, profileState, recovering, editing, admin ->
        resolve(auth, profileState, recovering, editing, admin)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    private fun resolve(
        auth: AuthState,
        profileState: ProfileState?,
        recovering: Boolean,
        editing: Boolean,
        admin: Boolean,
    ): AppUiState {
        if (!authRepository.isBackendConfigured) return AppUiState(RootDestination.SETUP_REQUIRED)
        return when (auth) {
            AuthState.Loading -> AppUiState(RootDestination.LOADING)
            AuthState.SignedOut -> AppUiState(RootDestination.AUTH)
            is AuthState.SignedIn -> when {
                recovering -> AppUiState(RootDestination.PASSWORD_RECOVERY)
                // Admins do not need to be verified students to review requests.
                admin && auth.isAdmin -> AppUiState(RootDestination.ADMIN_REVIEW, isAdmin = true)
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
        // Every signed-in device registers for push (verification results arrive before approval).
        viewModelScope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null && pushRepository.isConfigured) {
                        val result = pushRepository.registerCurrentDevice()
                        if (result is AppResult.Failure) Log.w(TAG, "Push registration failed: ${result.error}")
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

    fun openAdminReview() {
        adminOpen.value = true
    }

    fun closeAdminReview() {
        adminOpen.value = false
    }

    fun signOut() {
        viewModelScope.launch {
            adminOpen.value = false
            editingProfile.value = false
            passwordRecovery.value = false
            // This device must stop receiving the account's notifications.
            val unregistered = pushRepository.unregisterCurrentDevice()
            if (unregistered is AppResult.Failure) Log.w(TAG, "Push token not removed: ${unregistered.error}")
            if (authRepository.signOut() is AppResult.Failure) messageChannel.send(AppMessage.SIGN_OUT_FAILED)
        }
    }

    private companion object {
        const val TAG = "Root"
    }
}

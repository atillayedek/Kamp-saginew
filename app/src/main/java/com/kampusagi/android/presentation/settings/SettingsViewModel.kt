package com.kampusagi.android.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.BlockedUser
import com.kampusagi.android.domain.repository.AccountRepository
import com.kampusagi.android.domain.repository.ModerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface BlockedListState {
    data object Loading : BlockedListState
    data class Loaded(val users: List<BlockedUser>) : BlockedListState
    data class Failed(val error: AppError) : BlockedListState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val moderation: ModerationRepository,
) : ViewModel() {

    var blocked by mutableStateOf<BlockedListState>(BlockedListState.Loading)
        private set

    var unblockingId by mutableStateOf<String?>(null)
        private set

    var actionError by mutableStateOf<AppError?>(null)
        private set

    /** Empty when this build was made without a privacy policy URL. */
    val privacyPolicyUrl: String = AppConfig.privacyPolicyUrl

    init {
        loadBlocked()
    }

    fun loadBlocked() {
        blocked = BlockedListState.Loading
        viewModelScope.launch {
            blocked = when (val result = moderation.blockedUsers()) {
                is AppResult.Success -> BlockedListState.Loaded(result.value)
                is AppResult.Failure -> BlockedListState.Failed(result.error)
            }
        }
    }

    fun unblock(user: BlockedUser) {
        if (unblockingId != null) return
        unblockingId = user.userId
        actionError = null
        viewModelScope.launch {
            when (val result = moderation.unblock(user.userId)) {
                is AppResult.Success -> {
                    val current = blocked as? BlockedListState.Loaded
                    if (current != null) blocked = BlockedListState.Loaded(current.users.filterNot { it.userId == user.userId })
                }
                is AppResult.Failure -> actionError = result.error
            }
            unblockingId = null
        }
    }
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val account: AccountRepository,
) : ViewModel() {

    var confirming by mutableStateOf(false)
        private set

    var deleting by mutableStateOf(false)
        private set

    var error by mutableStateOf<AppError?>(null)
        private set

    fun requestDelete() {
        error = null
        confirming = true
    }

    fun cancel() {
        confirming = false
    }

    /** On success the session ends and the app returns to the sign-in screen by itself. */
    fun confirm() {
        if (deleting) return
        confirming = false
        deleting = true
        error = null
        viewModelScope.launch {
            val result = account.deleteAccount()
            if (result is AppResult.Failure) error = result.error
            deleting = false
        }
    }
}

sealed interface ConsentState {
    data object Loading : ConsentState
    data class Loaded(val optIn: Boolean, val saving: Boolean = false, val error: AppError? = null) : ConsentState
    data class Failed(val error: AppError) : ConsentState
}

/** Marketing e-mail consent (Turkish law 6563 / KVKK): off until the person turns it on. */
@HiltViewModel
class MarketingConsentViewModel @Inject constructor(
    private val account: AccountRepository,
) : ViewModel() {

    var state by mutableStateOf<ConsentState>(ConsentState.Loading)
        private set

    init {
        load()
    }

    fun load() {
        state = ConsentState.Loading
        viewModelScope.launch {
            state = when (val result = account.marketingConsent()) {
                is AppResult.Success -> ConsentState.Loaded(result.value)
                is AppResult.Failure -> ConsentState.Failed(result.error)
            }
        }
    }

    fun set(optIn: Boolean) {
        val current = state as? ConsentState.Loaded ?: return
        if (current.saving) return
        state = current.copy(saving = true, error = null)
        viewModelScope.launch {
            state = when (val result = account.setMarketingConsent(optIn)) {
                is AppResult.Success -> ConsentState.Loaded(optIn)
                is AppResult.Failure -> current.copy(saving = false, error = result.error)
            }
        }
    }
}

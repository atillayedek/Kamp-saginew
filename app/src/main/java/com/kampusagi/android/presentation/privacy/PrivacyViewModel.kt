package com.kampusagi.android.presentation.privacy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AccessLogEntry
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.ConsentEvent
import com.kampusagi.android.domain.model.ConsentStatus
import com.kampusagi.android.domain.model.DataExport
import com.kampusagi.android.domain.model.DataRequestType
import com.kampusagi.android.domain.model.DataSubjectRequest
import com.kampusagi.android.domain.model.LegalDocumentInfo
import com.kampusagi.android.domain.model.ModerationDecision
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.repository.ComplianceRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Values for [com.kampusagi.android.presentation.main.PrivacyRoute.section]. */
object PrivacySections {
    const val REQUESTS = "requests"
    const val DECISIONS = "decisions"
}

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Loaded<T>(val value: T) : Loadable<T>
    data class Failed(val error: AppError) : Loadable<Nothing>
}

private fun <T> AppResult<T>.toLoadable(): Loadable<T> = when (this) {
    is AppResult.Success -> Loadable.Loaded(value)
    is AppResult.Failure -> Loadable.Failed(error)
}

/**
 * Settings → Privacy and KVKK: consents (withdrawal takes effect at once), surname visibility,
 * data export, KVKK applications, own access records, moderation decisions with appeals, the
 * legal texts and account deletion.
 */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val compliance: ComplianceRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    /** Section to scroll to when opened from a notification. */
    val initialSection: String? = savedStateHandle["section"]

    var consents by mutableStateOf<Loadable<List<ConsentStatus>>>(Loadable.Loading)
        private set
    var history by mutableStateOf<Loadable<List<ConsentEvent>>?>(null)
        private set
    var requests by mutableStateOf<Loadable<List<DataSubjectRequest>>>(Loadable.Loading)
        private set
    var accessLogs by mutableStateOf<Loadable<List<AccessLogEntry>>?>(null)
        private set
    var decisions by mutableStateOf<Loadable<List<ModerationDecision>>>(Loadable.Loading)
        private set
    var documents by mutableStateOf<Loadable<List<LegalDocumentInfo>>>(Loadable.Loading)
        private set

    var export by mutableStateOf<DataExport?>(null)
        private set
    var exporting by mutableStateOf(false)
        private set

    var requestType by mutableStateOf(DataRequestType.ACCESS)
        private set
    var requestDetails by mutableStateOf("")
        private set
    var submittedRequestNo by mutableStateOf<String?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set

    var appealing by mutableStateOf<ModerationDecision?>(null)
        private set
    var appealText by mutableStateOf("")
        private set

    var savingConsent by mutableStateOf<String?>(null)
        private set
    var savingName by mutableStateOf(false)
        private set

    /** Errors of the last action (sections show their own loading errors). */
    var actionError by mutableStateOf<AppError?>(null)
        private set

    /** The signed-in profile (for the surname switch); updates after the switch is saved. */
    val profileState: StateFlow<ProfileState?> = profiles.profileState

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { consents = compliance.consents().toLoadable() }
        viewModelScope.launch { requests = compliance.myRequests().toLoadable() }
        viewModelScope.launch { decisions = compliance.moderationDecisions().toLoadable() }
        viewModelScope.launch { documents = compliance.legalDocuments().toLoadable() }
    }

    fun setConsent(docType: String, granted: Boolean) {
        if (savingConsent != null) return
        savingConsent = docType
        actionError = null
        viewModelScope.launch {
            when (val result = compliance.setConsent(docType, granted)) {
                is AppResult.Success -> {
                    consents = compliance.consents().toLoadable()
                    if (history != null) history = compliance.consentHistory().toLoadable()
                }
                is AppResult.Failure -> actionError = result.error
            }
            savingConsent = null
        }
    }

    fun toggleHistory() {
        if (history != null) {
            history = null
            return
        }
        history = Loadable.Loading
        viewModelScope.launch { history = compliance.consentHistory().toLoadable() }
    }

    fun toggleAccessLogs() {
        if (accessLogs != null) {
            accessLogs = null
            return
        }
        accessLogs = Loadable.Loading
        viewModelScope.launch { accessLogs = compliance.accessLogs().toLoadable() }
    }

    fun setShowFullName(show: Boolean) {
        if (savingName) return
        savingName = true
        actionError = null
        viewModelScope.launch {
            when (val result = compliance.setShowFullName(show)) {
                is AppResult.Success -> profiles.refresh()
                is AppResult.Failure -> actionError = result.error
            }
            savingName = false
        }
    }

    fun exportData() {
        if (exporting) return
        exporting = true
        actionError = null
        viewModelScope.launch {
            when (val result = compliance.exportMyData()) {
                is AppResult.Success -> export = result.value
                is AppResult.Failure -> actionError = result.error
            }
            exporting = false
        }
    }

    fun onRequestTypeChange(type: DataRequestType) {
        requestType = type
    }

    fun onRequestDetailsChange(value: String) {
        if (value.length <= MAX_REQUEST_LENGTH) requestDetails = value
    }

    val canSubmitRequest: Boolean
        get() = !submitting && requestDetails.trim().length >= MIN_REQUEST_LENGTH

    fun submitRequest() {
        if (!canSubmitRequest) return
        submitting = true
        actionError = null
        viewModelScope.launch {
            when (val result = compliance.submitRequest(requestType, requestDetails)) {
                is AppResult.Success -> {
                    submittedRequestNo = result.value
                    requestDetails = ""
                    requests = compliance.myRequests().toLoadable()
                }
                is AppResult.Failure -> actionError = result.error
            }
            submitting = false
        }
    }

    fun startAppeal(decision: ModerationDecision) {
        appealing = decision
        appealText = ""
    }

    fun onAppealTextChange(value: String) {
        if (value.length <= MAX_APPEAL_LENGTH) appealText = value
    }

    fun cancelAppeal() {
        appealing = null
    }

    fun sendAppeal() {
        val decision = appealing ?: return
        if (appealText.trim().length < MIN_APPEAL_LENGTH) return
        actionError = null
        viewModelScope.launch {
            when (val result = compliance.appeal(decision.reportId, appealText)) {
                is AppResult.Success -> {
                    appealing = null
                    decisions = compliance.moderationDecisions().toLoadable()
                }
                is AppResult.Failure -> actionError = result.error
            }
        }
    }

    companion object {
        const val MIN_REQUEST_LENGTH = 10
        const val MAX_REQUEST_LENGTH = 4000
        const val MIN_APPEAL_LENGTH = 10
        const val MAX_APPEAL_LENGTH = 2000
    }
}

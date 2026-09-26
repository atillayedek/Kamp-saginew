package com.kampusagi.android.presentation.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.OpenReport
import com.kampusagi.android.domain.model.ReportAction
import com.kampusagi.android.domain.repository.ModerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface ReportListState {
    data object Loading : ReportListState
    data class Loaded(val reports: List<OpenReport>) : ReportListState
    data class Failed(val error: AppError) : ReportListState
}

/** A decision waiting for the admin's confirmation. */
data class PendingDecision(val report: OpenReport, val action: ReportAction)

@HiltViewModel
class AdminReportsViewModel @Inject constructor(
    private val moderation: ModerationRepository,
) : ViewModel() {

    var list by mutableStateOf<ReportListState>(ReportListState.Loading)
        private set

    var busyId by mutableStateOf<String?>(null)
        private set

    var actionError by mutableStateOf<AppError?>(null)
        private set

    var pending by mutableStateOf<PendingDecision?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        list = ReportListState.Loading
        viewModelScope.launch {
            list = when (val result = moderation.openReports()) {
                is AppResult.Success -> ReportListState.Loaded(result.value)
                is AppResult.Failure -> ReportListState.Failed(result.error)
            }
        }
    }

    fun request(report: OpenReport, action: ReportAction) {
        actionError = null
        pending = PendingDecision(report, action)
    }

    fun cancel() {
        pending = null
    }

    /** The server closes related reports too, so the queue is reloaded afterwards. */
    fun confirm() {
        val decision = pending ?: return
        pending = null
        if (busyId != null) return
        busyId = decision.report.id
        viewModelScope.launch {
            when (val result = moderation.resolve(decision.report.id, decision.action)) {
                is AppResult.Success -> when (val reload = moderation.openReports()) {
                    is AppResult.Success -> list = ReportListState.Loaded(reload.value)
                    is AppResult.Failure -> actionError = reload.error
                }
                is AppResult.Failure -> actionError = result.error
            }
            busyId = null
        }
    }
}

package com.kampusagi.android.presentation.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.PendingVerification
import com.kampusagi.android.domain.repository.AdminRepository
import com.kampusagi.android.domain.usecase.ReviewVerificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface PendingListState {
    data object Loading : PendingListState
    data class Loaded(val items: List<PendingVerification>) : PendingListState
    data class Failed(val error: AppError) : PendingListState
}

@HiltViewModel
class AdminReviewViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val reviewVerification: ReviewVerificationUseCase,
) : ViewModel() {

    var list by mutableStateOf<PendingListState>(PendingListState.Loading)
        private set

    /** Verification currently being opened, approved or rejected. */
    var busyId by mutableStateOf<String?>(null)
        private set

    var actionError by mutableStateOf<AppError?>(null)
        private set

    private val documents = Channel<File>(Channel.BUFFERED)
    val documentsToOpen: Flow<File> = documents.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        list = PendingListState.Loading
        viewModelScope.launch {
            list = when (val result = adminRepository.pendingVerifications()) {
                is AppResult.Success -> PendingListState.Loaded(result.value)
                is AppResult.Failure -> PendingListState.Failed(result.error)
            }
        }
    }

    fun openDocument(item: PendingVerification) = runAction(item.verificationId) {
        when (val result = adminRepository.downloadDocument(item.documentPath)) {
            is AppResult.Success -> {
                documents.send(result.value)
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun approve(item: PendingVerification) = runAction(item.verificationId) {
        decide(item, approve = true, reason = "")
    }

    fun reject(item: PendingVerification, reason: String) = runAction(item.verificationId) {
        decide(item, approve = false, reason = reason)
    }

    private suspend fun decide(item: PendingVerification, approve: Boolean, reason: String): AppError? =
        when (val result = reviewVerification(item.verificationId, approve, reason)) {
            is AppResult.Success -> {
                val current = list
                if (current is PendingListState.Loaded) {
                    list = PendingListState.Loaded(current.items.filterNot { it.verificationId == item.verificationId })
                }
                null
            }
            is AppResult.Failure -> {
                // Someone else already decided: show the fresh list.
                if (result.error == AppError.VERIFICATION_NOT_PENDING) refresh()
                result.error
            }
        }

    private fun runAction(id: String, action: suspend () -> AppError?) {
        if (busyId != null) return
        busyId = id
        actionError = null
        viewModelScope.launch {
            actionError = action()
            busyId = null
        }
    }
}

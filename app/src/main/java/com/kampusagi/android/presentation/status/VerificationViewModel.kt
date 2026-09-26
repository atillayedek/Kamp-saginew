package com.kampusagi.android.presentation.status

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Verification
import com.kampusagi.android.domain.repository.VerificationRepository
import com.kampusagi.android.domain.usecase.SubmitStudentDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface LatestVerificationState {
    data object Loading : LatestVerificationState
    data class Loaded(val verification: Verification?) : LatestVerificationState
    data class Failed(val error: AppError) : LatestVerificationState
}

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verificationRepository: VerificationRepository,
    private val submitDocument: SubmitStudentDocumentUseCase,
) : ViewModel() {

    var latest by mutableStateOf<LatestVerificationState>(LatestVerificationState.Loading)
        private set

    var isUploading by mutableStateOf(false)
        private set

    var uploadError by mutableStateOf<AppError?>(null)
        private set

    fun loadLatest() {
        latest = LatestVerificationState.Loading
        viewModelScope.launch {
            latest = when (val result = verificationRepository.latestVerification()) {
                is AppResult.Success -> LatestVerificationState.Loaded(result.value)
                is AppResult.Failure -> LatestVerificationState.Failed(result.error)
            }
        }
    }

    /** On success the profile refreshes to PENDING_REVIEW and the screen changes on its own. */
    fun onDocumentPicked(uri: String) {
        if (isUploading) return
        isUploading = true
        uploadError = null
        viewModelScope.launch {
            when (val result = submitDocument(uri)) {
                is AppResult.Success -> loadLatest()
                is AppResult.Failure -> uploadError = result.error
            }
            isUploading = false
        }
    }
}

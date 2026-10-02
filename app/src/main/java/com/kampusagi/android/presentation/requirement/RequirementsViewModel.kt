package com.kampusagi.android.presentation.requirement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Requirement
import com.kampusagi.android.domain.repository.RequirementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface RequirementsState {
    data object Loading : RequirementsState
    data class Loaded(val items: List<Requirement>) : RequirementsState
    data class Failed(val error: AppError) : RequirementsState
}

@HiltViewModel
class RequirementsViewModel @Inject constructor(
    private val repository: RequirementRepository,
) : ViewModel() {

    var state by mutableStateOf<RequirementsState>(RequirementsState.Loading)
        private set

    var closingId by mutableStateOf<String?>(null)
        private set

    var actionError by mutableStateOf<AppError?>(null)
        private set

    init {
        load()
    }

    fun load() {
        if (state !is RequirementsState.Loaded) state = RequirementsState.Loading
        viewModelScope.launch {
            state = when (val result = repository.myRequirements()) {
                is AppResult.Success -> RequirementsState.Loaded(result.value)
                is AppResult.Failure -> if (state is RequirementsState.Loaded) {
                    actionError = result.error
                    state
                } else {
                    RequirementsState.Failed(result.error)
                }
            }
        }
    }

    fun close(requirement: Requirement) {
        if (closingId != null) return
        closingId = requirement.id
        actionError = null
        viewModelScope.launch {
            when (val result = repository.close(requirement.id)) {
                is AppResult.Success -> load()
                is AppResult.Failure -> actionError = result.error
            }
            closingId = null
        }
    }
}

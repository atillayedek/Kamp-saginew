package com.kampusagi.android.presentation.requirement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.domain.repository.RequirementRepository
import com.kampusagi.android.presentation.main.MatchesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface MatchesState {
    data object Loading : MatchesState
    data class Loaded(val matches: List<Match>) : MatchesState
    data class Failed(val error: AppError) : MatchesState
}

@HiltViewModel
class MatchesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RequirementRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MatchesRoute>()
    val title: String = route.title

    var state by mutableStateOf<MatchesState>(MatchesState.Loading)
        private set

    init {
        load()
    }

    fun load() {
        state = MatchesState.Loading
        viewModelScope.launch {
            state = when (val result = repository.matches(route.requirementId)) {
                is AppResult.Success -> MatchesState.Loaded(result.value)
                is AppResult.Failure -> MatchesState.Failed(result.error)
            }
        }
    }
}

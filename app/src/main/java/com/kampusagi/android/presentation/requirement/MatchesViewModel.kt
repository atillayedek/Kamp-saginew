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
import com.kampusagi.android.domain.repository.ChatRepository
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
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MatchesRoute>()
    val title: String = route.title

    var state by mutableStateOf<MatchesState>(MatchesState.Loading)
        private set

    /** Conversation to open (id to title) once it exists on the server. */
    var openChat by mutableStateOf<Pair<String, String>?>(null)
        private set

    var startingChatWith by mutableStateOf<String?>(null)
        private set

    var chatError by mutableStateOf<AppError?>(null)
        private set

    init {
        load()
    }

    fun startChat(match: Match) {
        if (startingChatWith != null) return
        startingChatWith = match.owner.id
        chatError = null
        viewModelScope.launch {
            when (val result = chatRepository.startConversation(match.owner.id)) {
                is AppResult.Success -> openChat = result.value to (match.owner.fullName ?: match.owner.username.orEmpty())
                is AppResult.Failure -> chatError = result.error
            }
            startingChatWith = null
        }
    }

    fun onChatOpened() {
        openChat = null
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

package com.kampusagi.android.presentation.requirement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.domain.repository.ChatRepository
import com.kampusagi.android.domain.repository.ComplianceRepository
import com.kampusagi.android.domain.usecase.MatchGroup
import com.kampusagi.android.domain.usecase.MyMatchesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface MyMatchesState {
    data object Loading : MyMatchesState
    data class Loaded(val groups: List<MatchGroup>) : MyMatchesState
    data class Failed(val error: AppError) : MyMatchesState
}

@HiltViewModel
class MyMatchesViewModel @Inject constructor(
    private val myMatches: MyMatchesUseCase,
    private val chatRepository: ChatRepository,
    private val compliance: ComplianceRepository,
) : ViewModel() {

    var state by mutableStateOf<MyMatchesState>(MyMatchesState.Loading)
        private set

    var isRefreshing by mutableStateOf(false)
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

    /** KVKK md.11/1-g: objection to an automatically suggested match; it is not shown again. */
    fun objectTo(requirementId: String, match: Match, reason: String?) {
        chatError = null
        viewModelScope.launch {
            when (val result = compliance.objectToMatch(requirementId, match.requirementId, reason)) {
                is AppResult.Success -> (state as? MyMatchesState.Loaded)?.let { loaded ->
                    state = MyMatchesState.Loaded(
                        loaded.groups.map { group ->
                            if (group.requirement.id != requirementId) group
                            else group.copy(matches = group.matches.filterNot { it.requirementId == match.requirementId })
                        },
                    )
                }
                is AppResult.Failure -> chatError = result.error
            }
        }
    }

    fun load() {
        state = MyMatchesState.Loading
        viewModelScope.launch { state = fetch() }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            val next = fetch()
            // A failed refresh keeps the list on screen.
            if (next is MyMatchesState.Loaded || state !is MyMatchesState.Loaded) state = next
            isRefreshing = false
        }
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

    private suspend fun fetch(): MyMatchesState = when (val result = myMatches()) {
        is AppResult.Success -> MyMatchesState.Loaded(result.value)
        is AppResult.Failure -> MyMatchesState.Failed(result.error)
    }
}

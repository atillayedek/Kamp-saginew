package com.kampusagi.android.presentation.chat

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Conversation
import com.kampusagi.android.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface ConversationsState {
    data object Loading : ConversationsState
    data class Loaded(val conversations: List<Conversation>) : ConversationsState
    data class Failed(val error: AppError) : ConversationsState
}

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    var state by mutableStateOf<ConversationsState>(ConversationsState.Loading)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    /** Total unread messages, for the tab badge. */
    val unreadTotal: Int
        get() = (state as? ConversationsState.Loaded)?.conversations?.sumOf { it.unreadCount } ?: 0

    init {
        load()
        viewModelScope.launch {
            repository.changes(conversationId = null)
                .catch { Log.w(TAG, "Inbox realtime stopped; pull-to-refresh still works", it) }
                .collect { load() }
        }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        load(onDone = { isRefreshing = false })
    }

    fun load(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.conversations()
            state = when (result) {
                is AppResult.Success -> ConversationsState.Loaded(result.value)
                // A failed background refresh keeps the list on screen.
                is AppResult.Failure -> if (state is ConversationsState.Loaded) state else ConversationsState.Failed(result.error)
            }
            onDone()
        }
    }

    private companion object {
        const val TAG = "Conversations"
    }
}

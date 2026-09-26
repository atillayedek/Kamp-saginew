package com.kampusagi.android.presentation.chat

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.ChatMessage
import com.kampusagi.android.domain.model.MessageCursor
import com.kampusagi.android.domain.repository.ChatRepository
import com.kampusagi.android.presentation.main.ChatRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** A message the person typed that the server has not confirmed yet. */
data class OutgoingMessage(val id: String, val body: String, val failed: Boolean)

data class ChatState(
    /** Server messages, newest first. */
    val messages: List<ChatMessage> = emptyList(),
    /** Local messages waiting for the server, oldest first. */
    val outgoing: List<OutgoingMessage> = emptyList(),
    val loaded: Boolean = false,
    val error: AppError? = null,
    val olderAvailable: Boolean = false,
    val isLoadingOlder: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ChatRoute>()
    val title: String = route.title

    var state by mutableStateOf(ChatState())
        private set

    var draft by mutableStateOf("")
        private set

    init {
        refresh(markRead = true)
        viewModelScope.launch {
            repository.changes(route.conversationId)
                .catch { Log.w(TAG, "Chat realtime stopped; messages still load on open and send", it) }
                .collect { refresh(markRead = true) }
        }
    }

    fun onDraftChange(value: String) {
        if (value.length <= MAX_LENGTH) draft = value
    }

    fun retryLoad() = refresh(markRead = true)

    fun send() {
        val body = draft.trim()
        if (body.isEmpty()) return
        draft = ""
        val message = OutgoingMessage(UUID.randomUUID().toString(), body, failed = false)
        state = state.copy(outgoing = state.outgoing + message)
        deliver(message)
    }

    /** Retries with the same id, so the server never stores it twice. */
    fun retry(message: OutgoingMessage) {
        state = state.copy(outgoing = state.outgoing.map { if (it.id == message.id) it.copy(failed = false) else it })
        deliver(message)
    }

    fun loadOlder() {
        val oldest = state.messages.lastOrNull() ?: return
        if (state.isLoadingOlder || !state.olderAvailable) return
        state = state.copy(isLoadingOlder = true)
        viewModelScope.launch {
            state = when (val result = repository.messages(route.conversationId, MessageCursor(oldest.createdAt, oldest.id))) {
                is AppResult.Success -> {
                    val known = state.messages.map { it.id }.toSet()
                    state.copy(
                        messages = state.messages + result.value.filterNot { it.id in known },
                        olderAvailable = result.value.size >= PAGE_SIZE,
                        isLoadingOlder = false,
                    )
                }
                is AppResult.Failure -> state.copy(isLoadingOlder = false, error = result.error)
            }
        }
    }

    private fun deliver(message: OutgoingMessage) {
        viewModelScope.launch {
            when (val result = repository.send(route.conversationId, message.id, message.body)) {
                is AppResult.Success -> refresh(markRead = false)
                is AppResult.Failure -> state = state.copy(
                    outgoing = state.outgoing.map { if (it.id == message.id) it.copy(failed = true) else it },
                    error = result.error,
                )
            }
        }
    }

    /**
     * Reloads the newest page and merges it with older pages already loaded.
     * Confirmed outgoing messages move from [ChatState.outgoing] to the list.
     */
    private fun refresh(markRead: Boolean) {
        viewModelScope.launch {
            when (val result = repository.messages(route.conversationId, before = null)) {
                is AppResult.Success -> {
                    val newest = result.value
                    val newestIds = newest.map { it.id }.toSet()
                    state = state.copy(
                        messages = mergeNewestFirst(newest, state.messages),
                        outgoing = state.outgoing.filterNot { it.id in newestIds },
                        loaded = true,
                        error = null,
                        olderAvailable = if (state.loaded) state.olderAvailable else newest.size >= PAGE_SIZE,
                    )
                    if (markRead && newest.any { !it.isMine }) {
                        val read = repository.markRead(route.conversationId)
                        if (read is AppResult.Failure) Log.w(TAG, "Read receipt not sent: ${read.error}")
                    }
                }
                is AppResult.Failure -> state = state.copy(error = result.error)
            }
        }
    }

    companion object {
        const val MAX_LENGTH = 2000

        /**
         * Fresh server rows win (they carry the latest read receipts); order is by
         * server time, then id. Timestamps are compared as instants, not strings,
         * because PostgreSQL trims trailing zeros in fractional seconds.
         */
        fun mergeNewestFirst(fresh: List<ChatMessage>, known: List<ChatMessage>): List<ChatMessage> =
            (fresh + known)
                .distinctBy { it.id }
                .sortedWith(compareByDescending<ChatMessage> { instantOf(it.createdAt) }.thenByDescending { it.id })

        private fun instantOf(timestamp: String): Instant =
            try {
                OffsetDateTime.parse(timestamp).toInstant()
            } catch (e: DateTimeParseException) {
                Instant.EPOCH
            }

        private const val PAGE_SIZE = 50
        private const val TAG = "Chat"
    }
}

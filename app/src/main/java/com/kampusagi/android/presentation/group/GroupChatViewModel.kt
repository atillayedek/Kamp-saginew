package com.kampusagi.android.presentation.group

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.GroupDetail
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.model.GroupMessage
import com.kampusagi.android.domain.model.MessageCursor
import com.kampusagi.android.domain.model.NewGroupMessage
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.domain.repository.GroupRepository
import com.kampusagi.android.domain.repository.ImageEncoder
import com.kampusagi.android.domain.repository.ModerationRepository
import com.kampusagi.android.domain.usecase.NewPostValidator
import com.kampusagi.android.presentation.community.PickedPhoto
import com.kampusagi.android.presentation.main.GroupRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GroupChatState(
    val group: GroupDetail? = null,
    /** Newest first. */
    val messages: List<GroupMessage> = emptyList(),
    val loaded: Boolean = false,
    /** Error loading the group itself (shown full screen). */
    val loadError: AppError? = null,
    val olderAvailable: Boolean = false,
    val isLoadingOlder: Boolean = false,
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupRepository,
    private val community: CommunityRepository,
    private val moderation: ModerationRepository,
    private val imageEncoder: ImageEncoder,
) : ViewModel() {

    val groupId: String = savedStateHandle.toRoute<GroupRoute>().groupId

    var state by mutableStateOf(GroupChatState())
        private set

    var draft by mutableStateOf("")
        private set

    var photo by mutableStateOf<PickedPhoto?>(null)
        private set

    /** Options of a poll attached to the next message (channels only); null when there is none. */
    var pollOptions by mutableStateOf<List<String>?>(null)
        private set

    var isSending by mutableStateOf(false)
        private set

    var isWorking by mutableStateOf(false)
        private set

    /** Error of the last action (send, like, vote, join…), shown under the list. */
    var actionError by mutableStateOf<AppError?>(null)
        private set

    var reportSent by mutableStateOf(false)
        private set

    /** Kept after a failed send so a retry uses the same id and is never stored twice. */
    private var pendingMessageId: String? = null

    val canSend: Boolean
        get() = !isSending && state.group?.canPost == true &&
            (draft.isNotBlank() || photo != null) &&
            (pollOptions == null || (draft.isNotBlank() && NewPostValidator.isValidPollOptions(pollOptions.orEmpty())))

    init {
        load()
        viewModelScope.launch {
            repository.changes(groupId)
                .catch { Log.w(TAG, "Group realtime stopped; messages still load on open and send", it) }
                .collect { refreshMessages() }
        }
    }

    fun load() {
        state = state.copy(loadError = null)
        viewModelScope.launch {
            when (val result = repository.group(groupId)) {
                is AppResult.Success -> {
                    state = state.copy(group = result.value)
                    if (result.value.myRole != null) refreshMessages() else state = state.copy(loaded = true)
                }
                is AppResult.Failure -> state = state.copy(loadError = result.error)
            }
        }
    }

    fun join() = act {
        when (val result = repository.join(groupId)) {
            is AppResult.Success -> {
                reloadGroup()
                refreshMessages()
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun onDraftChange(value: String) {
        if (value.length <= MAX_LENGTH) draft = value
        actionError = null
    }

    fun onPhotoPicked(uri: String) {
        viewModelScope.launch {
            when (val result = imageEncoder.postPhotoJpeg(uri)) {
                is AppResult.Success -> {
                    val preview = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(result.value, 0, result.value.size)?.asImageBitmap()
                    }
                    if (preview != null) photo = PickedPhoto(result.value, preview) else actionError = AppError.IMAGE_UNREADABLE
                }
                is AppResult.Failure -> actionError = result.error
            }
        }
    }

    fun removePhoto() {
        photo = null
    }

    fun startPoll() {
        if (state.group?.kind == GroupKind.CHANNEL) pollOptions = listOf("", "")
    }

    fun cancelPoll() {
        pollOptions = null
    }

    fun onPollOptionChange(index: Int, value: String) {
        if (value.length <= NewPostValidator.MAX_POLL_OPTION_LENGTH) {
            pollOptions = pollOptions?.mapIndexed { i, old -> if (i == index) value else old }
        }
    }

    fun addPollOption() {
        val options = pollOptions ?: return
        if (options.size < NewPostValidator.MAX_POLL_OPTIONS) pollOptions = options + ""
    }

    fun removePollOption(index: Int) {
        val options = pollOptions ?: return
        if (options.size > NewPostValidator.MIN_POLL_OPTIONS) pollOptions = options.filterIndexed { i, _ -> i != index }
    }

    fun send() {
        if (!canSend) return
        isSending = true
        actionError = null
        val id = pendingMessageId ?: UUID.randomUUID().toString().also { pendingMessageId = it }
        val message = NewGroupMessage(body = draft.ifBlank { null }, photo = photo?.jpeg, pollOptions = pollOptions)
        viewModelScope.launch {
            when (val result = repository.send(groupId, id, message)) {
                is AppResult.Success -> {
                    pendingMessageId = null
                    draft = ""
                    photo = null
                    pollOptions = null
                    refreshMessages()
                }
                is AppResult.Failure -> actionError = result.error
            }
            isSending = false
        }
    }

    fun toggleLike(message: GroupMessage) {
        val liked = !message.likedByMe
        update(message.id) { it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            when (val result = repository.setLiked(message.id, liked)) {
                is AppResult.Success -> update(message.id) { it.copy(likedByMe = liked, likeCount = result.value) }
                is AppResult.Failure -> {
                    update(message.id) { it.copy(likedByMe = message.likedByMe, likeCount = message.likeCount) }
                    actionError = result.error
                }
            }
        }
    }

    fun vote(message: GroupMessage, optionId: String?) {
        val poll = message.poll ?: return
        viewModelScope.launch {
            when (val result = community.vote(poll.id, optionId)) {
                is AppResult.Success -> update(message.id) { it.copy(poll = result.value) }
                is AppResult.Failure -> actionError = result.error
            }
        }
    }

    fun delete(message: GroupMessage) = act {
        when (val result = repository.deleteMessage(message.id)) {
            is AppResult.Success -> {
                state = state.copy(messages = state.messages.filterNot { it.id == message.id })
                if (state.group?.pinnedMessageId == message.id) reloadGroup()
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun pin(message: GroupMessage?) = act {
        when (val result = repository.pin(groupId, message?.id)) {
            is AppResult.Success -> {
                reloadGroup()
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun report(message: GroupMessage, reason: ReportReason, details: String?) = act {
        reportSent = false
        when (val result = moderation.report(ReportTarget.GROUP_MESSAGE, message.id, reason, details)) {
            is AppResult.Success -> {
                reportSent = true
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun loadOlder() {
        val oldest = state.messages.lastOrNull() ?: return
        if (state.isLoadingOlder || !state.olderAvailable) return
        state = state.copy(isLoadingOlder = true)
        viewModelScope.launch {
            state = when (val result = repository.messages(groupId, MessageCursor(oldest.createdAt, oldest.id))) {
                is AppResult.Success -> {
                    val known = state.messages.map { it.id }.toSet()
                    state.copy(
                        messages = state.messages + result.value.filterNot { it.id in known },
                        olderAvailable = result.value.size >= PAGE_SIZE,
                        isLoadingOlder = false,
                    )
                }
                is AppResult.Failure -> {
                    actionError = result.error
                    state.copy(isLoadingOlder = false)
                }
            }
        }
    }

    private suspend fun reloadGroup() {
        when (val result = repository.group(groupId)) {
            is AppResult.Success -> state = state.copy(group = result.value)
            is AppResult.Failure -> actionError = result.error
        }
    }

    /** Reloads the newest page, keeps older pages already loaded and marks the group read. */
    private suspend fun refreshMessages() {
        if (state.group?.myRole == null) return
        when (val result = repository.messages(groupId, before = null)) {
            is AppResult.Success -> {
                val newest = result.value
                state = state.copy(
                    messages = mergeNewestFirst(newest, state.messages),
                    loaded = true,
                    olderAvailable = if (state.loaded) state.olderAvailable else newest.size >= PAGE_SIZE,
                )
                val read = repository.markRead(groupId)
                if (read is AppResult.Failure) Log.w(TAG, "Group not marked read: ${read.error}")
            }
            is AppResult.Failure -> {
                actionError = result.error
                state = state.copy(loaded = true)
            }
        }
    }

    private fun update(messageId: String, change: (GroupMessage) -> GroupMessage) {
        state = state.copy(messages = state.messages.map { if (it.id == messageId) change(it) else it })
    }

    private fun act(action: suspend () -> AppError?) {
        if (isWorking) return
        isWorking = true
        actionError = null
        viewModelScope.launch {
            actionError = action()
            isWorking = false
        }
    }

    companion object {
        const val MAX_LENGTH = 2000
        private const val PAGE_SIZE = 50
        private const val TAG = "GroupChat"

        /** Fresh rows win; ordered by server time as instants, then id. */
        fun mergeNewestFirst(fresh: List<GroupMessage>, known: List<GroupMessage>): List<GroupMessage> {
            // An empty newest page means the group has no messages left.
            if (fresh.isEmpty()) return emptyList()
            val freshIds = fresh.map { it.id }.toSet()
            // Messages missing from a fresh newest page that are newer than its oldest row were deleted.
            val oldestFresh = instantOf(fresh.last().createdAt)
            val kept = known.filter { it.id in freshIds || instantOf(it.createdAt) < oldestFresh }
            return (fresh + kept)
                .distinctBy { it.id }
                .sortedWith(compareByDescending<GroupMessage> { instantOf(it.createdAt) }.thenByDescending { it.id })
        }

        private fun instantOf(timestamp: String): Instant =
            try {
                OffsetDateTime.parse(timestamp).toInstant()
            } catch (e: DateTimeParseException) {
                Instant.EPOCH
            }
    }
}

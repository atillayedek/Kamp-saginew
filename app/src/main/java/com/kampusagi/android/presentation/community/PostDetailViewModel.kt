package com.kampusagi.android.presentation.community

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.Comment
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.repository.ChatRepository
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.domain.repository.ModerationRepository
import com.kampusagi.android.domain.usecase.AddCommentUseCase
import com.kampusagi.android.domain.usecase.PostTextValidator
import com.kampusagi.android.presentation.main.PostDetailRoute
import com.kampusagi.android.presentation.moderation.ReportRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class OpenedConversation(val id: String, val title: String)

sealed interface PostDetailState {
    data object Loading : PostDetailState
    data class Loaded(val post: Post, val comments: List<Comment>) : PostDetailState
    data class Failed(val error: AppError) : PostDetailState
}

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
    private val addComment: AddCommentUseCase,
    private val moderation: ModerationRepository,
    private val chat: ChatRepository,
    private val postChanges: PostChanges,
) : ViewModel() {

    private val postId = savedStateHandle.toRoute<PostDetailRoute>().postId

    var state by mutableStateOf<PostDetailState>(PostDetailState.Loading)
        private set

    var commentText by mutableStateOf("")
        private set

    var isWorking by mutableStateOf(false)
        private set

    var actionError by mutableStateOf<AppError?>(null)
        private set

    /** Set once the post was deleted, so the screen can close. */
    var deleted by mutableStateOf(false)
        private set

    var reportRequest by mutableStateOf<ReportRequest?>(null)
        private set

    var blockRequest by mutableStateOf<Author?>(null)
        private set

    /** Shown once a report was accepted. */
    var reportSent by mutableStateOf(false)
        private set

    /** Set when the post's author was blocked, so the screen can close and the feed drop their posts. */
    var blockedAuthorId by mutableStateOf<String?>(null)
        private set

    /** A conversation opened with the seller, for the screen to navigate to. */
    var openedConversation by mutableStateOf<OpenedConversation?>(null)
        private set

    val canSendComment: Boolean get() = !isWorking && PostTextValidator.isValidComment(commentText)

    val interactor = PostInteractor(repository, viewModelScope, ::updatePost) { actionError = it }

    init {
        load()
    }

    fun load() {
        state = PostDetailState.Loading
        viewModelScope.launch {
            val post = async { repository.post(postId) }
            val comments = async { repository.comments(postId) }
            state = when (val p = post.await()) {
                is AppResult.Failure -> PostDetailState.Failed(p.error)
                is AppResult.Success -> when (val c = comments.await()) {
                    is AppResult.Failure -> PostDetailState.Failed(c.error)
                    is AppResult.Success -> PostDetailState.Loaded(p.value, c.value)
                }
            }
        }
    }

    fun onCommentTextChange(value: String) {
        if (value.length <= PostTextValidator.MAX_COMMENT_LENGTH) commentText = value
        actionError = null
    }

    fun sendComment() = runAction {
        when (val result = addComment(postId, commentText)) {
            is AppResult.Success -> {
                commentText = ""
                reload()
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun deleteComment(comment: Comment) = runAction {
        when (val result = repository.deleteComment(comment.id)) {
            is AppResult.Success -> {
                reload()
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun deletePost() = runAction {
        when (val result = repository.deletePost(postId)) {
            is AppResult.Success -> {
                deleted = true
                postChanges.publish(PostChange.Deleted(postId, source = this))
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun requestReport(target: ReportTarget, id: String) {
        reportRequest = ReportRequest(target, id)
        reportSent = false
    }

    fun cancelReport() {
        reportRequest = null
    }

    fun submitReport(reason: ReportReason, details: String?) {
        val request = reportRequest ?: return
        runAction {
            when (val result = moderation.report(request.target, request.id, reason, details)) {
                is AppResult.Success -> {
                    reportRequest = null
                    reportSent = true
                    null
                }
                is AppResult.Failure -> {
                    reportRequest = null
                    result.error
                }
            }
        }
    }

    fun requestBlock(author: Author) {
        blockRequest = author
    }

    fun cancelBlock() {
        blockRequest = null
    }

    /** Blocking the post's author closes the post; blocking a commenter hides their comments. */
    fun confirmBlock() {
        val author = blockRequest ?: return
        blockRequest = null
        runAction {
            when (val result = moderation.block(author.id)) {
                is AppResult.Success -> {
                    postChanges.publish(PostChange.AuthorBlocked(author.id, source = this))
                    if ((state as? PostDetailState.Loaded)?.post?.author?.id == author.id) {
                        blockedAuthorId = author.id
                    } else {
                        reload()
                    }
                    null
                }
                is AppResult.Failure -> result.error
            }
        }
    }

    /** Opens (or creates) the conversation with the seller of a listing. */
    fun messageAuthor() {
        val post = (state as? PostDetailState.Loaded)?.post ?: return
        runAction {
            when (val result = chat.startConversation(post.author.id)) {
                is AppResult.Success -> {
                    openedConversation = OpenedConversation(result.value, post.author.fullName ?: post.author.username.orEmpty())
                    null
                }
                is AppResult.Failure -> result.error
            }
        }
    }

    fun onConversationOpened() {
        openedConversation = null
    }

    private fun updatePost(id: String, change: (Post) -> Post) {
        val loaded = state as? PostDetailState.Loaded ?: return
        if (loaded.post.id != id) return
        val changed = change(loaded.post)
        state = loaded.copy(post = changed)
        postChanges.publish(PostChange.Changed(changed, source = this))
    }

    /** Refreshes post counters and comments after a change, keeping the screen on failure. */
    private suspend fun reload() {
        val post = repository.post(postId)
        val comments = repository.comments(postId)
        if (post is AppResult.Success && comments is AppResult.Success) {
            state = PostDetailState.Loaded(post.value, comments.value)
            postChanges.publish(PostChange.Changed(post.value, source = this))
        } else {
            actionError = ((post as? AppResult.Failure) ?: (comments as? AppResult.Failure))?.error
        }
    }

    private fun runAction(action: suspend () -> AppError?) {
        if (isWorking) return
        isWorking = true
        actionError = null
        viewModelScope.launch {
            actionError = action()
            isWorking = false
        }
    }
}

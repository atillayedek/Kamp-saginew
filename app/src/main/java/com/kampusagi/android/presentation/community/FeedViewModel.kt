package com.kampusagi.android.presentation.community

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.FeedCursor
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class FeedState(
    val posts: List<Post> = emptyList(),
    val nextCursor: FeedCursor? = null,
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Error of the first page (shown full screen) or of loading more (shown inline). */
    val error: AppError? = null,
) {
    val endReached: Boolean get() = loaded && nextCursor == null
    val isEmpty: Boolean get() = loaded && posts.isEmpty()
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: CommunityRepository,
) : ViewModel() {

    var selectedScope by mutableStateOf(PostScope.GENERAL)
        private set

    private val feeds = mutableStateMapOf<PostScope, FeedState>()

    /** Transient error from a like that could not be saved. */
    var likeError by mutableStateOf<AppError?>(null)
        private set

    fun state(scope: PostScope): FeedState = feeds[scope] ?: FeedState()

    init {
        loadFirstPage(PostScope.GENERAL)
    }

    fun selectScope(scope: PostScope) {
        selectedScope = scope
        if (!state(scope).loaded && !state(scope).isLoading) loadFirstPage(scope)
    }

    fun retry() = loadFirstPage(selectedScope)

    fun refresh() {
        val scope = selectedScope
        val current = state(scope)
        if (current.isRefreshing || current.isLoading) return
        feeds[scope] = current.copy(isRefreshing = true)
        viewModelScope.launch { feeds[scope] = fetchFirst(scope, state(scope)).copy(isRefreshing = false) }
    }

    fun loadMore() {
        val scope = selectedScope
        val current = state(scope)
        val cursor = current.nextCursor ?: return
        if (current.isLoadingMore || current.isLoading || current.isRefreshing) return
        feeds[scope] = current.copy(isLoadingMore = true, error = null)
        viewModelScope.launch {
            feeds[scope] = when (val result = repository.feed(scope, cursor)) {
                is AppResult.Success -> {
                    val known = state(scope).posts.map { it.id }.toSet()
                    state(scope).copy(
                        posts = state(scope).posts + result.value.posts.filterNot { it.id in known },
                        nextCursor = result.value.nextCursor,
                        isLoadingMore = false,
                    )
                }
                is AppResult.Failure -> state(scope).copy(isLoadingMore = false, error = result.error)
            }
        }
    }

    /** Reloads the scope the new post was shared in, so it appears on top. */
    fun onPostCreated(scope: PostScope) {
        selectedScope = scope
        loadFirstPage(scope)
    }

    fun onPostChanged(post: Post) = updatePost(post.id) { post }

    fun onPostDeleted(postId: String) {
        PostScope.entries.forEach { scope ->
            val current = state(scope)
            feeds[scope] = current.copy(posts = current.posts.filterNot { it.id == postId })
        }
    }

    /** Optimistic: the heart changes at once and is rolled back if the server refuses. */
    fun toggleLike(post: Post) {
        val liked = !post.likedByMe
        val optimistic = post.copy(likedByMe = liked, likeCount = (post.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
        updatePost(post.id) { optimistic }
        likeError = null
        viewModelScope.launch {
            when (val result = repository.setLiked(post.id, liked)) {
                is AppResult.Success -> updatePost(post.id) { it.copy(likedByMe = liked, likeCount = result.value) }
                is AppResult.Failure -> {
                    updatePost(post.id) { it.copy(likedByMe = post.likedByMe, likeCount = post.likeCount) }
                    likeError = result.error
                }
            }
        }
    }

    fun dismissLikeError() {
        likeError = null
    }

    private fun loadFirstPage(scope: PostScope) {
        feeds[scope] = state(scope).copy(isLoading = true, error = null)
        viewModelScope.launch { feeds[scope] = fetchFirst(scope, state(scope)).copy(isLoading = false) }
    }

    private suspend fun fetchFirst(scope: PostScope, current: FeedState): FeedState =
        when (val result = repository.feed(scope, cursor = null)) {
            is AppResult.Success -> FeedState(
                posts = result.value.posts,
                nextCursor = result.value.nextCursor,
                loaded = true,
            )
            // Keep what is already on screen when a refresh fails.
            is AppResult.Failure -> current.copy(error = result.error)
        }

    private fun updatePost(postId: String, change: (Post) -> Post) {
        PostScope.entries.forEach { scope ->
            val current = state(scope)
            if (current.posts.any { it.id == postId }) {
                feeds[scope] = current.copy(posts = current.posts.map { if (it.id == postId) change(it) else it })
            }
        }
    }
}

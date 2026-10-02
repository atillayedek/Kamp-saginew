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
import com.kampusagi.android.domain.model.PostCategory
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
    private val postChanges: PostChanges,
) : ViewModel() {

    var selectedScope by mutableStateOf(PostScope.GENERAL)
        private set

    /** Category filter shared by both scopes; null shows every category. */
    var selectedCategory by mutableStateOf<PostCategory?>(null)
        private set

    private val feeds = mutableStateMapOf<PostScope, FeedState>()

    /** Transient error from a like, save, vote or attendance that could not be saved. */
    var actionError by mutableStateOf<AppError?>(null)
        private set

    val interactor = PostInteractor(
        repository = repository,
        scope = viewModelScope,
        update = ::updatePost,
        onError = { actionError = it },
    )

    fun state(scope: PostScope): FeedState = feeds[scope] ?: FeedState()

    init {
        loadFirstPage(PostScope.GENERAL)
        viewModelScope.launch {
            postChanges.changes.collect { change ->
                if (change.source === this@FeedViewModel) return@collect
                when (change) {
                    is PostChange.Changed -> replacePost(change.post.id) { change.post }
                    is PostChange.Deleted -> onPostDeleted(change.postId)
                    is PostChange.AuthorBlocked -> onAuthorBlocked(change.userId)
                }
            }
        }
    }

    fun selectScope(scope: PostScope) {
        selectedScope = scope
        if (!state(scope).loaded && !state(scope).isLoading) loadFirstPage(scope)
    }

    /** A new filter starts both feeds over; the selected one loads now, the other when opened. */
    fun selectCategory(category: PostCategory?) {
        if (category == selectedCategory) return
        selectedCategory = category
        feeds.clear()
        loadFirstPage(selectedScope)
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
            feeds[scope] = when (val result = repository.feed(scope, selectedCategory, cursor)) {
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

    /** Reloads the scope the new post was shared in, so it appears on top (the filter is cleared so it is visible). */
    fun onPostCreated(scope: PostScope) {
        selectedScope = scope
        if (selectedCategory != null) {
            selectedCategory = null
            feeds.clear()
        }
        loadFirstPage(scope)
    }

    fun onPostDeleted(postId: String) {
        PostScope.entries.forEach { scope ->
            val current = state(scope)
            feeds[scope] = current.copy(posts = current.posts.filterNot { it.id == postId })
        }
    }

    /** Drops a blocked person's posts at once; the server leaves them out of every later page. */
    fun onAuthorBlocked(userId: String) {
        PostScope.entries.forEach { scope ->
            val current = state(scope)
            feeds[scope] = current.copy(posts = current.posts.filterNot { it.author.id == userId })
        }
    }

    /** Optimistic: the heart changes at once and is rolled back if the server refuses. */
    fun toggleLike(post: Post) {
        actionError = null
        interactor.toggleLike(post)
    }

    fun dismissActionError() {
        actionError = null
    }

    private fun loadFirstPage(scope: PostScope) {
        feeds[scope] = state(scope).copy(isLoading = true, error = null)
        viewModelScope.launch { feeds[scope] = fetchFirst(scope, state(scope)).copy(isLoading = false) }
    }

    private suspend fun fetchFirst(scope: PostScope, current: FeedState): FeedState =
        when (val result = repository.feed(scope, selectedCategory, cursor = null)) {
            is AppResult.Success -> FeedState(
                posts = result.value.posts,
                nextCursor = result.value.nextCursor,
                loaded = true,
            )
            // Keep what is already on screen when a refresh fails.
            is AppResult.Failure -> current.copy(error = result.error)
        }

    private fun updatePost(postId: String, change: (Post) -> Post) {
        replacePost(postId, change)?.let { postChanges.publish(PostChange.Changed(it, source = this)) }
    }

    private fun replacePost(postId: String, change: (Post) -> Post): Post? {
        var changed: Post? = null
        PostScope.entries.forEach { scope ->
            val current = state(scope)
            if (current.posts.any { it.id == postId }) {
                feeds[scope] = current.copy(posts = current.posts.map { if (it.id == postId) change(it).also { new -> changed = new } else it })
            }
        }
        return changed
    }
}

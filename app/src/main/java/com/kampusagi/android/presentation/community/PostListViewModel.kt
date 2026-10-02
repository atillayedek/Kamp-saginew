package com.kampusagi.android.presentation.community

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.repository.CommunityRepository
import kotlinx.coroutines.launch

sealed interface PostListState {
    data object Loading : PostListState
    data class Loaded(val posts: List<Post>) : PostListState
    data class Failed(val error: AppError) : PostListState
}

/** A list of posts loaded in one call (saved posts, events), kept in step with changes elsewhere. */
abstract class PostListViewModel(
    repository: CommunityRepository,
    private val postChanges: PostChanges,
) : ViewModel() {

    var state by mutableStateOf<PostListState>(PostListState.Loading)
        private set

    var actionError by mutableStateOf<AppError?>(null)
        private set

    val interactor = PostInteractor(repository, viewModelScope, ::updatePost) { actionError = it }

    protected abstract suspend fun fetch(): AppResult<List<Post>>

    init {
        viewModelScope.launch {
            postChanges.changes.collect { change ->
                if (change.source === this@PostListViewModel) return@collect
                when (change) {
                    is PostChange.Changed -> replace(change.post.id) { change.post }
                    is PostChange.Deleted -> removeWhere { it.id == change.postId }
                    is PostChange.AuthorBlocked -> removeWhere { it.author.id == change.userId }
                }
            }
        }
    }

    fun load() {
        if (state !is PostListState.Loaded) state = PostListState.Loading
        actionError = null
        viewModelScope.launch {
            state = when (val result = fetch()) {
                is AppResult.Success -> PostListState.Loaded(result.value)
                is AppResult.Failure -> (state as? PostListState.Loaded)?.also { actionError = result.error }
                    ?: PostListState.Failed(result.error)
            }
        }
    }

    /** Adds a further page, skipping posts already shown. */
    protected fun append(posts: List<Post>) {
        val loaded = state as? PostListState.Loaded ?: return
        val known = loaded.posts.map { it.id }.toSet()
        state = loaded.copy(posts = loaded.posts + posts.filterNot { it.id in known })
    }

    protected fun reportError(error: AppError) {
        actionError = error
    }

    private fun updatePost(postId: String, change: (Post) -> Post) {
        val changed = replace(postId, change) ?: return
        postChanges.publish(PostChange.Changed(changed, source = this))
    }

    private fun replace(postId: String, change: (Post) -> Post): Post? {
        val loaded = state as? PostListState.Loaded ?: return null
        var changed: Post? = null
        state = loaded.copy(posts = loaded.posts.map { if (it.id == postId) change(it).also { new -> changed = new } else it })
        return changed
    }

    private fun removeWhere(predicate: (Post) -> Boolean) {
        val loaded = state as? PostListState.Loaded ?: return
        state = loaded.copy(posts = loaded.posts.filterNot(predicate))
    }
}

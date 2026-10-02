package com.kampusagi.android.presentation.community

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.repository.CommunityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Like, save, vote, attend and sold for any screen that shows posts. Likes and
 * saves change at once and are rolled back if the server refuses; votes and
 * attendance take the server's numbers.
 */
class PostInteractor(
    private val repository: CommunityRepository,
    private val scope: CoroutineScope,
    private val update: (postId: String, change: (Post) -> Post) -> Unit,
    private val onError: (AppError) -> Unit,
) {
    fun toggleLike(post: Post) {
        val liked = !post.likedByMe
        update(post.id) { it.copy(likedByMe = liked, likeCount = (post.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        scope.launch {
            when (val result = repository.setLiked(post.id, liked)) {
                is AppResult.Success -> update(post.id) { it.copy(likedByMe = liked, likeCount = result.value) }
                is AppResult.Failure -> {
                    update(post.id) { it.copy(likedByMe = post.likedByMe, likeCount = post.likeCount) }
                    onError(result.error)
                }
            }
        }
    }

    fun toggleSave(post: Post) {
        val saved = !post.savedByMe
        update(post.id) { it.copy(savedByMe = saved) }
        scope.launch {
            val result = repository.setSaved(post.id, saved)
            if (result is AppResult.Failure) {
                update(post.id) { it.copy(savedByMe = post.savedByMe) }
                onError(result.error)
            }
        }
    }

    /** [optionId] null withdraws the vote. */
    fun vote(post: Post, optionId: String?) {
        val poll = post.poll ?: return
        scope.launch {
            when (val result = repository.vote(poll.id, optionId)) {
                is AppResult.Success -> update(post.id) { it.copy(poll = result.value) }
                is AppResult.Failure -> onError(result.error)
            }
        }
    }

    fun toggleAttending(post: Post) {
        val event = post.event ?: return
        val attending = !event.attending
        scope.launch {
            when (val result = repository.setAttending(post.id, attending)) {
                is AppResult.Success -> update(post.id) {
                    it.copy(event = it.event?.copy(attending = attending, attendeeCount = result.value))
                }
                is AppResult.Failure -> onError(result.error)
            }
        }
    }

    fun setSold(post: Post, sold: Boolean) {
        val listing = post.listing ?: return
        scope.launch {
            when (val result = repository.setSold(post.id, sold)) {
                is AppResult.Success -> update(post.id) { it.copy(listing = listing.copy(sold = sold)) }
                is AppResult.Failure -> onError(result.error)
            }
        }
    }

    /** Callbacks for one post card. */
    fun callbacks(post: Post, onOpen: () -> Unit, onOpenAuthor: (String) -> Unit) = PostCallbacks(
        onOpen = onOpen,
        onOpenAuthor = onOpenAuthor,
        onToggleLike = { toggleLike(post) },
        onToggleSave = { toggleSave(post) },
        onVote = { vote(post, it) },
        onToggleAttending = { toggleAttending(post) },
    )
}

data class PostCallbacks(
    val onOpen: () -> Unit,
    val onOpenAuthor: (String) -> Unit,
    val onToggleLike: () -> Unit,
    val onToggleSave: () -> Unit,
    val onVote: (String?) -> Unit,
    val onToggleAttending: () -> Unit,
)

package com.kampusagi.android.presentation.community

import com.kampusagi.android.domain.model.Post
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface PostChange {
    /** Who made the change, so a screen does not apply its own change twice. */
    val source: Any

    data class Changed(val post: Post, override val source: Any) : PostChange
    data class Deleted(val postId: String, override val source: Any) : PostChange
    data class AuthorBlocked(val userId: String, override val source: Any) : PostChange
}

/** Keeps every open list of posts (feed, saved, events, search, profiles) in step with changes made elsewhere. */
@Singleton
class PostChanges @Inject constructor() {
    private val events = MutableSharedFlow<PostChange>(extraBufferCapacity = 64)

    val changes: SharedFlow<PostChange> = events.asSharedFlow()

    fun publish(change: PostChange) {
        events.tryEmit(change)
    }
}

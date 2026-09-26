package com.kampusagi.android.domain.model

/** Mirrors `public.post_scope`. */
enum class PostScope { GENERAL, UNIVERSITY }

data class Author(
    val id: String,
    val fullName: String?,
    val username: String?,
    val university: String?,
)

data class Post(
    val id: String,
    val scope: PostScope,
    val body: String,
    val createdAt: String,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
    val isMine: Boolean,
    val author: Author,
)

data class Comment(
    val id: String,
    val body: String,
    val createdAt: String,
    val isMine: Boolean,
    val author: Author,
)

/** Keyset cursor: the oldest post of the previous page. */
data class FeedCursor(val createdAt: String, val id: String)

data class FeedPage(val posts: List<Post>, val nextCursor: FeedCursor?)

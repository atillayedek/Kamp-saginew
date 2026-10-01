package com.kampusagi.android.domain.model

/** Mirrors `public.post_scope`. */
enum class PostScope { GENERAL, UNIVERSITY }

/** Mirrors `public.post_category`; the author picks one when sharing. */
enum class PostCategory { GENERAL, QUESTION, STUDY, EVENT, ANNOUNCEMENT, MARKETPLACE, HOUSING, LOST_FOUND, CAREER, SPORTS }

data class Author(
    val id: String,
    val fullName: String?,
    val username: String?,
    val university: String?,
)

data class PollOption(val id: String, val label: String, val votes: Int)

data class Poll(
    val id: String,
    val options: List<PollOption>,
    val totalVotes: Int,
    val myOptionId: String?,
    /** ISO-8601; null means the poll stays open. */
    val closesAt: String?,
)

data class PostEvent(
    val startsAt: String,
    val endsAt: String?,
    val location: String?,
    val attendeeCount: Int,
    val attending: Boolean,
)

/** A marketplace listing's price in kuruş (0 = free) and whether it was sold. */
data class Listing(val priceKurus: Long, val sold: Boolean)

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
    val category: PostCategory = PostCategory.GENERAL,
    val savedByMe: Boolean = false,
    /** Storage paths of the photos, in the order the author chose. */
    val media: List<String> = emptyList(),
    val poll: Poll? = null,
    val event: PostEvent? = null,
    val listing: Listing? = null,
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

/** Everything a new post can carry besides its text. Photos are JPEGs ready to upload. */
data class NewPost(
    val scope: PostScope,
    val category: PostCategory,
    val body: String,
    val photos: List<ByteArray> = emptyList(),
    val pollOptions: List<String>? = null,
    val pollClosesAt: String? = null,
    val eventStartsAt: String? = null,
    val eventLocation: String? = null,
    val priceKurus: Long? = null,
)

/** A person found by search. */
data class PersonSummary(
    val id: String,
    val fullName: String?,
    val username: String?,
    val university: String?,
    val department: String?,
)

/** Another student's public profile. */
data class UserProfile(
    val id: String,
    val fullName: String?,
    val username: String?,
    val university: String?,
    val department: String?,
    val bio: String?,
    val joinedAt: String,
    val postCount: Int,
    val isMe: Boolean,
)

/** Someone offered while typing "@" in a post or comment; [displayName] is masked unless they show it. */
data class MentionSuggestion(
    val userId: String,
    val username: String,
    val displayName: String?,
    val university: String?,
)

/** A #tag with how many visible posts used it in the last seven days. */
data class TagCount(val tag: String, val postCount: Long)

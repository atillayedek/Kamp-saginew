package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.CommentDto
import com.kampusagi.android.data.remote.PersonDto
import com.kampusagi.android.data.remote.PollDto
import com.kampusagi.android.data.remote.PostDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.UserProfileDto
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.Comment
import com.kampusagi.android.domain.model.FeedCursor
import com.kampusagi.android.domain.model.FeedPage
import com.kampusagi.android.domain.model.Listing
import com.kampusagi.android.domain.model.NewPost
import com.kampusagi.android.domain.model.PersonSummary
import com.kampusagi.android.domain.model.Poll
import com.kampusagi.android.domain.model.PollOption
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.model.PostEvent
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.model.UserProfile
import com.kampusagi.android.domain.repository.CommunityRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class CommunityRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : CommunityRepository {

    override suspend fun feed(scope: PostScope, category: PostCategory?, cursor: FeedCursor?): AppResult<FeedPage> = call { client ->
        val posts = client.postgrest.rpc(
            "list_posts",
            buildJsonObject {
                put("p_scope", scope.name)
                put("p_before_created_at", cursor?.createdAt)
                put("p_before_id", cursor?.id)
                put("p_limit", PAGE_SIZE)
                put("p_category", category?.name)
            },
        ).decodeList<PostDto>().map { it.toDomain() }
        page(posts)
    }

    override suspend fun post(postId: String): AppResult<Post> = call { client ->
        client.postgrest.rpc("get_post", params("p_post_id" to postId))
            .decodeList<PostDto>()
            .firstOrNull()
            ?.toDomain()
            ?: throw PostMissingException()
    }

    override suspend fun comments(postId: String): AppResult<List<Comment>> = call { client ->
        client.postgrest.rpc("list_comments", params("p_post_id" to postId))
            .decodeList<CommentDto>()
            .map {
                Comment(
                    id = it.id,
                    body = it.body,
                    createdAt = it.createdAt,
                    isMine = it.isMine,
                    author = Author(it.authorId, it.authorFullName, it.authorUsername, university = null),
                )
            }
    }

    override suspend fun createPost(post: NewPost): AppResult<String> = call { client ->
        val userId = client.auth.currentUserOrNull()?.id ?: throw SessionMissingException()
        val bucket = client.storage.from(AppConfig.POST_MEDIA_BUCKET)
        val paths = mutableListOf<String>()
        try {
            for (photo in post.photos) {
                val path = "$userId/${UUID.randomUUID()}.jpg"
                bucket.upload(path, photo) {
                    upsert = false
                    contentType = ContentType.Image.JPEG
                }
                paths += path
            }
            client.postgrest.rpc(
                "create_post",
                buildJsonObject {
                    put("p_scope", post.scope.name)
                    put("p_body", post.body)
                    put("p_category", post.category.name)
                    put("p_media_paths", JsonArray(paths.map { JsonPrimitive(it) }))
                    put("p_poll_options", post.pollOptions?.let { options -> JsonArray(options.map { JsonPrimitive(it) }) } ?: JsonNull)
                    put("p_poll_closes_at", post.pollClosesAt)
                    put("p_event_starts_at", post.eventStartsAt)
                    put("p_event_location", post.eventLocation)
                    put("p_price_kurus", post.priceKurus)
                },
            ).decodeAs<String>()
        } catch (e: Throwable) {
            // Photos uploaded for a post that was not created would never be shown; remove them.
            if (paths.isNotEmpty()) {
                safeCall { bucket.delete(paths) }.onFailure { Log.w(TAG, "Unused post photos could not be deleted", it) }
            }
            throw e
        }
    }

    override suspend fun deletePost(postId: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("delete_post", params("p_post_id" to postId))
        Unit
    }

    override suspend fun addComment(postId: String, body: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "add_comment",
            buildJsonObject {
                put("p_post_id", postId)
                put("p_body", body)
            },
        )
        Unit
    }

    override suspend fun deleteComment(commentId: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("delete_comment", params("p_comment_id" to commentId))
        Unit
    }

    override suspend fun setLiked(postId: String, liked: Boolean): AppResult<Int> = call { client ->
        client.postgrest.rpc(
            "set_post_like",
            buildJsonObject {
                put("p_post_id", postId)
                put("p_liked", liked)
            },
        ).decodeAs<Int>()
    }

    override suspend fun setSaved(postId: String, saved: Boolean): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "set_post_saved",
            buildJsonObject {
                put("p_post_id", postId)
                put("p_saved", saved)
            },
        )
        Unit
    }

    override suspend fun vote(pollId: String, optionId: String?): AppResult<Poll> = call { client ->
        client.postgrest.rpc(
            "vote_poll",
            buildJsonObject {
                put("p_poll_id", pollId)
                put("p_option_id", optionId)
            },
        ).decodeAs<PollDto>().toDomain()
    }

    override suspend fun setAttending(postId: String, attending: Boolean): AppResult<Int> = call { client ->
        client.postgrest.rpc(
            "set_event_attendance",
            buildJsonObject {
                put("p_post_id", postId)
                put("p_attending", attending)
            },
        ).decodeAs<Int>()
    }

    override suspend fun setSold(postId: String, sold: Boolean): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "set_listing_sold",
            buildJsonObject {
                put("p_post_id", postId)
                put("p_sold", sold)
            },
        )
        Unit
    }

    override suspend fun savedPosts(): AppResult<List<Post>> = call { client ->
        client.postgrest.rpc("list_saved_posts", JsonObject(emptyMap())).decodeList<PostDto>().map { it.toDomain() }
    }

    override suspend fun upcomingEvents(): AppResult<List<Post>> = call { client ->
        client.postgrest.rpc("list_upcoming_events", JsonObject(emptyMap())).decodeList<PostDto>().map { it.toDomain() }
    }

    override suspend fun searchPosts(query: String): AppResult<List<Post>> = call { client ->
        client.postgrest.rpc("search_posts", params("p_query" to query)).decodeList<PostDto>().map { it.toDomain() }
    }

    override suspend fun searchPeople(query: String): AppResult<List<PersonSummary>> = call { client ->
        client.postgrest.rpc("search_people", params("p_query" to query)).decodeList<PersonDto>().map {
            PersonSummary(it.userId, it.fullName, it.username, it.university, it.department)
        }
    }

    override suspend fun userProfile(userId: String): AppResult<UserProfile> = call { client ->
        val row = client.postgrest.rpc("get_user_profile", params("p_user_id" to userId))
            .decodeList<UserProfileDto>()
            .firstOrNull() ?: throw PostMissingException()
        UserProfile(
            id = row.userId,
            fullName = row.fullName,
            username = row.username,
            university = row.university,
            department = row.department,
            bio = row.bio,
            joinedAt = row.joinedAt,
            postCount = row.postCount,
            isMe = row.isMe,
        )
    }

    override suspend fun userPosts(userId: String, cursor: FeedCursor?): AppResult<FeedPage> = call { client ->
        val posts = client.postgrest.rpc(
            "list_user_posts",
            buildJsonObject {
                put("p_user_id", userId)
                put("p_before_created_at", cursor?.createdAt)
                put("p_before_id", cursor?.id)
                put("p_limit", PAGE_SIZE)
            },
        ).decodeList<PostDto>().map { it.toDomain() }
        page(posts)
    }

    override suspend fun downloadPhoto(path: String): AppResult<ByteArray> = call { client ->
        client.storage.from(AppConfig.POST_MEDIA_BUCKET).downloadAuthenticated(path)
    }

    /** A full page means there may be more; the last post is the next cursor. */
    private fun page(posts: List<Post>) =
        FeedPage(posts, if (posts.size == PAGE_SIZE) posts.last().let { FeedCursor(it.createdAt, it.id) } else null)

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                AppResult.Failure(
                    when (it) {
                        is PostMissingException -> AppError.NOT_FOUND
                        is SessionMissingException -> AppError.SESSION_EXPIRED
                        else -> it.toAppError()
                    },
                )
            },
        )
    }

    private fun params(vararg pairs: Pair<String, String>) =
        JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) })

    private fun PostDto.toDomain() = Post(
        id = id,
        scope = PostScope.entries.firstOrNull { it.name == scope } ?: throw UnknownStatusException(scope),
        body = body,
        createdAt = createdAt,
        likeCount = likeCount,
        commentCount = commentCount,
        likedByMe = likedByMe,
        isMine = isMine,
        author = Author(authorId, authorFullName, authorUsername, authorUniversity),
        // A category added on the server later shows as general until the app knows it.
        category = PostCategory.entries.firstOrNull { it.name == category } ?: PostCategory.GENERAL,
        savedByMe = savedByMe,
        media = media,
        poll = poll?.toDomain(),
        event = event?.let { PostEvent(it.startsAt, it.endsAt, it.location, it.attendeeCount, it.attending) },
        listing = listing?.let { Listing(it.priceKurus, it.sold) },
    )

    private fun PollDto.toDomain() = Poll(
        id = id,
        options = options.map { PollOption(it.id, it.label, it.votes) },
        totalVotes = totalVotes,
        myOptionId = myOptionId,
        closesAt = closesAt,
    )

    private class PostMissingException : IllegalStateException("Post not returned")
    private class SessionMissingException : IllegalStateException("No signed-in user")

    companion object {
        const val PAGE_SIZE = 20
        private const val TAG = "CommunityRepository"
    }
}

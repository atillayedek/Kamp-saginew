package com.kampusagi.android.data.repository

import com.kampusagi.android.data.remote.CommentDto
import com.kampusagi.android.data.remote.PostDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.Comment
import com.kampusagi.android.domain.model.FeedCursor
import com.kampusagi.android.domain.model.FeedPage
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.repository.CommunityRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
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
        // A full page means there may be more; the last post is the next cursor.
        val next = if (posts.size == PAGE_SIZE) posts.last().let { FeedCursor(it.createdAt, it.id) } else null
        FeedPage(posts, next)
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

    override suspend fun createPost(scope: PostScope, category: PostCategory, body: String): AppResult<String> = call { client ->
        client.postgrest.rpc(
            "create_post",
            buildJsonObject {
                put("p_scope", scope.name)
                put("p_body", body)
                put("p_category", category.name)
            },
        ).decodeAs<String>()
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

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(if (it is PostMissingException) AppError.NOT_FOUND else it.toAppError()) },
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
    )

    private class PostMissingException : IllegalStateException("Post not returned")

    companion object {
        const val PAGE_SIZE = 20
    }
}

package com.kampusagi.android.presentation.community

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.Comment
import com.kampusagi.android.domain.model.FeedCursor
import com.kampusagi.android.domain.model.FeedPage
import com.kampusagi.android.domain.model.MentionSuggestion
import com.kampusagi.android.domain.model.NewPost
import com.kampusagi.android.domain.model.PersonSummary
import com.kampusagi.android.domain.model.Poll
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.model.TagCount
import com.kampusagi.android.domain.model.UserProfile
import com.kampusagi.android.domain.repository.CommunityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun feedPost(id: String, liked: Boolean = false, likes: Int = 0) = Post(
    id = id, scope = PostScope.GENERAL, body = "gönderi $id", createdAt = "2026-09-26T12:00:00+00:00",
    likeCount = likes, commentCount = 0, likedByMe = liked, isMine = false,
    author = Author("a", "Ali Veli", "aliveli", "İTÜ"),
)

/** Scripted repository: pages by cursor id, like results on demand. Test-only. */
private class ScriptedCommunityRepository : CommunityRepository {
    val pages = mutableMapOf<String?, AppResult<FeedPage>>()
    var likeResult: AppResult<Int> = AppResult.Success(1)
    var saveResult: AppResult<Unit> = AppResult.Success(Unit)
    val feedCalls = mutableListOf<Pair<PostScope, FeedCursor?>>()
    val categoryCalls = mutableListOf<PostCategory?>()

    override suspend fun feed(scope: PostScope, category: PostCategory?, cursor: FeedCursor?): AppResult<FeedPage> {
        feedCalls += scope to cursor
        categoryCalls += category
        return pages[cursor?.id] ?: AppResult.Success(FeedPage(emptyList(), null))
    }
    override suspend fun post(postId: String): AppResult<Post> = AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun comments(postId: String): AppResult<List<Comment>> = AppResult.Success(emptyList())
    override suspend fun createPost(post: NewPost): AppResult<String> = AppResult.Success("new")
    override suspend fun deletePost(postId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun addComment(postId: String, body: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun deleteComment(commentId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setLiked(postId: String, liked: Boolean): AppResult<Int> = likeResult
    override suspend fun setSaved(postId: String, saved: Boolean): AppResult<Unit> = saveResult
    override suspend fun vote(pollId: String, optionId: String?): AppResult<Poll> = AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun setAttending(postId: String, attending: Boolean): AppResult<Int> = AppResult.Success(1)
    override suspend fun setSold(postId: String, sold: Boolean): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun savedPosts(): AppResult<List<Post>> = AppResult.Success(emptyList())
    override suspend fun upcomingEvents(): AppResult<List<Post>> = AppResult.Success(emptyList())
    override suspend fun searchPosts(query: String): AppResult<List<Post>> = AppResult.Success(emptyList())
    override suspend fun searchPeople(query: String): AppResult<List<PersonSummary>> = AppResult.Success(emptyList())
    override suspend fun userProfile(userId: String): AppResult<UserProfile> = AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun userPosts(userId: String, cursor: FeedCursor?): AppResult<FeedPage> = AppResult.Success(FeedPage(emptyList(), null))
    override suspend fun tagPosts(tag: String, cursor: FeedCursor?): AppResult<FeedPage> = AppResult.Success(FeedPage(emptyList(), null))
    override suspend fun popularTags(): AppResult<List<TagCount>> = AppResult.Success(emptyList())
    override suspend fun suggestMentions(query: String, scope: PostScope): AppResult<List<MentionSuggestion>> = AppResult.Success(emptyList())
    override suspend fun resolveUsername(username: String): AppResult<String> = AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun downloadPhoto(path: String): AppResult<ByteArray> = AppResult.Failure(AppError.NOT_FOUND)
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the general feed first and pages with the cursor`() {
        val repository = ScriptedCommunityRepository().apply {
            pages[null] = AppResult.Success(FeedPage(listOf(feedPost("1"), feedPost("2")), FeedCursor("t", "2")))
            // The second page repeats a post (inserted meanwhile): it must not be duplicated.
            pages["2"] = AppResult.Success(FeedPage(listOf(feedPost("2"), feedPost("3")), null))
        }
        val viewModel = FeedViewModel(repository, PostChanges())
        assertEquals(listOf("1", "2"), viewModel.state(PostScope.GENERAL).posts.map { it.id })

        viewModel.loadMore()
        val state = viewModel.state(PostScope.GENERAL)
        assertEquals(listOf("1", "2", "3"), state.posts.map { it.id })
        assertTrue(state.endReached)
        viewModel.loadMore()
        assertEquals(2, repository.feedCalls.size)
    }

    @Test
    fun `choosing a category reloads the feed with that filter`() {
        val repository = ScriptedCommunityRepository().apply {
            pages[null] = AppResult.Success(FeedPage(listOf(feedPost("1")), null))
        }
        val viewModel = FeedViewModel(repository, PostChanges())
        viewModel.selectCategory(PostCategory.MARKETPLACE)
        assertEquals(listOf(null, PostCategory.MARKETPLACE), repository.categoryCalls)
        assertEquals(PostCategory.MARKETPLACE, viewModel.selectedCategory)

        // The same filter again does nothing; a new post clears the filter so it is visible.
        viewModel.selectCategory(PostCategory.MARKETPLACE)
        assertEquals(2, repository.categoryCalls.size)
        viewModel.onPostCreated(PostScope.GENERAL)
        assertNull(viewModel.selectedCategory)
        assertEquals(null, repository.categoryCalls.last())
    }

    @Test
    fun `an empty feed is an empty state, not an error`() {
        val viewModel = FeedViewModel(ScriptedCommunityRepository(), PostChanges())
        val state = viewModel.state(PostScope.GENERAL)
        assertTrue(state.isEmpty)
        assertNull(state.error)
    }

    @Test
    fun `first page failure is shown and retry recovers`() {
        val repository = ScriptedCommunityRepository().apply { pages[null] = AppResult.Failure(AppError.NETWORK) }
        val viewModel = FeedViewModel(repository, PostChanges())
        assertEquals(AppError.NETWORK, viewModel.state(PostScope.GENERAL).error)
        assertFalse(viewModel.state(PostScope.GENERAL).loaded)

        repository.pages[null] = AppResult.Success(FeedPage(listOf(feedPost("1")), null))
        viewModel.retry()
        assertEquals(listOf("1"), viewModel.state(PostScope.GENERAL).posts.map { it.id })
        assertNull(viewModel.state(PostScope.GENERAL).error)
    }

    @Test
    fun `like uses the server count and is rolled back on failure`() {
        val repository = ScriptedCommunityRepository().apply {
            pages[null] = AppResult.Success(FeedPage(listOf(feedPost("1", likes = 4)), null))
            likeResult = AppResult.Success(7)
        }
        val viewModel = FeedViewModel(repository, PostChanges())
        viewModel.toggleLike(viewModel.state(PostScope.GENERAL).posts.single())
        var updated = viewModel.state(PostScope.GENERAL).posts.single()
        assertTrue(updated.likedByMe)
        assertEquals(7, updated.likeCount)

        repository.likeResult = AppResult.Failure(AppError.NETWORK)
        viewModel.toggleLike(updated)
        updated = viewModel.state(PostScope.GENERAL).posts.single()
        assertTrue(updated.likedByMe)
        assertEquals(7, updated.likeCount)
        assertEquals(AppError.NETWORK, viewModel.actionError)
    }

    @Test
    fun `university tab loads lazily on first selection`() {
        val repository = ScriptedCommunityRepository()
        val viewModel = FeedViewModel(repository, PostChanges())
        assertEquals(listOf(PostScope.GENERAL), repository.feedCalls.map { it.first })
        viewModel.selectScope(PostScope.UNIVERSITY)
        viewModel.selectScope(PostScope.GENERAL)
        viewModel.selectScope(PostScope.UNIVERSITY)
        assertEquals(listOf(PostScope.GENERAL, PostScope.UNIVERSITY), repository.feedCalls.map { it.first })
    }

    @Test
    fun `saving is optimistic and rolled back on failure`() {
        val repository = ScriptedCommunityRepository().apply {
            pages[null] = AppResult.Success(FeedPage(listOf(feedPost("1")), null))
        }
        val viewModel = FeedViewModel(repository, PostChanges())
        viewModel.interactor.toggleSave(viewModel.state(PostScope.GENERAL).posts.single())
        assertTrue(viewModel.state(PostScope.GENERAL).posts.single().savedByMe)

        repository.saveResult = AppResult.Failure(AppError.TOO_MANY_SAVED)
        viewModel.interactor.toggleSave(viewModel.state(PostScope.GENERAL).posts.single())
        assertTrue(viewModel.state(PostScope.GENERAL).posts.single().savedByMe)
        assertEquals(AppError.TOO_MANY_SAVED, viewModel.actionError)
    }

    @Test
    fun `changes made on another screen reach the feed`() {
        val repository = ScriptedCommunityRepository().apply {
            pages[null] = AppResult.Success(FeedPage(listOf(feedPost("1"), feedPost("2")), null))
        }
        val changes = PostChanges()
        val viewModel = FeedViewModel(repository, changes)
        val other = Any()
        changes.publish(PostChange.Changed(feedPost("1", liked = true, likes = 9), source = other))
        assertEquals(9, viewModel.state(PostScope.GENERAL).posts.first().likeCount)

        changes.publish(PostChange.Deleted("2", source = other))
        assertEquals(listOf("1"), viewModel.state(PostScope.GENERAL).posts.map { it.id })

        changes.publish(PostChange.AuthorBlocked("a", source = other))
        assertTrue(viewModel.state(PostScope.GENERAL).posts.isEmpty())
    }
}

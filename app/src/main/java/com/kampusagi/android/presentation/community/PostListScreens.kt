package com.kampusagi.android.presentation.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.presentation.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SavedPostsViewModel @Inject constructor(
    private val repository: CommunityRepository,
    postChanges: PostChanges,
) : PostListViewModel(repository, postChanges) {
    init {
        load()
    }

    override suspend fun fetch(): AppResult<List<Post>> = repository.savedPosts()
}

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repository: CommunityRepository,
    postChanges: PostChanges,
) : PostListViewModel(repository, postChanges) {
    init {
        load()
    }

    override suspend fun fetch(): AppResult<List<Post>> = repository.upcomingEvents()
}

@Composable
fun SavedPostsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    viewModel: SavedPostsViewModel = hiltViewModel(),
) {
    PostListPage(
        title = stringResource(R.string.saved_title),
        emptyIcon = AppIcons.Bookmark,
        emptyTitle = stringResource(R.string.saved_empty_title),
        emptyBody = stringResource(R.string.saved_empty_body),
        viewModel = viewModel,
        onBack = onBack,
        onOpenPost = onOpenPost,
        onOpenAuthor = onOpenAuthor,
    )
}

@Composable
fun EventsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    PostListPage(
        title = stringResource(R.string.events_title),
        emptyIcon = AppIcons.CalendarMonth,
        emptyTitle = stringResource(R.string.events_empty_title),
        emptyBody = stringResource(R.string.events_empty_body),
        viewModel = viewModel,
        onBack = onBack,
        onOpenPost = onOpenPost,
        onOpenAuthor = onOpenAuthor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostListPage(
    title: String,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyBody: String,
    viewModel: PostListViewModel,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        viewModel.actionError?.let { error ->
            Text(
                stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
        when (val state = viewModel.state) {
            PostListState.Loading -> LoadingView()
            is PostListState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.feed_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is PostListState.Loaded -> if (state.posts.isEmpty()) {
                MessageView(icon = emptyIcon, title = emptyTitle, body = emptyBody)
            } else {
                PostLazyList(state.posts, viewModel.interactor, onOpenPost, onOpenAuthor)
            }
        }
    }
}

/** Posts one under another, as in the feed. */
@Composable
fun PostLazyList(
    posts: List<Post>,
    interactor: PostInteractor,
    onOpenPost: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(posts, key = { it.id }) { post ->
            PostCard(post = post, callbacks = interactor.callbacks(post, onOpen = { onOpenPost(post.id) }, onOpenAuthor = onOpenAuthor))
        }
    }
}

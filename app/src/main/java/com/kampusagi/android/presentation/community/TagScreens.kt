package com.kampusagi.android.presentation.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.FeedCursor
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.main.MentionRoute
import com.kampusagi.android.presentation.main.TagPostsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Posts with one #tag, newest first, loading further pages as the list is scrolled. */
@HiltViewModel
class TagPostsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
    postChanges: PostChanges,
) : PostListViewModel(repository, postChanges) {

    val tag: String = savedStateHandle.toRoute<TagPostsRoute>().tag

    private var cursor: FeedCursor? = null
    private var loadingMore = false

    init {
        load()
    }

    override suspend fun fetch(): AppResult<List<Post>> = when (val result = repository.tagPosts(tag, null)) {
        is AppResult.Success -> {
            cursor = result.value.nextCursor
            AppResult.Success(result.value.posts)
        }
        is AppResult.Failure -> result
    }

    fun loadMore() {
        val next = cursor ?: return
        if (loadingMore || state !is PostListState.Loaded) return
        loadingMore = true
        viewModelScope.launch {
            when (val result = repository.tagPosts(tag, next)) {
                is AppResult.Success -> {
                    cursor = result.value.nextCursor
                    append(result.value.posts)
                }
                is AppResult.Failure -> reportError(result.error)
            }
            loadingMore = false
        }
    }
}

@Composable
fun TagPostsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    viewModel: TagPostsViewModel = hiltViewModel(),
) {
    PostListPage(
        title = "#" + viewModel.tag,
        emptyIcon = AppIcons.Search,
        emptyTitle = stringResource(R.string.tag_empty_title),
        emptyBody = stringResource(R.string.tag_empty_body),
        viewModel = viewModel,
        onBack = onBack,
        onOpenPost = onOpenPost,
        onOpenAuthor = onOpenAuthor,
        onReachEnd = viewModel::loadMore,
    )
}

sealed interface MentionState {
    data object Loading : MentionState
    data class Found(val userId: String) : MentionState
    data class Failed(val error: AppError) : MentionState
}

/** Opening "@username" from a post: finds the profile, then hands over to the profile screen. */
@HiltViewModel
class MentionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
) : ViewModel() {

    val username: String = savedStateHandle.toRoute<MentionRoute>().username

    var state by mutableStateOf<MentionState>(MentionState.Loading)
        private set

    init {
        resolve()
    }

    fun resolve() {
        state = MentionState.Loading
        viewModelScope.launch {
            state = when (val result = repository.resolveUsername(username)) {
                is AppResult.Success -> MentionState.Found(result.value)
                is AppResult.Failure -> MentionState.Failed(result.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentionScreen(
    onBack: () -> Unit,
    onFound: (String) -> Unit,
    viewModel: MentionViewModel = hiltViewModel(),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("@" + viewModel.username.trimEnd('.')) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        when (val state = viewModel.state) {
            MentionState.Loading -> LoadingView()
            is MentionState.Found -> LaunchedEffect(state.userId) { onFound(state.userId) }
            is MentionState.Failed -> MessageView(
                icon = AppIcons.Person,
                title = stringResource(
                    if (state.error == AppError.NOT_FOUND) R.string.mention_not_found_title else R.string.mention_failed_title,
                ),
                body = stringResource(state.error.messageRes()),
            ) {
                if (state.error != AppError.NOT_FOUND) {
                    PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::resolve)
                }
            }
        }
    }
}

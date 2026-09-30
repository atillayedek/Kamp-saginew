package com.kampusagi.android.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.FeedCursor
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.model.UserProfile
import com.kampusagi.android.domain.repository.ChatRepository
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.domain.repository.ModerationRepository
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.community.OpenedConversation
import com.kampusagi.android.presentation.community.PostCard
import com.kampusagi.android.presentation.community.PostChange
import com.kampusagi.android.presentation.community.PostChanges
import com.kampusagi.android.presentation.community.PostListState
import com.kampusagi.android.presentation.community.PostListViewModel
import com.kampusagi.android.presentation.main.UserProfileRoute
import com.kampusagi.android.presentation.moderation.BlockDialog
import com.kampusagi.android.presentation.moderation.MenuAction
import com.kampusagi.android.presentation.moderation.OverflowMenu
import com.kampusagi.android.presentation.moderation.ReportDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface UserProfileState {
    data object Loading : UserProfileState
    data class Loaded(val profile: UserProfile) : UserProfileState
    data class Failed(val error: AppError) : UserProfileState
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
    private val chat: ChatRepository,
    private val moderation: ModerationRepository,
    private val postChanges: PostChanges,
) : PostListViewModel(repository, postChanges) {

    val userId = savedStateHandle.toRoute<UserProfileRoute>().userId

    var profile by mutableStateOf<UserProfileState>(UserProfileState.Loading)
        private set

    var isWorking by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var openedConversation by mutableStateOf<OpenedConversation?>(null)
        private set

    var reporting by mutableStateOf(false)
        private set

    var confirmingBlock by mutableStateOf(false)
        private set

    var reportSent by mutableStateOf(false)
        private set

    /** Set once the person was blocked, so the screen can close. */
    var blocked by mutableStateOf(false)
        private set

    private var nextCursor: FeedCursor? = null

    init {
        loadProfile()
    }

    fun loadProfile() {
        profile = UserProfileState.Loading
        viewModelScope.launch {
            profile = when (val result = repository.userProfile(userId)) {
                is AppResult.Success -> UserProfileState.Loaded(result.value)
                is AppResult.Failure -> UserProfileState.Failed(result.error)
            }
        }
        load()
    }

    override suspend fun fetch(): AppResult<List<Post>> = when (val result = repository.userPosts(userId, cursor = null)) {
        is AppResult.Success -> {
            nextCursor = result.value.nextCursor
            AppResult.Success(result.value.posts)
        }
        is AppResult.Failure -> result
    }

    val canLoadMore: Boolean get() = nextCursor != null && !isLoadingMore

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            when (val result = repository.userPosts(userId, cursor)) {
                is AppResult.Success -> {
                    nextCursor = result.value.nextCursor
                    append(result.value.posts)
                }
                is AppResult.Failure -> reportError(result.error)
            }
            isLoadingMore = false
        }
    }

    fun message() {
        val loaded = (profile as? UserProfileState.Loaded)?.profile ?: return
        runAction {
            when (val result = chat.startConversation(userId)) {
                is AppResult.Success -> openedConversation = OpenedConversation(result.value, loaded.fullName ?: loaded.username.orEmpty())
                is AppResult.Failure -> reportError(result.error)
            }
        }
    }

    fun onConversationOpened() {
        openedConversation = null
    }

    fun startReport() {
        reporting = true
        reportSent = false
    }

    fun cancelReport() {
        reporting = false
    }

    fun submitReport(reason: ReportReason, details: String?) = runAction {
        reporting = false
        when (val result = moderation.report(ReportTarget.USER, userId, reason, details)) {
            is AppResult.Success -> reportSent = true
            is AppResult.Failure -> reportError(result.error)
        }
    }

    fun startBlock() {
        confirmingBlock = true
    }

    fun cancelBlock() {
        confirmingBlock = false
    }

    fun confirmBlock() = runAction {
        confirmingBlock = false
        when (val result = moderation.block(userId)) {
            is AppResult.Success -> {
                postChanges.publish(PostChange.AuthorBlocked(userId, source = this))
                blocked = true
            }
            is AppResult.Failure -> reportError(result.error)
        }
    }

    private fun runAction(action: suspend () -> Unit) {
        if (isWorking) return
        isWorking = true
        viewModelScope.launch {
            action()
            isWorking = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenChat: (conversationId: String, title: String) -> Unit,
    onEditOwnProfile: () -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.openedConversation) {
        viewModel.openedConversation?.let {
            viewModel.onConversationOpened()
            onOpenChat(it.id, it.title)
        }
    }
    LaunchedEffect(viewModel.blocked) { if (viewModel.blocked) onBack() }
    val loaded = (viewModel.profile as? UserProfileState.Loaded)?.profile

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(loaded?.username?.let { "@$it" } ?: loaded?.fullName.orEmpty()) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                if (loaded != null && !loaded.isMe) {
                    OverflowMenu(
                        enabled = !viewModel.isWorking,
                        actions = listOf(
                            MenuAction(R.string.report_user, viewModel::startReport),
                            MenuAction(R.string.action_block_user, viewModel::startBlock),
                        ),
                    )
                }
            },
        )
        when (val state = viewModel.profile) {
            UserProfileState.Loading -> LoadingView()
            is UserProfileState.Failed -> MessageView(
                icon = if (state.error == AppError.NOT_FOUND) AppIcons.PersonSearch else AppIcons.CloudOff,
                title = stringResource(if (state.error == AppError.NOT_FOUND) R.string.user_profile_not_found else R.string.user_profile_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                if (state.error != AppError.NOT_FOUND) {
                    PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::loadProfile)
                }
            }
            is UserProfileState.Loaded -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { ProfileHeader(state.profile, viewModel, onEditOwnProfile) }
                when (val posts = viewModel.state) {
                    PostListState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    is PostListState.Failed -> item {
                        Text(
                            stringResource(posts.error.messageRes()),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                    is PostListState.Loaded -> {
                        if (posts.posts.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.user_profile_no_posts),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(Spacing.md),
                                )
                            }
                        }
                        items(posts.posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                callbacks = viewModel.interactor.callbacks(
                                    post,
                                    onOpen = { onOpenPost(post.id) },
                                    onOpenAuthor = { if (it != viewModel.userId) onOpenPerson(it) },
                                ),
                            )
                        }
                        if (viewModel.canLoadMore || viewModel.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                    if (viewModel.isLoadingMore) {
                                        CircularProgressIndicator()
                                    } else {
                                        TextButton(onClick = viewModel::loadMore) { Text(stringResource(R.string.action_load_more)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewModel.reporting) {
        ReportDialog(
            target = ReportTarget.USER,
            submitting = viewModel.isWorking,
            onSubmit = viewModel::submitReport,
            onDismiss = viewModel::cancelReport,
        )
    }
    if (viewModel.confirmingBlock) {
        BlockDialog(
            name = loaded?.fullName ?: loaded?.username?.let { "@$it" } ?: stringResource(R.string.author_unknown),
            onConfirm = viewModel::confirmBlock,
            onDismiss = viewModel::cancelBlock,
        )
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile, viewModel: UserProfileViewModel, onEditOwnProfile: () -> Unit) {
    Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(profile.id, profile.fullName, profile.username, size = 88.dp)
            Column(modifier = Modifier.padding(start = Spacing.lg).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.fullName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Icon(
                        AppIcons.Verified,
                        contentDescription = stringResource(R.string.profile_verified),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.xs).size(18.dp),
                    )
                }
                Text(
                    pluralStringResource(R.plurals.user_profile_posts, profile.postCount, profile.postCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val school = listOfNotNull(profile.university, profile.department).joinToString(" · ")
        if (school.isNotEmpty()) {
            Text(school, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        profile.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs)) }
        if (viewModel.reportSent) {
            Text(stringResource(R.string.report_sent), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
        viewModel.actionError?.let {
            Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(modifier = Modifier.padding(top = Spacing.sm)) {
            if (profile.isMe) {
                OutlinedButton(onClick = onEditOwnProfile, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_edit))
                }
            } else {
                Button(onClick = viewModel::message, enabled = !viewModel.isWorking, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.user_profile_message), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

package com.kampusagi.android.presentation.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onOpenPost: (Post) -> Unit,
    onCreatePost: (PostScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = viewModel.selectedScope
    val state = viewModel.state(scope)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = scope.ordinal) {
                PostScope.entries.forEach { tab ->
                    Tab(
                        selected = tab == scope,
                        onClick = { viewModel.selectScope(tab) },
                        text = { Text(stringResource(tab.labelRes())) },
                    )
                }
            }
            viewModel.likeError?.let { error ->
                Text(
                    stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
            when {
                !state.loaded && state.error != null -> MessageView(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.feed_load_failed),
                    body = stringResource(state.error.messageRes()),
                ) {
                    PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::retry)
                }
                !state.loaded -> LoadingView()
                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.isEmpty) {
                        MessageView(
                            icon = Icons.Outlined.Forum,
                            title = stringResource(R.string.feed_empty_title),
                            body = stringResource(
                                if (scope == PostScope.GENERAL) R.string.feed_empty_general else R.string.feed_empty_university,
                            ),
                        )
                    } else {
                        PostList(state, viewModel, onOpenPost)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onCreatePost(scope) },
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            text = { Text(stringResource(R.string.action_new_post)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
        )
    }
}

@Composable
private fun PostList(state: FeedState, viewModel: FeedViewModel, onOpenPost: (Post) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.nextCursor) {
        if (nearEnd && state.error == null) viewModel.loadMore()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        // Leaves room for the floating button over the last post.
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = FAB_CLEARANCE),
    ) {
        items(state.posts, key = { it.id }) { post ->
            PostCard(post = post, onOpen = { onOpenPost(post) }, onToggleLike = { viewModel.toggleLike(post) })
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                when {
                    state.isLoadingMore -> CircularProgressIndicator()
                    state.error != null -> TextButton(onClick = viewModel::loadMore) {
                        Text(stringResource(state.error.messageRes()) + " " + stringResource(R.string.action_retry))
                    }
                    state.endReached -> Text(
                        stringResource(R.string.feed_end),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

fun PostScope.labelRes(): Int = when (this) {
    PostScope.GENERAL -> R.string.scope_general
    PostScope.UNIVERSITY -> R.string.scope_university
}

private const val LOAD_MORE_THRESHOLD = 5
private val FAB_CLEARANCE = androidx.compose.ui.unit.Dp(88f)

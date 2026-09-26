package com.kampusagi.android.presentation.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Comment
import com.kampusagi.android.domain.model.Post
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    onPostChanged: (Post) -> Unit,
    onPostDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = hiltViewModel(),
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val loaded = viewModel.state as? PostDetailState.Loaded

    // Keep the feed in step with likes and comment counts made here.
    LaunchedEffect(loaded?.post) { loaded?.post?.let(onPostChanged) }
    LaunchedEffect(viewModel.deleted) {
        if (viewModel.deleted) {
            loaded?.post?.id?.let(onPostDeleted)
            onBack()
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.post_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                if (loaded?.post?.isMine == true) {
                    IconButton(onClick = { confirmDelete = true }, enabled = !viewModel.isWorking) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_delete_post))
                    }
                }
            },
        )
        when (val state = viewModel.state) {
            PostDetailState.Loading -> LoadingView()
            is PostDetailState.Failed -> MessageView(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.post_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is PostDetailState.Loaded -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            AuthorLine(state.post.author, state.post.createdAt)
                            Text(state.post.body, style = MaterialTheme.typography.bodyLarge)
                            PostActions(state.post, onToggleLike = viewModel::toggleLike, onOpenComments = null)
                            HorizontalDivider()
                        }
                    }
                    if (state.comments.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.comments_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Spacing.md),
                            )
                        }
                    }
                    items(state.comments, key = { it.id }) { comment ->
                        CommentRow(comment, enabled = !viewModel.isWorking, onDelete = { viewModel.deleteComment(comment) })
                    }
                }
                viewModel.actionError?.let { error ->
                    Text(
                        stringResource(error.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                ) {
                    OutlinedTextField(
                        value = viewModel.commentText,
                        onValueChange = viewModel::onCommentTextChange,
                        placeholder = { Text(stringResource(R.string.comment_hint)) },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::sendComment, enabled = viewModel.canSendComment) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.action_send_comment))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_post_title)) },
            text = { Text(stringResource(R.string.delete_post_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deletePost()
                }) { Text(stringResource(R.string.action_delete_post)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CommentRow(comment: Comment, enabled: Boolean, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AuthorLine(comment.author, comment.createdAt)
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (comment.isMine) {
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_delete_comment))
            }
        }
    }
}

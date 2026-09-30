package com.kampusagi.android.presentation.community

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
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
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.moderation.BlockDialog
import com.kampusagi.android.presentation.moderation.MenuAction
import com.kampusagi.android.presentation.moderation.OverflowMenu
import com.kampusagi.android.presentation.moderation.ReportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenChat: (conversationId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = hiltViewModel(),
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val loaded = viewModel.state as? PostDetailState.Loaded

    // Other screens learn about changes made here through PostChanges.
    LaunchedEffect(viewModel.blockedAuthorId) {
        if (viewModel.blockedAuthorId != null) onBack()
    }
    LaunchedEffect(viewModel.deleted) {
        if (viewModel.deleted) onBack()
    }
    LaunchedEffect(viewModel.openedConversation) {
        viewModel.openedConversation?.let {
            viewModel.onConversationOpened()
            onOpenChat(it.id, it.title)
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.post_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                val post = loaded?.post
                if (post?.isMine == true) {
                    IconButton(onClick = { confirmDelete = true }, enabled = !viewModel.isWorking) {
                        Icon(AppIcons.Delete, contentDescription = stringResource(R.string.action_delete_post))
                    }
                } else if (post != null) {
                    OverflowMenu(
                        enabled = !viewModel.isWorking,
                        actions = listOf(
                            MenuAction(R.string.report_post) { viewModel.requestReport(ReportTarget.POST, post.id) },
                            MenuAction(R.string.action_block_user) { viewModel.requestBlock(post.author) },
                        ),
                    )
                }
            },
        )
        when (val state = viewModel.state) {
            PostDetailState.Loading -> LoadingView()
            is PostDetailState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
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
                        val post = state.post
                        val callbacks = viewModel.interactor.callbacks(post, onOpen = {}, onOpenAuthor = onOpenAuthor)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Column(
                                modifier = Modifier.padding(horizontal = Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                AuthorLine(post.author, post.createdAt, modifier = Modifier.clickable { onOpenAuthor(post.author.id) })
                                PostLabels(post)
                                Text(post.body, style = MaterialTheme.typography.bodyLarge)
                            }
                            PostMedia(post.media)
                            PostAttachments(post, callbacks)
                            post.listing?.let { listing ->
                                Row(modifier = Modifier.padding(horizontal = Spacing.md)) {
                                    if (post.isMine) {
                                        OutlinedButton(
                                            onClick = { viewModel.interactor.setSold(post, !listing.sold) },
                                            enabled = !viewModel.isWorking,
                                        ) {
                                            Text(stringResource(if (listing.sold) R.string.listing_mark_available else R.string.listing_mark_sold))
                                        }
                                    } else if (!listing.sold) {
                                        FilledTonalButton(onClick = viewModel::messageAuthor, enabled = !viewModel.isWorking) {
                                            Icon(AppIcons.Chat, contentDescription = null)
                                            Text(stringResource(R.string.listing_message_seller), modifier = Modifier.padding(start = Spacing.sm))
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.padding(horizontal = Spacing.xs)) {
                                PostActions(
                                    post,
                                    onToggleLike = callbacks.onToggleLike,
                                    onToggleSave = callbacks.onToggleSave,
                                    onOpenComments = null,
                                )
                            }
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
                        CommentRow(
                            comment,
                            enabled = !viewModel.isWorking,
                            onOpenAuthor = onOpenAuthor,
                            onDelete = { viewModel.deleteComment(comment) },
                            onReport = { viewModel.requestReport(ReportTarget.COMMENT, comment.id) },
                            onBlock = { viewModel.requestBlock(comment.author) },
                        )
                    }
                }
                if (viewModel.reportSent) {
                    Text(
                        stringResource(R.string.report_sent),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
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
                        Icon(AppIcons.Send, contentDescription = stringResource(R.string.action_send_comment))
                    }
                }
            }
        }
    }

    viewModel.reportRequest?.let { request ->
        ReportDialog(
            target = request.target,
            submitting = viewModel.isWorking,
            onSubmit = viewModel::submitReport,
            onDismiss = viewModel::cancelReport,
        )
    }
    viewModel.blockRequest?.let { author ->
        BlockDialog(
            name = author.fullName ?: author.username?.let { "@$it" } ?: stringResource(R.string.author_unknown),
            onConfirm = viewModel::confirmBlock,
            onDismiss = viewModel::cancelBlock,
        )
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
private fun CommentRow(
    comment: Comment,
    enabled: Boolean,
    onOpenAuthor: (String) -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AuthorLine(comment.author, comment.createdAt, modifier = Modifier.clickable { onOpenAuthor(comment.author.id) })
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (comment.isMine) {
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(AppIcons.Delete, contentDescription = stringResource(R.string.action_delete_comment))
            }
        } else {
            OverflowMenu(
                enabled = enabled,
                actions = listOf(
                    MenuAction(R.string.report_comment, onReport),
                    MenuAction(R.string.action_block_user, onBlock),
                ),
            )
        }
    }
}

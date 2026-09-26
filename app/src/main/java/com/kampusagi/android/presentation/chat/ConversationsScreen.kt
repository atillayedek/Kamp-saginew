package com.kampusagi.android.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Conversation
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.common.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    onOpen: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val state = viewModel.state) {
        ConversationsState.Loading -> LoadingView(modifier)
        is ConversationsState.Failed -> MessageView(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.conversations_load_failed),
            body = stringResource(state.error.messageRes()),
            modifier = modifier,
        ) {
            PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::refresh)
        }
        is ConversationsState.Loaded -> {
            PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = modifier.fillMaxSize(),
            ) {
                if (state.conversations.isEmpty()) {
                    MessageView(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = stringResource(R.string.conversations_empty_title),
                        body = stringResource(R.string.conversations_empty_body),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.conversations, key = { it.id }) { conversation ->
                            ConversationRow(conversation, onClick = { onOpen(conversation) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    val unread = conversation.unreadCount > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                conversation.other.displayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else null,
            )
            val preview = conversation.lastMessageBody?.let {
                if (conversation.lastMessageIsMine) stringResource(R.string.chat_you_prefix, it) else it
            } ?: stringResource(R.string.chat_no_messages_yet)
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            conversation.lastMessageAt?.let {
                Text(relativeTime(it), style = MaterialTheme.typography.bodySmall)
            }
            if (unread) Badge { Text(conversation.unreadCount.toString()) }
        }
    }
}

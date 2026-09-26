package com.kampusagi.android.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.ChatMessage
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.common.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(viewModel.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.loaded && state.error != null -> MessageView(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.chat_load_failed),
                    body = stringResource(state.error.messageRes()),
                ) {
                    PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::retryLoad)
                }
                !state.loaded -> LoadingView()
                state.messages.isEmpty() && state.outgoing.isEmpty() -> MessageView(
                    icon = Icons.AutoMirrored.Outlined.Send,
                    title = stringResource(R.string.chat_empty_title),
                    body = stringResource(R.string.chat_empty_body),
                )
                else -> MessageList(state, viewModel)
            }
        }
        if (state.loaded && state.error != null) {
            Text(
                stringResource(state.error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
            OutlinedTextField(
                value = viewModel.draft,
                onValueChange = viewModel::onDraftChange,
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                maxLines = 5,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::send, enabled = viewModel.draft.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.action_send_message))
            }
        }
    }
}

@Composable
private fun MessageList(state: ChatState, viewModel: ChatViewModel) {
    val listState = rememberLazyListState()
    // Newest at the bottom: the list is reversed, so index 0 is the newest item.
    LaunchedEffect(state.outgoing.size, state.messages.firstOrNull()?.id) { listState.animateScrollToItem(0) }
    val lastMine = state.messages.firstOrNull { it.isMine }?.id
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(Spacing.md),
    ) {
        items(state.outgoing.reversed(), key = { "out-" + it.id }) { message ->
            Bubble(
                body = message.body,
                mine = true,
                status = stringResource(if (message.failed) R.string.chat_status_failed else R.string.chat_status_sending),
                statusIsError = message.failed,
                onClick = if (message.failed) ({ viewModel.retry(message) }) else null,
            )
        }
        items(state.messages, key = { it.id }) { message ->
            Bubble(
                body = message.body,
                mine = message.isMine,
                time = relativeTime(message.createdAt),
                // Receipt only under the newest own message, like most messengers.
                status = if (message.id == lastMine) receipt(message) else null,
            )
        }
        if (state.olderAvailable) {
            item {
                TextButton(onClick = viewModel::loadOlder, enabled = !state.isLoadingOlder, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.chat_load_older))
                }
            }
        }
    }
}

@Composable
private fun receipt(message: ChatMessage): String =
    stringResource(if (message.readByOther) R.string.chat_status_read else R.string.chat_status_sent)

@Composable
private fun Bubble(
    body: String,
    mine: Boolean,
    time: String? = null,
    status: String? = null,
    statusIsError: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        ) {
            Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.sm))
        }
        val meta = listOfNotNull(time?.ifEmpty { null }, status).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Ad Soyad" or "@kullanici" for a chat partner. */
fun Author.displayName(): String = fullName ?: username?.let { "@$it" } ?: ""


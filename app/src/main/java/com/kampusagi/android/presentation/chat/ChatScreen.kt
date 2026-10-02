package com.kampusagi.android.presentation.chat

import com.kampusagi.android.presentation.common.rememberPersonalDataGuard
import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.combinedClickable
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
import com.kampusagi.android.presentation.moderation.BlockDialog
import com.kampusagi.android.presentation.moderation.MenuAction
import com.kampusagi.android.presentation.moderation.OverflowMenu
import com.kampusagi.android.presentation.moderation.ReportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onBlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val personalData = rememberPersonalDataGuard()
    val state = viewModel.state
    LaunchedEffect(viewModel.blocked) { if (viewModel.blocked) onBlocked() }
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(viewModel.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                OverflowMenu(
                    enabled = !viewModel.moderationBusy,
                    actions = listOf(
                        MenuAction(R.string.report_user, viewModel::requestUserReport),
                        MenuAction(R.string.action_block_user, viewModel::requestBlock),
                    ),
                )
            },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.loaded && state.error != null -> MessageView(
                    icon = AppIcons.CloudOff,
                    title = stringResource(R.string.chat_load_failed),
                    body = stringResource(state.error.messageRes()),
                ) {
                    PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::retryLoad)
                }
                !state.loaded -> LoadingView()
                state.messages.isEmpty() && state.outgoing.isEmpty() -> MessageView(
                    icon = AppIcons.Send,
                    title = stringResource(R.string.chat_empty_title),
                    body = stringResource(R.string.chat_empty_body),
                )
                else -> MessageList(state, viewModel)
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
            IconButton(onClick = { personalData.send(viewModel.draft, viewModel::send) }, enabled = viewModel.draft.isNotBlank()) {
                Icon(AppIcons.Send, contentDescription = stringResource(R.string.action_send_message))
            }
        }
    }

    viewModel.reportRequest?.let { request ->
        ReportDialog(
            target = request.target,
            submitting = viewModel.moderationBusy,
            onSubmit = viewModel::submitReport,
            onDismiss = viewModel::cancelReport,
        )
    }
    if (viewModel.blockRequested) {
        BlockDialog(name = viewModel.title, onConfirm = viewModel::confirmBlock, onDismiss = viewModel::cancelBlock)
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
                // Long-press on the other person's message to report it.
                onLongClick = if (message.isMine) null else ({ viewModel.requestMessageReport(message) }),
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
    onLongClick: (() -> Unit)? = null,
) {
    val reportLabel = stringResource(R.string.report_message)
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
                .let {
                    if (onClick != null || onLongClick != null) {
                        it.combinedClickable(
                            onClick = onClick ?: {},
                            onLongClickLabel = if (onLongClick != null) reportLabel else null,
                            onLongClick = onLongClick,
                        )
                    } else {
                        it
                    }
                },
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


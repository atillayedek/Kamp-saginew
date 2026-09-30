package com.kampusagi.android.presentation.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.GroupDetail
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.model.GroupMessage
import com.kampusagi.android.domain.model.GroupRole
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.usecase.NewPostValidator
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.common.media.PhotoSource
import com.kampusagi.android.presentation.common.media.PostPhoto
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.common.relativeTime
import com.kampusagi.android.presentation.community.PollView
import com.kampusagi.android.presentation.moderation.MenuAction
import com.kampusagi.android.presentation.moderation.OverflowMenu
import com.kampusagi.android.presentation.moderation.ReportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    onOpenInfo: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenPremium: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val group = state.group
    var reporting by remember { mutableStateOf<GroupMessage?>(null) }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = {
                if (group != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenInfo(group.id) },
                    ) {
                        GroupAvatar(group.kind, group.photoPath, size = 36.dp)
                        Column(modifier = Modifier.padding(start = Spacing.sm)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                pluralStringResource(R.plurals.group_members, group.memberCount, group.memberCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            },
            actions = {
                if (group != null) {
                    IconButton(onClick = { onOpenInfo(group.id) }) {
                        Icon(AppIcons.Info, contentDescription = stringResource(R.string.group_info_title))
                    }
                }
            },
        )
        when {
            group == null && state.loadError != null -> MessageView(
                icon = if (state.loadError == AppError.NOT_FOUND) AppIcons.Groups else AppIcons.CloudOff,
                title = stringResource(if (state.loadError == AppError.NOT_FOUND) R.string.group_not_found else R.string.groups_load_failed),
                body = stringResource(state.loadError.messageRes()),
            ) {
                if (state.loadError != AppError.NOT_FOUND) PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            group == null -> LoadingView()
            group.myRole == null -> NotJoined(group, viewModel)
            else -> {
                group.pinnedMessageBody?.let { PinnedBanner(it, canUnpin = group.canManage, onUnpin = { viewModel.pin(null) }) }
                Box(modifier = Modifier.weight(1f)) {
                    if (!state.loaded) {
                        LoadingView()
                    } else if (state.messages.isEmpty()) {
                        MessageView(
                            icon = if (group.kind == GroupKind.CHANNEL) AppIcons.Campaign else AppIcons.Forum,
                            title = stringResource(R.string.group_no_messages_title),
                            body = stringResource(
                                if (group.kind == GroupKind.CHANNEL) R.string.channel_no_messages_body else R.string.group_no_messages_body,
                            ),
                        )
                    } else {
                        Messages(group, viewModel, onOpenPerson, onReport = { reporting = it })
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
                viewModel.actionError?.let {
                    Text(
                        stringResource(it.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (group.canPost) {
                    Composer(group, viewModel)
                } else {
                    CannotPost(group, onOpenPremium)
                }
            }
        }
    }

    reporting?.let { message ->
        ReportDialog(
            target = ReportTarget.GROUP_MESSAGE,
            submitting = viewModel.isWorking,
            onSubmit = { reason, details ->
                reporting = null
                viewModel.report(message, reason, details)
            },
            onDismiss = { reporting = null },
        )
    }
}

@Composable
private fun NotJoined(group: GroupDetail, viewModel: GroupChatViewModel) {
    MessageView(
        icon = if (group.kind == GroupKind.CHANNEL) AppIcons.Campaign else AppIcons.Groups,
        title = group.name,
        body = group.description ?: stringResource(if (group.kind == GroupKind.CHANNEL) R.string.channel_join_body else R.string.group_join_body),
    ) {
        viewModel.actionError?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            text = stringResource(if (group.kind == GroupKind.CHANNEL) R.string.channel_follow else R.string.group_join),
            onClick = viewModel::join,
            loading = viewModel.isWorking,
        )
    }
}

@Composable
private fun PinnedBanner(body: String, canUnpin: Boolean, onUnpin: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = Spacing.md)) {
            Icon(AppIcons.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                body.ifBlank { stringResource(R.string.group_photo_message) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            )
            if (canUnpin) {
                IconButton(onClick = onUnpin) { Icon(AppIcons.Close, contentDescription = stringResource(R.string.group_unpin)) }
            }
        }
    }
}

@Composable
private fun Messages(group: GroupDetail, viewModel: GroupChatViewModel, onOpenPerson: (String) -> Unit, onReport: (GroupMessage) -> Unit) {
    val listState = rememberLazyListState()
    val nearOldest by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearOldest, viewModel.state.messages.size) { if (nearOldest) viewModel.loadOlder() }
    // Keep the newest message in view when one arrives while the person is at the bottom.
    LaunchedEffect(viewModel.state.messages.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
        items(viewModel.state.messages, key = { it.id }) { message ->
            MessageItem(
                message = message,
                group = group,
                onOpenPerson = onOpenPerson,
                onLike = { viewModel.toggleLike(message) },
                onVote = { viewModel.vote(message, it) },
                actions = buildList {
                    if (group.canManage) {
                        add(
                            if (group.pinnedMessageId == message.id) MenuAction(R.string.group_unpin) { viewModel.pin(null) }
                            else MenuAction(R.string.group_pin) { viewModel.pin(message) },
                        )
                    }
                    if (message.isMine || group.canManage) add(MenuAction(R.string.group_delete_message) { viewModel.delete(message) })
                    if (!message.isMine) add(MenuAction(R.string.report_group_message) { onReport(message) })
                },
            )
        }
        if (viewModel.state.isLoadingOlder) {
            item { Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun MessageItem(
    message: GroupMessage,
    group: GroupDetail,
    onOpenPerson: (String) -> Unit,
    onLike: () -> Unit,
    onVote: (String?) -> Unit,
    actions: List<MenuAction>,
) {
    val channel = group.kind == GroupKind.CHANNEL
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.Top) {
        // Channels speak as the channel; study groups show who wrote each message.
        if (channel) {
            GroupAvatar(group.kind, group.photoPath, size = 36.dp)
        } else {
            UserAvatar(
                message.sender.id,
                message.sender.fullName,
                message.sender.username,
                size = 36.dp,
                modifier = Modifier.clickable { onOpenPerson(message.sender.id) },
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (channel) group.name else message.sender.fullName ?: message.sender.username.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " · " + relativeTime(message.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = if (message.isMine && !channel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(top = 2.dp).widthIn(max = 340.dp),
            ) {
                Column(modifier = Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    message.mediaPath?.let {
                        PostPhoto(
                            it,
                            source = PhotoSource.GROUP,
                            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f).clip(MaterialTheme.shapes.medium),
                        )
                    }
                    message.body?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    message.poll?.let { PollView(it, onVote = onVote) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (message.likedByMe) AppIcons.FavoriteFilled else AppIcons.Favorite,
                        contentDescription = stringResource(if (message.likedByMe) R.string.cd_unlike else R.string.cd_like),
                        tint = if (message.likedByMe) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (message.likeCount > 0) Text(message.likeCount.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (actions.isNotEmpty()) OverflowMenu(actions = actions)
    }
}

@Composable
private fun CannotPost(group: GroupDetail, onOpenPremium: () -> Unit) {
    val premiumLapsed = group.kind == GroupKind.CHANNEL && group.canManage && !group.ownerIsPremium
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
        Icon(if (premiumLapsed) AppIcons.WorkspacePremium else AppIcons.Campaign, contentDescription = null)
        Text(
            stringResource(
                when {
                    !premiumLapsed -> R.string.channel_read_only
                    group.myRole == GroupRole.OWNER -> R.string.channel_premium_lapsed_owner
                    else -> R.string.channel_premium_lapsed_admin
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
        )
        if (premiumLapsed && group.myRole == GroupRole.OWNER) {
            TextButton(onClick = onOpenPremium) { Text(stringResource(R.string.premium_title)) }
        }
    }
}

@Composable
private fun Composer(group: GroupDetail, viewModel: GroupChatViewModel) {
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onPhotoPicked(it.toString()) }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        viewModel.photo?.let { photo ->
            Box(modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.medium)) {
                Image(photo.preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp))
                IconButton(onClick = viewModel::removePhoto, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.create_post_remove_photo))
                }
            }
        }
        viewModel.pollOptions?.let { options ->
            Text(stringResource(R.string.group_poll_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = option,
                        onValueChange = { viewModel.onPollOptionChange(index, it) },
                        placeholder = { Text(stringResource(R.string.create_post_poll_option, index + 1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (options.size > NewPostValidator.MIN_POLL_OPTIONS) {
                        IconButton(onClick = { viewModel.removePollOption(index) }) {
                            Icon(AppIcons.Remove, contentDescription = stringResource(R.string.create_post_poll_remove_option))
                        }
                    }
                }
            }
            Row {
                if (options.size < NewPostValidator.MAX_POLL_OPTIONS) {
                    TextButton(onClick = viewModel::addPollOption) { Text(stringResource(R.string.create_post_poll_add_option)) }
                }
                TextButton(onClick = viewModel::cancelPoll) { Text(stringResource(R.string.group_poll_remove)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !viewModel.isSending,
            ) {
                Icon(AppIcons.AddPhotoAlternate, contentDescription = stringResource(R.string.group_attach_photo))
            }
            if (group.kind == GroupKind.CHANNEL && viewModel.pollOptions == null) {
                IconButton(onClick = viewModel::startPoll, enabled = !viewModel.isSending) {
                    Icon(AppIcons.BarChart, contentDescription = stringResource(R.string.create_post_add_poll))
                }
            }
            OutlinedTextField(
                value = viewModel.draft,
                onValueChange = viewModel::onDraftChange,
                placeholder = {
                    Text(
                        stringResource(
                            when {
                                viewModel.pollOptions != null -> R.string.group_poll_question
                                group.kind == GroupKind.CHANNEL -> R.string.channel_message_hint
                                else -> R.string.chat_input_hint
                            },
                        ),
                    )
                },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            if (viewModel.isSending) {
                CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm).size(24.dp))
            } else {
                IconButton(onClick = viewModel::send, enabled = viewModel.canSend) {
                    Icon(AppIcons.Send, contentDescription = stringResource(R.string.action_send))
                }
            }
        }
    }
}

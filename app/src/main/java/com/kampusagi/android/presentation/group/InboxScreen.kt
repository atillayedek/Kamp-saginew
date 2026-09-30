package com.kampusagi.android.presentation.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.model.GroupSummary
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.common.relativeTime

/** The Sohbet tab: direct messages, study groups and channels, as in Instagram's inbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    groupsViewModel: GroupsViewModel,
    unreadMessages: Int,
    messages: @Composable () -> Unit,
    onOpenGroup: (String) -> Unit,
    onDiscover: (GroupKind) -> Unit,
    onCreate: (GroupKind) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_chat), style = MaterialTheme.typography.titleLarge) })
        PrimaryTabRow(selectedTabIndex = tab) {
            InboxTab(selected = tab == 0, label = R.string.inbox_messages, unread = unreadMessages) { tab = 0 }
            InboxTab(
                selected = tab == 1,
                label = R.string.inbox_groups,
                unread = unreadOf(groupsViewModel, GroupKind.STUDY_GROUP),
            ) { tab = 1 }
            InboxTab(
                selected = tab == 2,
                label = R.string.inbox_channels,
                unread = unreadOf(groupsViewModel, GroupKind.CHANNEL),
            ) { tab = 2 }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> messages()
                1 -> GroupList(GroupKind.STUDY_GROUP, groupsViewModel, onOpenGroup, onDiscover, onCreate)
                else -> GroupList(GroupKind.CHANNEL, groupsViewModel, onOpenGroup, onDiscover, onCreate)
            }
        }
    }
}

private fun unreadOf(viewModel: GroupsViewModel, kind: GroupKind): Int =
    (viewModel.state(kind) as? GroupListState.Loaded)?.groups?.sumOf { it.unreadCount } ?: 0

@Composable
private fun InboxTab(selected: Boolean, label: Int, unread: Int, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            if (unread > 0) {
                BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                    Text(stringResource(label), modifier = Modifier.padding(end = Spacing.sm))
                }
            } else {
                Text(stringResource(label))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupList(
    kind: GroupKind,
    viewModel: GroupsViewModel,
    onOpenGroup: (String) -> Unit,
    onDiscover: (GroupKind) -> Unit,
    onCreate: (GroupKind) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = viewModel.state(kind)) {
            GroupListState.Loading -> LoadingView()
            is GroupListState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.groups_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is GroupListState.Loaded -> PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        ) {
                            Text(
                                stringResource(if (kind == GroupKind.CHANNEL) R.string.channels_intro else R.string.groups_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { onDiscover(kind) }, modifier = Modifier.padding(start = Spacing.sm)) {
                                Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.groups_discover), modifier = Modifier.padding(start = Spacing.xs))
                            }
                        }
                    }
                    if (state.groups.isEmpty()) {
                        item {
                            MessageView(
                                icon = if (kind == GroupKind.CHANNEL) AppIcons.Campaign else AppIcons.Groups,
                                title = stringResource(if (kind == GroupKind.CHANNEL) R.string.channels_empty_title else R.string.groups_empty_title),
                                body = stringResource(if (kind == GroupKind.CHANNEL) R.string.channels_empty_body else R.string.groups_empty_body),
                                modifier = Modifier.padding(top = Spacing.lg),
                            )
                        }
                    }
                    items(state.groups, key = { it.id }) { group ->
                        GroupRow(group, onClick = { onOpenGroup(group.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onCreate(kind) },
            icon = { Icon(AppIcons.Add, contentDescription = null) },
            text = { Text(stringResource(if (kind == GroupKind.CHANNEL) R.string.channel_create else R.string.group_create)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
        )
    }
}

@Composable
fun GroupRow(group: GroupSummary, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val unread = group.unreadCount > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.md),
    ) {
        GroupAvatar(group.kind, group.photoPath)
        Column(
            modifier = Modifier.weight(1f).padding(start = Spacing.sm + Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                group.courseCode?.let { add(it) }
                add(pluralStringResource(R.plurals.group_members, group.memberCount, group.memberCount))
                if (group.kind == GroupKind.CHANNEL) {
                    add(stringResource(if (group.isGlobal) R.string.channel_scope_all else R.string.channel_scope_university))
                }
            }
            Text(
                group.lastMessageBody?.takeIf { it.isNotBlank() }
                    ?: group.description
                    ?: details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (group.lastMessageBody != null || group.description != null) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            group.lastMessageAt?.let { Text(relativeTime(it), style = MaterialTheme.typography.bodySmall) }
            if (unread) Badge { Text(group.unreadCount.toString()) }
            trailing()
        }
    }
}

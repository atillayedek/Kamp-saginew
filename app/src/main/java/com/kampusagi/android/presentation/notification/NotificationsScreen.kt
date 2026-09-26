package com.kampusagi.android.presentation.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppNotification
import com.kampusagi.android.domain.model.NotificationKind
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.common.relativeTime

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    notificationsAllowed: Boolean,
    onOpen: (AppNotification) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        val pushNote = when {
            !viewModel.isPushConfigured -> R.string.push_not_configured
            !notificationsAllowed -> R.string.push_permission_denied
            else -> null
        }
        pushNote?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(Spacing.md),
            )
        }
        when (val state = viewModel.state) {
            NotificationsState.Loading -> LoadingView()
            is NotificationsState.Failed -> MessageView(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.notifications_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is NotificationsState.Loaded -> if (state.items.isEmpty()) {
                MessageView(
                    icon = Icons.Outlined.NotificationsNone,
                    title = stringResource(R.string.notifications_empty_title),
                    body = stringResource(R.string.notifications_empty_body),
                )
            } else {
                if (state.items.any { !it.read }) {
                    TextButton(onClick = viewModel::markAllRead, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                        Text(stringResource(R.string.action_mark_all_read))
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { notification ->
                        NotificationRow(notification, onClick = {
                            viewModel.onOpened(notification)
                            onOpen(notification)
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    val background = if (notification.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(notification.text(), style = MaterialTheme.typography.bodyLarge)
        Text(
            relativeTime(notification.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppNotification.text(): String {
    val who = actorName ?: stringResource(R.string.author_unknown)
    return when (kind) {
        NotificationKind.NEW_MESSAGE -> stringResource(R.string.notification_new_message, who)
        NotificationKind.NEW_COMMENT -> stringResource(R.string.notification_new_comment, who)
        NotificationKind.VERIFICATION_APPROVED -> stringResource(R.string.notification_verification_approved)
        NotificationKind.VERIFICATION_REJECTED -> stringResource(R.string.notification_verification_rejected)
    }
}

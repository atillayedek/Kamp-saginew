package com.kampusagi.android.presentation.settings

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.domain.model.ThemeMode
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.BlockedUser
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppearanceSection()

            HorizontalDivider()
            Text(stringResource(R.string.settings_blocked_title), style = MaterialTheme.typography.titleMedium)
            when (val blocked = viewModel.blocked) {
                BlockedListState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm))
                is BlockedListState.Failed -> {
                    ErrorText(stringResource(blocked.error.messageRes()))
                    SecondaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::loadBlocked)
                }
                is BlockedListState.Loaded -> if (blocked.users.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_blocked_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    blocked.users.forEach { user ->
                        BlockedRow(user, busy = viewModel.unblockingId == user.userId, enabled = viewModel.unblockingId == null) {
                            viewModel.unblock(user)
                        }
                    }
                }
            }
            viewModel.actionError?.let { ErrorText(stringResource(it.messageRes())) }

            HorizontalDivider()
            Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_privacy_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(text = stringResource(R.string.privacy_title), onClick = onOpenPrivacy)
        }
    }
}

@Composable
private fun BlockedRow(user: BlockedUser, busy: Boolean, enabled: Boolean, onUnblock: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(user.fullName ?: user.username?.let { "@$it" } ?: stringResource(R.string.author_unknown))
            user.username?.let {
                Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm).size(24.dp))
        } else {
            TextButton(onClick = onUnblock, enabled = enabled) { Text(stringResource(R.string.action_unblock)) }
        }
    }
}

@Composable
private fun AppearanceSection(viewModel: AppearanceViewModel = hiltViewModel()) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.settings_appearance_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ThemeMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = { viewModel.select(option) },
                    label = { Text(stringResource(option.labelRes())) },
                    leadingIcon = {
                        Icon(
                            when (option) {
                                ThemeMode.SYSTEM -> AppIcons.Contrast
                                ThemeMode.LIGHT -> AppIcons.LightMode
                                ThemeMode.DARK -> AppIcons.DarkMode
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** Account deletion, offered wherever a signed-in person can reach it. */
@Composable
fun DeleteAccountSection(modifier: Modifier = Modifier, viewModel: DeleteAccountViewModel = hiltViewModel()) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.delete_account_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.delete_account_summary_grace),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (viewModel.deleting) {
            CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm))
        } else {
            TextButton(onClick = viewModel::requestDelete) {
                Text(stringResource(R.string.action_delete_account), color = MaterialTheme.colorScheme.error)
            }
        }
        viewModel.error?.let { ErrorText(stringResource(it.messageRes())) }
    }
    if (viewModel.confirming) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(stringResource(R.string.delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.delete_account_confirm_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirm) {
                    Text(stringResource(R.string.action_delete_account), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

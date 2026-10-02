package com.kampusagi.android.presentation.requirement

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.Requirement
import com.kampusagi.android.domain.model.RequirementStatus
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.common.relativeTime

@Composable
fun RequirementsScreen(
    viewModel: RequirementsViewModel,
    onCreate: () -> Unit,
    onOpenMatches: (Requirement) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val state = viewModel.state) {
            RequirementsState.Loading -> LoadingView()
            is RequirementsState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.requirements_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is RequirementsState.Loaded -> if (state.items.isEmpty()) {
                MessageView(
                    icon = AppIcons.Lightbulb,
                    title = stringResource(R.string.requirements_empty_title),
                    body = stringResource(R.string.requirements_empty_body),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = 88.dp),
                ) {
                    viewModel.actionError?.let { error ->
                        item {
                            Text(
                                stringResource(error.messageRes()),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    items(state.items, key = { it.id }) { requirement ->
                        RequirementCard(
                            requirement = requirement,
                            closing = viewModel.closingId == requirement.id,
                            enabled = viewModel.closingId == null,
                            onClose = { viewModel.close(requirement) },
                            onOpenMatches = { onOpenMatches(requirement) },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(AppIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.action_new_requirement)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
        )
    }
}

@Composable
private fun RequirementCard(
    requirement: Requirement,
    closing: Boolean,
    enabled: Boolean,
    onClose: () -> Unit,
    onOpenMatches: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(requirement.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(
                    stringResource(requirement.category.labelRes()),
                    relativeTime(requirement.createdAt),
                    stringResource(
                        if (requirement.status == RequirementStatus.ACTIVE) R.string.requirement_active else R.string.requirement_closed,
                    ),
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (requirement.description.isNotBlank()) {
                Text(requirement.description, style = MaterialTheme.typography.bodyMedium)
            }
            formatStartsAt(requirement.startsAt)?.let {
                Text(stringResource(R.string.requirement_starts_at, it), style = MaterialTheme.typography.bodySmall)
            }
            requirement.locationText?.let {
                Text(stringResource(R.string.requirement_location, it), style = MaterialTheme.typography.bodySmall)
            }
            if (requirement.tags.isNotEmpty()) {
                Text(requirement.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
            }
            if (requirement.status == RequirementStatus.ACTIVE) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onOpenMatches) { Text(stringResource(R.string.action_show_matches)) }
                    OutlinedButton(onClick = onClose, enabled = enabled) {
                        Text(stringResource(if (closing) R.string.admin_working else R.string.action_close_requirement))
                    }
                }
            }
        }
    }
}

package com.kampusagi.android.presentation.admin

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.OpenReport
import com.kampusagi.android.domain.model.ReportAction
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.moderation.labelRes
import com.kampusagi.android.presentation.moderation.titleRes

@Composable
fun AdminReportsContent(modifier: Modifier = Modifier, viewModel: AdminReportsViewModel = hiltViewModel()) {
    Column(modifier = modifier.fillMaxSize()) {
        viewModel.actionError?.let { error ->
            Text(
                stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
        when (val list = viewModel.list) {
            ReportListState.Loading -> LoadingView()
            is ReportListState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.admin_reports_load_failed),
                body = stringResource(list.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::refresh)
            }
            is ReportListState.Loaded -> if (list.reports.isEmpty()) {
                MessageView(
                    icon = AppIcons.Inbox,
                    title = stringResource(R.string.admin_reports_empty_title),
                    body = stringResource(R.string.admin_reports_empty_body),
                ) {
                    PrimaryButton(text = stringResource(R.string.action_refresh_status), onClick = viewModel::refresh)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    contentPadding = PaddingValues(Spacing.md),
                ) {
                    items(list.reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            busy = viewModel.busyId == report.id,
                            enabled = viewModel.busyId == null,
                            onAction = { viewModel.request(report, it) },
                        )
                    }
                }
            }
        }
    }

    viewModel.pending?.let { decision ->
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(stringResource(decision.action.labelRes())) },
            text = { Text(stringResource(decision.action.confirmRes())) },
            confirmButton = { TextButton(onClick = viewModel::confirm) { Text(stringResource(R.string.action_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportCard(report: OpenReport, busy: Boolean, enabled: Boolean, onAction: (ReportAction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(report.target.titleRes()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.admin_report_reason, stringResource(report.reason.labelRes()), report.reportCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(report.excerpt, style = MaterialTheme.typography.bodyLarge, maxLines = 10)
            report.details?.let {
                Text(stringResource(R.string.admin_report_details, it), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(
                    R.string.admin_report_people,
                    report.targetFullName ?: report.targetUsername.orEmpty(),
                    report.reporterUsername.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.targetSuspended) {
                Text(
                    stringResource(R.string.admin_report_user_suspended),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (busy) {
                CircularProgressIndicator()
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = { onAction(ReportAction.DISMISS) }, enabled = enabled) {
                        Text(stringResource(ReportAction.DISMISS.labelRes()))
                    }
                    if (report.target != ReportTarget.USER) {
                        OutlinedButton(onClick = { onAction(ReportAction.REMOVE_CONTENT) }, enabled = enabled) {
                            Text(stringResource(ReportAction.REMOVE_CONTENT.labelRes()))
                        }
                    }
                    if (!report.targetSuspended) {
                        OutlinedButton(onClick = { onAction(ReportAction.SUSPEND_USER) }, enabled = enabled) {
                            Text(stringResource(ReportAction.SUSPEND_USER.labelRes()))
                        }
                    }
                }
            }
        }
    }
}

private fun ReportAction.labelRes(): Int = when (this) {
    ReportAction.DISMISS -> R.string.admin_action_dismiss
    ReportAction.REMOVE_CONTENT -> R.string.admin_action_remove
    ReportAction.SUSPEND_USER -> R.string.admin_action_suspend
}

private fun ReportAction.confirmRes(): Int = when (this) {
    ReportAction.DISMISS -> R.string.admin_confirm_dismiss
    ReportAction.REMOVE_CONTENT -> R.string.admin_confirm_remove
    ReportAction.SUSPEND_USER -> R.string.admin_confirm_suspend
}

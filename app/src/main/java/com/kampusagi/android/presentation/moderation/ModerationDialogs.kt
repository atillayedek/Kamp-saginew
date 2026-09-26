package com.kampusagi.android.presentation.moderation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget

const val MAX_REPORT_DETAILS = 500

/** Something the person asked to report. */
data class ReportRequest(val target: ReportTarget, val id: String)

/** One entry of an overflow menu. */
data class MenuAction(@StringRes val label: Int, val onClick: () -> Unit)

@Composable
fun OverflowMenu(actions: List<MenuAction>, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun ReportDialog(
    target: ReportTarget,
    submitting: Boolean,
    onSubmit: (ReportReason, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(target.titleRes())) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(stringResource(R.string.report_body), style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup()) {
                    ReportReason.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = reason == option, role = Role.RadioButton, onClick = { reason = option }),
                        ) {
                            RadioButton(selected = reason == option, onClick = null)
                            Text(stringResource(option.labelRes()), modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= MAX_REPORT_DETAILS) details = it },
                    label = { Text(stringResource(R.string.report_details)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let { onSubmit(it, details.trim().ifEmpty { null }) } },
                enabled = reason != null && !submitting,
            ) { Text(stringResource(R.string.action_send_report)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun BlockDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_title, name)) },
        text = { Text(stringResource(R.string.block_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_block)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@StringRes
fun ReportReason.labelRes(): Int = when (this) {
    ReportReason.SPAM -> R.string.report_reason_spam
    ReportReason.HARASSMENT -> R.string.report_reason_harassment
    ReportReason.INAPPROPRIATE -> R.string.report_reason_inappropriate
    ReportReason.FAKE_PROFILE -> R.string.report_reason_fake_profile
    ReportReason.OTHER -> R.string.report_reason_other
}

@StringRes
fun ReportTarget.titleRes(): Int = when (this) {
    ReportTarget.POST -> R.string.report_post
    ReportTarget.COMMENT -> R.string.report_comment
    ReportTarget.USER -> R.string.report_user
    ReportTarget.MESSAGE -> R.string.report_message
}

package com.kampusagi.android.presentation.admin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.PendingVerification
import com.kampusagi.android.domain.usecase.RejectionReasonValidator
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReviewScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminReviewViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var noViewer by rememberSaveable { mutableStateOf(false) }
    var rejecting by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.documentsToOpen.collect { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(intent)
                noViewer = false
            } catch (e: ActivityNotFoundException) {
                Log.w("AdminReview", "No PDF viewer installed", e)
                noViewer = true
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.admin_panel_title)) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.admin_tab_verifications)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.admin_tab_reports)) })
        }
        if (tab == 1) {
            AdminReportsContent()
        } else {
            val error: AppError? = viewModel.actionError
            if (error != null || noViewer) {
                Text(
                    text = if (noViewer) stringResource(R.string.admin_no_pdf_viewer) else stringResource(error!!.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
            when (val list = viewModel.list) {
                PendingListState.Loading -> LoadingView()
                is PendingListState.Failed -> MessageView(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.admin_load_failed),
                    body = stringResource(list.error.messageRes()),
                ) {
                    PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::refresh)
                }
                is PendingListState.Loaded -> if (list.items.isEmpty()) {
                    MessageView(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(R.string.admin_empty_title),
                        body = stringResource(R.string.admin_empty_body),
                    ) {
                        PrimaryButton(text = stringResource(R.string.action_refresh_status), onClick = viewModel::refresh)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
                    ) {
                        items(list.items, key = { it.verificationId }) { item ->
                            PendingCard(
                                item = item,
                                busy = viewModel.busyId == item.verificationId,
                                enabled = viewModel.busyId == null,
                                onOpen = { viewModel.openDocument(item) },
                                onApprove = { viewModel.approve(item) },
                                onReject = { rejecting = item.verificationId },
                            )
                        }
                    }
                }
            }
        }
    }

    val loaded = viewModel.list as? PendingListState.Loaded
    val target = loaded?.items?.firstOrNull { it.verificationId == rejecting }
    if (target != null) {
        RejectDialog(
            onConfirm = { reason ->
                viewModel.reject(target, reason)
                rejecting = null
            },
            onDismiss = { rejecting = null },
        )
    }
}

@Composable
private fun PendingCard(
    item: PendingVerification,
    busy: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.fullName ?: item.email, style = MaterialTheme.typography.titleMedium)
            item.username?.let { Text("@$it", style = MaterialTheme.typography.bodySmall) }
            Text(item.email, style = MaterialTheme.typography.bodySmall)
            Text(
                listOfNotNull(item.universityName, item.department).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.admin_submitted_at, item.submittedAt.take(16).replace('T', ' ')),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (busy) R.string.admin_working else R.string.action_open_document))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReject, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_reject))
                }
                Button(onClick = onApprove, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_approve))
                }
            }
        }
    }
}

@Composable
private fun RejectDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    val valid = RejectionReasonValidator.isValid(reason)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_reject_title)) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { if (it.length <= RejectionReasonValidator.LENGTH.last) reason = it },
                label = { Text(stringResource(R.string.admin_reject_reason)) },
                supportingText = { Text(stringResource(R.string.admin_reject_reason_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = valid) { Text(stringResource(R.string.action_reject)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

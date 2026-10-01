package com.kampusagi.android.presentation.status

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AccountStatus
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.domain.model.VerificationStatus
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.settings.DeleteAccountSection

/**
 * Shows where the account is in the verification lifecycle. The status comes
 * from `profiles.account_status`, which only the backend can change.
 */
@Composable
fun AccountStatusScreen(
    profile: Profile,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onEditProfile: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerificationViewModel = hiltViewModel(),
) {
    val canUpload = profile.status == AccountStatus.DOCUMENT_REQUIRED || profile.status == AccountStatus.REJECTED
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onDocumentPicked(uri.toString())
    }
    // The latest request (and its rejection reason) changes whenever the status does.
    LaunchedEffect(profile.status) { viewModel.loadLatest() }

    val content = profile.status.content()
    MessageView(
        icon = content.icon,
        title = stringResource(content.title),
        body = stringResource(content.body),
        modifier = modifier,
    ) {
        if (profile.status == AccountStatus.REJECTED) RejectionReason(viewModel.latest)
        if (canUpload) {
            viewModel.uploadError?.let { error ->
                Text(
                    stringResource(error.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.document_requirements),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // KVKK: purpose, who sees it and how long it is kept, before anything is uploaded.
            Text(
                viewModel.uploadNotice ?: stringResource(R.string.document_upload_notice_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.action_upload_document),
                onClick = { picker.launch(arrayOf(PDF_MIME_TYPE)) },
                loading = viewModel.isUploading,
            )
            SecondaryButton(
                text = stringResource(R.string.action_edit_profile),
                onClick = onEditProfile,
                enabled = !viewModel.isUploading,
            )
        }
        if (isAdmin) {
            SecondaryButton(text = stringResource(R.string.action_open_admin), onClick = onOpenAdmin)
        }
        SecondaryButton(
            text = stringResource(R.string.action_refresh_status),
            onClick = onRefresh,
            enabled = !viewModel.isUploading,
        )
        LinkButton(text = stringResource(R.string.action_sign_out), onClick = onSignOut, enabled = !viewModel.isUploading)
        DeleteAccountSection()
    }
}

@Composable
private fun RejectionReason(state: LatestVerificationState) {
    val text = when (state) {
        LatestVerificationState.Loading -> return
        is LatestVerificationState.Failed -> stringResource(state.error.messageRes())
        is LatestVerificationState.Loaded -> state.verification
            ?.takeIf { it.status == VerificationStatus.REJECTED }
            ?.rejectionReason
            ?.let { stringResource(R.string.rejection_reason, it) }
            ?: return
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val PDF_MIME_TYPE = "application/pdf"

private data class StatusContent(val icon: ImageVector, val title: Int, val body: Int)

@Composable
private fun AccountStatus.content(): StatusContent = when (this) {
    AccountStatus.PROFILE_INCOMPLETE, AccountStatus.DOCUMENT_REQUIRED -> StatusContent(
        AppIcons.UploadFile,
        R.string.status_document_required_title,
        R.string.status_document_required_body,
    )
    AccountStatus.PENDING_REVIEW -> StatusContent(
        AppIcons.HourglassTop,
        R.string.status_pending_title,
        R.string.status_pending_body,
    )
    AccountStatus.APPROVED -> StatusContent(
        AppIcons.CheckCircle,
        R.string.status_approved_title,
        R.string.status_approved_body,
    )
    AccountStatus.REJECTED -> StatusContent(
        AppIcons.Error,
        R.string.status_rejected_title,
        R.string.status_rejected_body,
    )
    AccountStatus.SUSPENDED -> StatusContent(
        AppIcons.Block,
        R.string.status_suspended_title,
        R.string.status_suspended_body,
    )
}

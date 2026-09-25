package com.kampusagi.android.presentation.status

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.domain.model.AccountStatus
import com.kampusagi.android.domain.model.Profile

/**
 * Shows where the account is in the verification lifecycle. The status comes
 * from `profiles.account_status`, which only the backend can change.
 */
@Composable
fun AccountStatusScreen(
    profile: Profile,
    onEditProfile: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = profile.status.content()
    MessageView(
        icon = content.icon,
        title = stringResource(content.title),
        body = stringResource(content.body),
        modifier = modifier,
    ) {
        if (profile.status == AccountStatus.DOCUMENT_REQUIRED) {
            SecondaryButton(text = stringResource(R.string.action_edit_profile), onClick = onEditProfile)
        }
        SecondaryButton(text = stringResource(R.string.action_refresh_status), onClick = onRefresh)
        LinkButton(text = stringResource(R.string.action_sign_out), onClick = onSignOut)
    }
}

private data class StatusContent(val icon: ImageVector, val title: Int, val body: Int)

private fun AccountStatus.content(): StatusContent = when (this) {
    AccountStatus.PROFILE_INCOMPLETE, AccountStatus.DOCUMENT_REQUIRED -> StatusContent(
        Icons.Outlined.UploadFile,
        R.string.status_document_required_title,
        R.string.status_document_required_body,
    )
    AccountStatus.PENDING_REVIEW -> StatusContent(
        Icons.Outlined.HourglassTop,
        R.string.status_pending_title,
        R.string.status_pending_body,
    )
    AccountStatus.APPROVED -> StatusContent(
        Icons.Outlined.CheckCircle,
        R.string.status_approved_title,
        R.string.status_approved_body,
    )
    AccountStatus.REJECTED -> StatusContent(
        Icons.Outlined.ErrorOutline,
        R.string.status_rejected_title,
        R.string.status_rejected_body,
    )
    AccountStatus.SUSPENDED -> StatusContent(
        Icons.Outlined.Block,
        R.string.status_suspended_title,
        R.string.status_suspended_body,
    )
}

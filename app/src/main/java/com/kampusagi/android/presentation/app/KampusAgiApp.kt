package com.kampusagi.android.presentation.app

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.presentation.admin.AdminReviewScreen
import com.kampusagi.android.presentation.auth.AuthNavHost
import com.kampusagi.android.presentation.auth.PasswordRecoveryScreen
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.main.MainScreen
import com.kampusagi.android.presentation.profile.ProfileSetupScreen
import com.kampusagi.android.presentation.status.AccountStatusScreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KampusAgiApp(state: AppUiState, viewModel: RootViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val openNotificationsRequested by viewModel.openNotificationsRequested.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(context.getString(message.textRes()))
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        // System bar insets are applied here once; nested bars and scaffolds must not add them again.
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        when (state.destination) {
            RootDestination.LOADING -> LoadingView(modifier)
            RootDestination.SETUP_REQUIRED -> MessageView(
                icon = AppIcons.Tune,
                title = stringResource(R.string.setup_required_title),
                body = stringResource(R.string.setup_required_body),
                modifier = modifier,
            )
            RootDestination.AUTH -> AuthNavHost(modifier = modifier)
            RootDestination.PASSWORD_RECOVERY -> PasswordRecoveryScreen(
                onFinished = viewModel::onPasswordRecoveryFinished,
                modifier = modifier,
            )
            RootDestination.PROFILE_ERROR -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.profile_error_title),
                body = stringResource((state.profileError ?: AppError.UNKNOWN).messageRes()),
                modifier = modifier,
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::retryProfile)
                if (state.isAdmin) {
                    SecondaryButton(text = stringResource(R.string.action_open_admin), onClick = viewModel::openAdminReview)
                }
                SecondaryButton(text = stringResource(R.string.action_sign_out), onClick = viewModel::signOut)
            }
            RootDestination.PROFILE_SETUP -> ProfileSetupScreen(
                existingProfile = state.profile,
                onDone = viewModel::onProfileEditFinished,
                onSignOut = viewModel::signOut,
                modifier = modifier,
            )
            RootDestination.ACCOUNT_STATUS -> state.profile?.let { profile ->
                AccountStatusScreen(
                    profile = profile,
                    isAdmin = state.isAdmin,
                    onOpenAdmin = viewModel::openAdminReview,
                    onEditProfile = viewModel::editProfile,
                    onRefresh = viewModel::retryProfile,
                    onSignOut = viewModel::signOut,
                    modifier = modifier,
                )
            } ?: LoadingView(modifier)
            RootDestination.ADMIN_REVIEW -> AdminReviewScreen(onClose = viewModel::closeAdminReview, modifier = modifier)
            RootDestination.MAIN -> state.profile?.let { profile ->
                MainScreen(
                    profile = profile,
                    isAdmin = state.isAdmin,
                    onOpenAdmin = viewModel::openAdminReview,
                    onSignOut = viewModel::signOut,
                    openNotificationsRequested = openNotificationsRequested,
                    onNotificationsOpened = viewModel::onNotificationsOpened,
                    modifier = modifier,
                )
            } ?: LoadingView(modifier)
        }
    }
}

private fun AppMessage.textRes(): Int = when (this) {
    AppMessage.EMAIL_CONFIRMED -> R.string.message_email_confirmed
    AppMessage.LINK_EXPIRED -> R.string.message_link_expired
    AppMessage.NETWORK_ERROR -> R.string.error_network
    AppMessage.GENERIC_ERROR -> R.string.error_unknown
    AppMessage.PASSWORD_UPDATED -> R.string.message_password_updated
    AppMessage.SIGN_OUT_FAILED -> R.string.message_sign_out_failed
}

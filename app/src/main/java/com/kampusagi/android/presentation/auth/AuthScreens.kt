package com.kampusagi.android.presentation.auth

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.AppTextField
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PasswordField
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.domain.usecase.AuthInputError
import com.kampusagi.android.domain.usecase.AuthInputValidator
import com.kampusagi.android.presentation.common.messageRes

@Composable
fun SignInScreen(
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    AuthScaffold(
        title = stringResource(R.string.sign_in_title),
        subtitle = stringResource(R.string.sign_in_subtitle),
        modifier = modifier,
    ) {
        ErrorBanner(state.error)
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.field_email),
            error = state.inputErrors.messageFor(AuthInputError.EMAIL_INVALID),
            enabled = !state.isSubmitting,
            keyboardType = KeyboardType.Email,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.field_password),
            error = state.inputErrors.messageFor(AuthInputError.PASSWORD_REQUIRED),
            enabled = !state.isSubmitting,
            onImeAction = viewModel::submit,
        )
        PrimaryButton(
            text = stringResource(R.string.action_sign_in),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinkButton(stringResource(R.string.action_forgot_password), onForgotPassword, enabled = !state.isSubmitting)
            LinkButton(stringResource(R.string.action_create_account), onCreateAccount, enabled = !state.isSubmitting)
        }
    }
}

@Composable
fun SignUpScreen(
    onVerificationSent: (String) -> Unit,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val sentTo = viewModel.verificationSentTo
    LaunchedEffect(sentTo) {
        if (sentTo != null) {
            viewModel.onVerificationScreenShown()
            onVerificationSent(sentTo)
        }
    }
    AuthScaffold(
        title = stringResource(R.string.sign_up_title),
        subtitle = stringResource(R.string.sign_up_subtitle),
        modifier = modifier,
    ) {
        ErrorBanner(state.error)
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.field_email),
            error = state.inputErrors.messageFor(AuthInputError.EMAIL_INVALID),
            supportingText = stringResource(R.string.field_email_hint_student),
            enabled = !state.isSubmitting,
            keyboardType = KeyboardType.Email,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.field_password),
            error = state.inputErrors.messageFor(AuthInputError.PASSWORD_TOO_SHORT),
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Next,
        )
        PasswordField(
            value = state.confirmation,
            onValueChange = viewModel::onConfirmationChange,
            label = stringResource(R.string.field_password_confirmation),
            error = state.inputErrors.messageFor(AuthInputError.PASSWORDS_DO_NOT_MATCH),
            enabled = !state.isSubmitting,
            onImeAction = viewModel::submit,
        )
        Text(
            text = stringResource(R.string.password_rule, AuthInputValidator.MIN_PASSWORD_LENGTH),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TermsConsent(
            accepted = viewModel.acceptedTerms,
            onAcceptedChange = viewModel::onAcceptedTermsChange,
            termsUrl = viewModel.termsUrl,
            privacyPolicyUrl = viewModel.privacyPolicyUrl,
            enabled = !state.isSubmitting,
        )
        PrimaryButton(
            text = stringResource(R.string.action_create_account),
            onClick = viewModel::submit,
            enabled = viewModel.acceptedTerms,
            loading = state.isSubmitting,
        )
        LinkButton(stringResource(R.string.action_have_account), onBackToSignIn, enabled = !state.isSubmitting)
    }
}

@Composable
fun VerifyEmailScreen(
    email: String,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val body = buildString {
        append(stringResource(R.string.verify_email_body, email))
        if (state.resent) append("\n\n").append(stringResource(R.string.verify_email_resent))
        state.error?.let { append("\n\n").append(stringResource(it.messageRes())) }
    }
    MessageView(
        icon = AppIcons.MarkEmailRead,
        title = stringResource(R.string.verify_email_title),
        body = body,
        modifier = modifier,
    ) {
        SecondaryButton(
            text = stringResource(R.string.action_resend_email),
            onClick = { viewModel.resend(email) },
            enabled = !state.isSending,
        )
        LinkButton(stringResource(R.string.action_back_to_sign_in), onBackToSignIn)
    }
}

@Composable
fun ForgotPasswordScreen(
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    if (viewModel.sent) {
        MessageView(
            icon = AppIcons.MarkEmailRead,
            title = stringResource(R.string.forgot_password_sent_title),
            body = stringResource(R.string.forgot_password_sent_body, state.email.trim()),
            modifier = modifier,
        ) {
            LinkButton(stringResource(R.string.action_back_to_sign_in), onBackToSignIn)
        }
        return
    }
    AuthScaffold(
        title = stringResource(R.string.forgot_password_title),
        subtitle = stringResource(R.string.forgot_password_subtitle),
        modifier = modifier,
    ) {
        ErrorBanner(state.error)
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.field_email),
            error = state.inputErrors.messageFor(AuthInputError.EMAIL_INVALID),
            enabled = !state.isSubmitting,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submit,
        )
        PrimaryButton(
            text = stringResource(R.string.action_send_reset_link),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )
        LinkButton(stringResource(R.string.action_back_to_sign_in), onBackToSignIn, enabled = !state.isSubmitting)
    }
}

/** Shown after the person opened a password reset link; they are signed in with a recovery session. */
@Composable
fun PasswordRecoveryScreen(
    onFinished: (updated: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PasswordRecoveryViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    LaunchedEffect(viewModel.completed) {
        if (viewModel.completed) onFinished(true)
    }
    AuthScaffold(
        title = stringResource(R.string.new_password_title),
        subtitle = stringResource(R.string.new_password_subtitle),
        modifier = modifier,
    ) {
        ErrorBanner(state.error)
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.field_new_password),
            error = state.inputErrors.messageFor(AuthInputError.PASSWORD_TOO_SHORT),
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Next,
        )
        PasswordField(
            value = state.confirmation,
            onValueChange = viewModel::onConfirmationChange,
            label = stringResource(R.string.field_password_confirmation),
            error = state.inputErrors.messageFor(AuthInputError.PASSWORDS_DO_NOT_MATCH),
            enabled = !state.isSubmitting,
            onImeAction = viewModel::submit,
        )
        PrimaryButton(
            text = stringResource(R.string.action_save_password),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )
        LinkButton(stringResource(R.string.action_cancel), { onFinished(false) }, enabled = !state.isSubmitting)
    }
}

/** Required before sign-up: age (18+) and acceptance of the terms and privacy policy, each openable. */
@Composable
private fun TermsConsent(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    termsUrl: String,
    privacyPolicyUrl: String,
    enabled: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: android.content.ActivityNotFoundException) {
            android.util.Log.w("SignUp", "No browser to open $url", e)
        }
    }
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
        androidx.compose.material3.Checkbox(checked = accepted, onCheckedChange = onAcceptedChange, enabled = enabled)
        androidx.compose.foundation.layout.Column {
            Text(
                text = stringResource(R.string.sign_up_consent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            androidx.compose.foundation.layout.Row {
                if (termsUrl.isNotEmpty()) {
                    androidx.compose.material3.TextButton(onClick = { open(termsUrl) }) { Text(stringResource(R.string.terms_of_use)) }
                }
                if (privacyPolicyUrl.isNotEmpty()) {
                    androidx.compose.material3.TextButton(onClick = { open(privacyPolicyUrl) }) { Text(stringResource(R.string.privacy_policy)) }
                }
            }
        }
    }
}

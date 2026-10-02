package com.kampusagi.android.presentation.auth

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.LegalDocTypes
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    onOpenLegal: (String) -> Unit,
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
        // 5651 / KVKK: people are told before signing in that access records are kept.
        Column {
            Text(
                viewModel.loginNotice ?: stringResource(R.string.sign_in_log_notice_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinkButton(stringResource(R.string.privacy_notice), { onOpenLegal(LegalDocTypes.PRIVACY_NOTICE) })
        }
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
    onOpenLegal: (String) -> Unit,
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
        when (val legal = viewModel.legal) {
            SignUpLegalState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm))
            is SignUpLegalState.Failed -> {
                ErrorBanner(legal.error)
                SecondaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::loadLegal)
            }
            is SignUpLegalState.Ready -> SignUpLegalSection(
                viewModel = viewModel,
                registerNotice = legal.config.registerLogNotice,
                minAge = legal.config.minAge,
                onOpenLegal = onOpenLegal,
                enabled = !state.isSubmitting,
            )
        }
        PrimaryButton(
            text = stringResource(R.string.action_create_account),
            onClick = viewModel::submit,
            enabled = viewModel.legal is SignUpLegalState.Ready && viewModel.noticeRead && viewModel.termsAccepted,
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

/**
 * KVKK sign-up section: birth date (age check only, not stored), the log notice, "I have read the
 * privacy notice" (information, not consent), the required terms + community rules box and each
 * optional explicit consent on its own. Nothing is pre-ticked; refusing a consent changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SignUpLegalSection(
    viewModel: SignUpViewModel,
    registerNotice: String,
    minAge: Int,
    onOpenLegal: (String) -> Unit,
    enabled: Boolean,
) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val errors = viewModel.state.inputErrors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = { pickDate = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(
                viewModel.birthDate?.let { stringResource(R.string.sign_up_birth_date_value, it.format(DATE_FORMAT)) }
                    ?: stringResource(R.string.sign_up_birth_date_pick, minAge),
            )
        }
        errors.messageFor(AuthInputError.BIRTH_DATE_REQUIRED, AuthInputError.UNDERAGE)?.let { ErrorLine(it) }

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(registerNotice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Spacing.sm))
        }

        ConsentRow(
            checked = viewModel.noticeRead,
            onCheckedChange = viewModel::onNoticeReadChange,
            text = stringResource(R.string.sign_up_notice_read),
            enabled = enabled,
        ) {
            LinkButton(stringResource(R.string.privacy_notice), { onOpenLegal(LegalDocTypes.PRIVACY_NOTICE) })
            LinkButton(stringResource(R.string.privacy_policy), { onOpenLegal(LegalDocTypes.PRIVACY_POLICY) })
        }
        errors.messageFor(AuthInputError.NOTICE_NOT_READ)?.let { ErrorLine(it) }

        ConsentRow(
            checked = viewModel.termsAccepted,
            onCheckedChange = viewModel::onTermsAcceptedChange,
            text = stringResource(R.string.sign_up_terms_accept, minAge),
            enabled = enabled,
        ) {
            LinkButton(stringResource(R.string.terms_of_use), { onOpenLegal(LegalDocTypes.TERMS) })
            LinkButton(stringResource(R.string.community_rules), { onOpenLegal(LegalDocTypes.COMMUNITY_RULES) })
        }
        errors.messageFor(AuthInputError.TERMS_NOT_ACCEPTED)?.let { ErrorLine(it) }

        if (viewModel.consentDocuments.isNotEmpty()) {
            Text(
                stringResource(R.string.sign_up_optional_consents),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            viewModel.consentDocuments.forEach { doc ->
                ConsentRow(
                    checked = viewModel.optionalConsents[doc.docType] == true,
                    onCheckedChange = { viewModel.onConsentChange(doc.docType, it) },
                    text = doc.title,
                    enabled = enabled,
                ) {
                    LinkButton(stringResource(R.string.action_read), { onOpenLegal(doc.docType) })
                }
            }
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialDisplayMode = DisplayMode.Input)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onBirthDateChange(
                        state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() },
                    )
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    enabled: Boolean,
    links: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            FlowRow { links() }
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))

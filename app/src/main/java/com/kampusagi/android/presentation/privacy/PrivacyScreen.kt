package com.kampusagi.android.presentation.privacy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.ConsentAction
import com.kampusagi.android.domain.model.DataRequestStatus
import com.kampusagi.android.domain.model.DataRequestType
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.moderation.labelRes
import com.kampusagi.android.presentation.settings.DeleteAccountSection
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onOpenLegal: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    // Opened from a notification: the decisions and requests sit low on the page.
    LaunchedEffect(viewModel.initialSection) {
        if (viewModel.initialSection != null) scroll.animateScrollTo(scroll.maxValue)
    }
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Log.w("Privacy", "No browser for the data export link", e)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.privacy_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            },
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            viewModel.actionError?.let { ErrorText(stringResource(it.messageRes())) }

            // Consents --------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.privacy_consents_title))
            Hint(stringResource(R.string.privacy_consents_hint))
            when (val consents = viewModel.consents) {
                Loadable.Loading -> Progress()
                is Loadable.Failed -> Retry(consents.error.messageRes(), viewModel::reload)
                is Loadable.Loaded -> consents.value.forEach { consent ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
                            Text(consent.title, style = MaterialTheme.typography.bodyMedium)
                            consent.changedAt?.let {
                                Hint(stringResource(if (consent.granted) R.string.privacy_consent_given_at else R.string.privacy_consent_off_since, date(it)))
                            }
                            LinkButton(stringResource(R.string.action_read), { onOpenLegal(consent.docType) })
                        }
                        Switch(
                            checked = consent.granted,
                            onCheckedChange = { viewModel.setConsent(consent.docType, it) },
                            enabled = viewModel.savingConsent == null,
                        )
                    }
                }
            }
            TextButton(onClick = viewModel::toggleHistory) {
                Text(stringResource(if (viewModel.history == null) R.string.privacy_history_show else R.string.privacy_history_hide))
            }
            when (val history = viewModel.history) {
                null -> Unit
                Loadable.Loading -> Progress()
                is Loadable.Failed -> ErrorText(stringResource(history.error.messageRes()))
                is Loadable.Loaded -> history.value.forEach { event ->
                    Hint(
                        stringResource(
                            R.string.privacy_history_row,
                            date(event.at),
                            event.docType,
                            event.version ?: 0,
                            stringResource(
                                when (event.action) {
                                    ConsentAction.ACCEPTED -> R.string.consent_action_accepted
                                    ConsentAction.DECLINED -> R.string.consent_action_declined
                                    ConsentAction.WITHDRAWN -> R.string.consent_action_withdrawn
                                    ConsentAction.INFORMED -> R.string.consent_action_informed
                                },
                            ),
                        ),
                    )
                }
            }

            HorizontalDivider()
            // Surname visibility ------------------------------------------------------------------
            val showFullName = (profileState as? ProfileState.Loaded)?.profile?.showFullName == true
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
                    Text(stringResource(R.string.privacy_show_full_name), style = MaterialTheme.typography.bodyMedium)
                    Hint(stringResource(R.string.privacy_show_full_name_hint))
                }
                Switch(checked = showFullName, onCheckedChange = viewModel::setShowFullName, enabled = !viewModel.savingName)
            }

            HorizontalDivider()
            // Data export -------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.privacy_export_title))
            Hint(stringResource(R.string.privacy_export_hint))
            val export = viewModel.export
            if (export == null) {
                PrimaryButton(text = stringResource(R.string.privacy_export_action), onClick = viewModel::exportData, loading = viewModel.exporting)
            } else {
                Hint(stringResource(R.string.privacy_export_ready, date(export.expiresAt)))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton(text = stringResource(R.string.privacy_export_open_html), onClick = { open(export.htmlUrl) })
                    LinkButton(stringResource(R.string.privacy_export_open_json), { open(export.jsonUrl) })
                }
            }

            HorizontalDivider()
            // Access records ----------------------------------------------------------------------
            SectionTitle(stringResource(R.string.privacy_access_title))
            Hint(stringResource(R.string.privacy_access_hint))
            TextButton(onClick = viewModel::toggleAccessLogs) {
                Text(stringResource(if (viewModel.accessLogs == null) R.string.privacy_access_show else R.string.privacy_history_hide))
            }
            when (val logs = viewModel.accessLogs) {
                null -> Unit
                Loadable.Loading -> Progress()
                is Loadable.Failed -> ErrorText(stringResource(logs.error.messageRes()))
                is Loadable.Loaded -> if (logs.value.isEmpty()) {
                    Hint(stringResource(R.string.privacy_access_empty))
                } else {
                    logs.value.forEach { entry ->
                        Hint(stringResource(R.string.privacy_access_row, date(entry.occurredAt), accessEvent(entry.event), entry.ip ?: "—", entry.device ?: "—"))
                    }
                }
            }

            HorizontalDivider()
            // KVKK applications -------------------------------------------------------------------
            SectionTitle(stringResource(R.string.privacy_request_title))
            Hint(stringResource(R.string.privacy_request_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DataRequestType.entries.forEach { type ->
                    FilterChip(
                        selected = viewModel.requestType == type,
                        onClick = { viewModel.onRequestTypeChange(type) },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
            OutlinedTextField(
                value = viewModel.requestDetails,
                onValueChange = viewModel::onRequestDetailsChange,
                label = { Text(stringResource(R.string.privacy_request_details)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.privacy_request_send),
                onClick = viewModel::submitRequest,
                enabled = viewModel.canSubmitRequest,
                loading = viewModel.submitting,
            )
            viewModel.submittedRequestNo?.let { Text(stringResource(R.string.privacy_request_sent, it), style = MaterialTheme.typography.bodyMedium) }
            when (val requests = viewModel.requests) {
                Loadable.Loading -> Progress()
                is Loadable.Failed -> ErrorText(stringResource(requests.error.messageRes()))
                is Loadable.Loaded -> requests.value.forEach { request ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            stringResource(R.string.privacy_request_row, request.requestNo, stringResource(request.type.labelRes()), stringResource(request.status.labelRes())),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Hint(stringResource(R.string.privacy_request_dates, date(request.receivedAt), date(request.dueAt)))
                        request.response?.let { Text(stringResource(R.string.privacy_request_answer, it), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            HorizontalDivider()
            // Moderation decisions ----------------------------------------------------------------
            SectionTitle(stringResource(R.string.privacy_decisions_title))
            when (val decisions = viewModel.decisions) {
                Loadable.Loading -> Progress()
                is Loadable.Failed -> ErrorText(stringResource(decisions.error.messageRes()))
                is Loadable.Loaded -> if (decisions.value.isEmpty()) {
                    Hint(stringResource(R.string.privacy_decisions_empty))
                } else {
                    decisions.value.forEach { decision ->
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                stringResource(
                                    if (decision.suspended) R.string.privacy_decision_suspended else R.string.privacy_decision_removed,
                                    stringResource(decision.reason.labelRes()),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            decision.excerpt?.let { Hint("“$it”") }
                            when (decision.appealStatus) {
                                null -> TextButton(onClick = { viewModel.startAppeal(decision) }) { Text(stringResource(R.string.action_appeal)) }
                                "OPEN" -> Hint(stringResource(R.string.privacy_appeal_open))
                                "REVERSED" -> Hint(stringResource(R.string.privacy_appeal_reversed, decision.appealNote.orEmpty()))
                                else -> Hint(stringResource(R.string.privacy_appeal_upheld, decision.appealNote.orEmpty()))
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            // Legal texts --------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.privacy_documents_title))
            when (val documents = viewModel.documents) {
                Loadable.Loading -> Progress()
                is Loadable.Failed -> ErrorText(stringResource(documents.error.messageRes()))
                is Loadable.Loaded -> documents.value.forEach { doc ->
                    LinkButton(stringResource(R.string.privacy_document_row, doc.title, doc.version), { onOpenLegal(doc.docType) })
                }
            }

            HorizontalDivider()
            DeleteAccountSection()
        }
    }

    viewModel.appealing?.let { decision ->
        AlertDialog(
            onDismissRequest = viewModel::cancelAppeal,
            title = { Text(stringResource(R.string.privacy_appeal_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(decision.reason.labelRes()), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = viewModel.appealText,
                        onValueChange = viewModel::onAppealTextChange,
                        label = { Text(stringResource(R.string.privacy_appeal_hint)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::sendAppeal,
                    enabled = viewModel.appealText.trim().length >= PrivacyViewModel.MIN_APPEAL_LENGTH,
                ) { Text(stringResource(R.string.action_send)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelAppeal) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Progress() {
    CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm))
}

@Composable
private fun Retry(message: Int, onRetry: () -> Unit) {
    ErrorText(stringResource(message))
    SecondaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
}

@Composable
private fun accessEvent(event: String): String = stringResource(
    when (event) {
        "login" -> R.string.access_event_login
        "logout" -> R.string.access_event_logout
        "login_failed" -> R.string.access_event_failed
        "password_recovery" -> R.string.access_event_recovery
        "signup" -> R.string.access_event_signup
        else -> R.string.access_event_other
    },
)

private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("tr"))

/** Local date and time of a database timestamp; the raw value if it cannot be read. */
private fun date(iso: String): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME)
} catch (e: java.time.format.DateTimeParseException) {
    Log.w("Privacy", "Unreadable timestamp", e)
    iso
}

private fun DataRequestType.labelRes(): Int = when (this) {
    DataRequestType.ACCESS -> R.string.dsr_type_access
    DataRequestType.PURPOSE -> R.string.dsr_type_purpose
    DataRequestType.THIRD_PARTIES -> R.string.dsr_type_third_parties
    DataRequestType.RECTIFICATION -> R.string.dsr_type_rectification
    DataRequestType.ERASURE -> R.string.dsr_type_erasure
    DataRequestType.NOTIFY_THIRD_PARTIES -> R.string.dsr_type_notify
    DataRequestType.OBJECTION_AUTOMATED -> R.string.dsr_type_objection
    DataRequestType.COMPENSATION -> R.string.dsr_type_compensation
    DataRequestType.OTHER -> R.string.dsr_type_other
}

private fun DataRequestStatus.labelRes(): Int = when (this) {
    DataRequestStatus.RECEIVED -> R.string.dsr_status_received
    DataRequestStatus.IN_PROGRESS -> R.string.dsr_status_in_progress
    DataRequestStatus.ANSWERED -> R.string.dsr_status_answered
    DataRequestStatus.REJECTED -> R.string.dsr_status_rejected
}

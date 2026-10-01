package com.kampusagi.android.presentation.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.LegalDocumentInfo
import com.kampusagi.android.domain.model.LegalKind
import com.kampusagi.android.domain.repository.ComplianceRepository
import com.kampusagi.android.presentation.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Records the acceptance (agreements) or the reading (notices) of the texts that changed. */
@HiltViewModel
class LegalUpdateViewModel @Inject constructor(
    private val compliance: ComplianceRepository,
) : ViewModel() {
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set

    fun confirm(documents: List<LegalDocumentInfo>) {
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            for (document in documents) {
                val result = compliance.acknowledge(document, channel = "login")
                if (result is AppResult.Failure) {
                    error = result.error
                    break
                }
            }
            // Whatever was recorded is no longer pending; the gate decides what is left.
            compliance.refreshAccountGate()
            saving = false
        }
    }
}

/**
 * Shown after sign-in when a legal text changed: agreements (terms, community rules) must be
 * accepted again to continue; changed notices (privacy notice, policy) are shown and marked read.
 */
@Composable
fun LegalUpdateScreen(
    pending: List<LegalDocumentInfo>,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LegalUpdateViewModel = hiltViewModel(),
) {
    var reading by rememberSaveable { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    reading?.let { type ->
        LegalDocumentScreen(onBack = { reading = null }, docType = type, modifier = modifier)
        return
    }
    val agreements = pending.filter { it.kind == LegalKind.AGREEMENT }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.legal_update_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.legal_update_body), style = MaterialTheme.typography.bodyMedium)
        pending.forEach { doc ->
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = doc.docType in checked,
                    onCheckedChange = { on -> checked = if (on) checked + doc.docType else checked - doc.docType },
                    enabled = !viewModel.saving,
                )
                Column {
                    Text(
                        stringResource(
                            if (doc.kind == LegalKind.AGREEMENT) R.string.legal_update_accept else R.string.legal_update_read,
                            doc.title,
                            doc.version,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    LinkButton(stringResource(R.string.action_read), { reading = doc.docType })
                }
            }
        }
        viewModel.error?.let {
            Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = { viewModel.confirm(pending) },
            enabled = pending.all { it.docType in checked },
            loading = viewModel.saving,
        )
        if (agreements.isNotEmpty()) {
            Text(
                stringResource(R.string.legal_update_decline_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SecondaryButton(text = stringResource(R.string.action_sign_out), onClick = onSignOut, enabled = !viewModel.saving)
    }
}

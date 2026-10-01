package com.kampusagi.android.presentation.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import com.kampusagi.android.domain.usecase.PersonalDataDetector

/**
 * Warns before a message or post that contains an identity number, IBAN or phone number is
 * sent. It never blocks: "Yine de gönder" sends it as written.
 */
@Stable
class PersonalDataGuard {
    var found by mutableStateOf<Set<PersonalDataDetector.Kind>>(emptySet())
        private set
    private var pending: (() -> Unit)? = null

    fun send(text: String, action: () -> Unit) {
        val kinds = PersonalDataDetector.find(text)
        if (kinds.isEmpty()) {
            action()
        } else {
            pending = action
            found = kinds
        }
    }

    fun confirm() {
        val action = pending
        dismiss()
        action?.invoke()
    }

    fun dismiss() {
        pending = null
        found = emptySet()
    }
}

@Composable
fun rememberPersonalDataGuard(): PersonalDataGuard {
    val guard = remember { PersonalDataGuard() }
    if (guard.found.isNotEmpty()) {
        val kinds = guard.found.joinToString(", ") { kind ->
            stringResource(
                when (kind) {
                    PersonalDataDetector.Kind.TC_IDENTITY_NUMBER -> R.string.personal_data_tc
                    PersonalDataDetector.Kind.IBAN -> R.string.personal_data_iban
                    PersonalDataDetector.Kind.PHONE_NUMBER -> R.string.personal_data_phone
                },
            )
        }
        AlertDialog(
            onDismissRequest = guard::dismiss,
            title = { Text(stringResource(R.string.personal_data_warning_title)) },
            text = { Text(stringResource(R.string.personal_data_warning_body, kinds)) },
            confirmButton = { TextButton(onClick = guard::confirm) { Text(stringResource(R.string.action_send_anyway)) } },
            dismissButton = { TextButton(onClick = guard::dismiss) { Text(stringResource(R.string.action_edit)) } },
        )
    }
    return guard
}

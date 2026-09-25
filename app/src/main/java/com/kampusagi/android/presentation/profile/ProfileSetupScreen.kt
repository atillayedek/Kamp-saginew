package com.kampusagi.android.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.AppTextField
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.Profile
import com.kampusagi.android.domain.model.University
import com.kampusagi.android.domain.usecase.ProfileInputError
import com.kampusagi.android.presentation.auth.AuthScaffold
import com.kampusagi.android.presentation.auth.ErrorBanner
import com.kampusagi.android.presentation.common.messageRes
import java.util.Locale

@Composable
fun ProfileSetupScreen(
    existingProfile: Profile?,
    onDone: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    LaunchedEffect(existingProfile) { viewModel.prefill(existingProfile) }
    LaunchedEffect(viewModel.saved) { if (viewModel.saved) onDone() }

    val form = viewModel.form
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val universities = viewModel.universities

    AuthScaffold(
        title = stringResource(R.string.profile_setup_title),
        subtitle = stringResource(R.string.profile_setup_subtitle),
        modifier = modifier,
    ) {
        ErrorBanner(form.error)
        AppTextField(
            value = form.fullName,
            onValueChange = viewModel::onFullNameChange,
            label = stringResource(R.string.field_full_name),
            error = form.inputErrors.messageFor(ProfileInputError.FULL_NAME_INVALID),
            enabled = !form.isSubmitting,
            capitalization = KeyboardCapitalization.Words,
        )
        AppTextField(
            value = form.username,
            onValueChange = viewModel::onUsernameChange,
            label = stringResource(R.string.field_username),
            error = if (form.error == AppError.USERNAME_TAKEN) {
                stringResource(R.string.error_username_taken)
            } else {
                form.inputErrors.messageFor(ProfileInputError.USERNAME_INVALID)
            },
            supportingText = stringResource(R.string.field_username_hint),
            enabled = !form.isSubmitting,
        )
        UniversityField(
            state = universities,
            selectedId = form.universityId,
            error = form.inputErrors.messageFor(ProfileInputError.UNIVERSITY_REQUIRED),
            enabled = !form.isSubmitting,
            onOpen = { pickerOpen = true },
            onRetry = viewModel::loadUniversities,
        )
        AppTextField(
            value = form.department,
            onValueChange = viewModel::onDepartmentChange,
            label = stringResource(R.string.field_department),
            error = form.inputErrors.messageFor(ProfileInputError.DEPARTMENT_INVALID),
            enabled = !form.isSubmitting,
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submit,
        )
        PrimaryButton(
            text = stringResource(R.string.action_save_profile),
            onClick = viewModel::submit,
            loading = form.isSubmitting,
        )
        if (existingProfile?.fullName != null) {
            SecondaryButton(text = stringResource(R.string.action_cancel), onClick = onDone, enabled = !form.isSubmitting)
        }
        LinkButton(stringResource(R.string.action_sign_out), onSignOut, enabled = !form.isSubmitting)
    }

    val loaded = universities as? UniversitiesState.Loaded
    if (pickerOpen && loaded != null) {
        UniversityPicker(
            universities = loaded.universities,
            onSelect = {
                viewModel.onUniversitySelected(it.id)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun Set<ProfileInputError>.messageFor(error: ProfileInputError): String? =
    if (error in this) stringResource(error.messageRes()) else null

@Composable
private fun UniversityField(
    state: UniversitiesState,
    selectedId: String?,
    error: String?,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        UniversitiesState.Loading -> Text(
            stringResource(R.string.universities_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is UniversitiesState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(state.error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            SecondaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        }
        is UniversitiesState.Loaded -> if (state.universities.isEmpty()) {
            Text(
                stringResource(R.string.universities_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val selected = state.universities.firstOrNull { it.id == selectedId }
            // Read-only field that opens the searchable picker.
            OutlinedTextField(
                value = selected?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.field_university)) },
                trailingIcon = { Icon(Icons.Outlined.ExpandMore, contentDescription = null) },
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                shape = MaterialTheme.shapes.small,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledSupportingTextColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onOpen),
            )
        }
    }
}

@Composable
private fun UniversityPicker(
    universities: List<University>,
    onSelect: (University) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, universities) {
        val needle = query.trim().lowercase(TURKISH)
        if (needle.isEmpty()) {
            universities
        } else {
            universities.filter {
                needle in it.name.lowercase(TURKISH) || needle in it.city.lowercase(TURKISH)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.field_university)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.universities_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.universities_no_match), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.id }) { university ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(university) }
                                    .padding(vertical = Spacing.sm),
                            ) {
                                Text(university.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    university.city,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

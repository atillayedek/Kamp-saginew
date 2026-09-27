package com.kampusagi.android.presentation.requirement

import com.kampusagi.android.core.designsystem.icon.AppIcons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LinkButton
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.usecase.DraftInputError
import com.kampusagi.android.domain.usecase.RequirementValidator
import com.kampusagi.android.presentation.common.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRequirementScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateRequirementViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.publishedId) { if (viewModel.publishedId != null) onPublished() }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.create_requirement_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val form = viewModel.form
            if (form == null) {
                Text(stringResource(R.string.create_requirement_intro), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = viewModel.text,
                    onValueChange = viewModel::onTextChange,
                    placeholder = { Text(stringResource(R.string.create_requirement_placeholder)) },
                    supportingText = { Text("${viewModel.text.trim().length} / ${RequirementValidator.TEXT_LENGTH.last}") },
                    enabled = !viewModel.isWorking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
                ErrorText(viewModel)
                Text(
                    stringResource(R.string.ai_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(
                    text = stringResource(R.string.action_analyze),
                    onClick = viewModel::analyze,
                    enabled = viewModel.canAnalyze,
                    loading = viewModel.isWorking,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.md)) {
                        Icon(AppIcons.Stars, contentDescription = null)
                        Text(
                            stringResource(R.string.ai_draft_note),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
                val errors = viewModel.inputErrors
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { v -> viewModel.updateForm { it.copy(title = v) } },
                    label = { Text(stringResource(R.string.field_requirement_title)) },
                    isError = DraftInputError.TITLE_INVALID in errors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                    label = { Text(stringResource(R.string.field_requirement_description)) },
                    isError = DraftInputError.DESCRIPTION_INVALID in errors,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.field_requirement_category), style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    RequirementCategory.entries.forEach { category ->
                        FilterChip(
                            selected = form.category == category,
                            onClick = { viewModel.updateForm { it.copy(category = category) } },
                            label = { Text(stringResource(category.labelRes())) },
                        )
                    }
                }
                OutlinedTextField(
                    value = form.tagsText,
                    onValueChange = { v -> viewModel.updateForm { it.copy(tagsText = v) } },
                    label = { Text(stringResource(R.string.field_requirement_tags)) },
                    supportingText = { Text(stringResource(R.string.field_requirement_tags_hint)) },
                    isError = DraftInputError.TAGS_INVALID in errors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.locationText,
                    onValueChange = { v -> viewModel.updateForm { it.copy(locationText = v) } },
                    label = { Text(stringResource(R.string.field_requirement_location)) },
                    isError = DraftInputError.LOCATION_INVALID in errors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.participantsText,
                    onValueChange = { v -> viewModel.updateForm { it.copy(participantsText = v.filter(Char::isDigit).take(2)) } },
                    label = { Text(stringResource(R.string.field_requirement_participants)) },
                    isError = DraftInputError.PARTICIPANTS_INVALID in errors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                formatStartsAt(form.startsAt)?.let { formatted ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.requirement_starts_at, formatted),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.updateForm { it.copy(startsAt = null) } }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.cd_remove_time))
                        }
                    }
                }
                if (errors.isNotEmpty()) {
                    Text(
                        stringResource(R.string.error_invalid_input),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ErrorText(viewModel)
                PrimaryButton(
                    text = stringResource(R.string.action_publish_requirement),
                    onClick = viewModel::publish,
                    loading = viewModel.isWorking,
                )
                LinkButton(stringResource(R.string.action_edit_text), viewModel::editText, enabled = !viewModel.isWorking)
            }
        }
    }
}

@Composable
private fun ErrorText(viewModel: CreateRequirementViewModel) {
    viewModel.error?.let { error ->
        Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

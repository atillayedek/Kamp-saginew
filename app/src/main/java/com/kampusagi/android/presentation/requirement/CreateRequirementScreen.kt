package com.kampusagi.android.presentation.requirement

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.usecase.DraftInputError
import com.kampusagi.android.domain.usecase.RequirementValidator
import com.kampusagi.android.presentation.common.messageRes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateRequirementScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateRequirementViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.publishedId) { if (viewModel.publishedId != null) onPublished() }
    val errors = viewModel.inputErrors
    val enabled = !viewModel.isWorking

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
            Text(stringResource(R.string.create_requirement_intro), style = MaterialTheme.typography.bodyLarge)

            Text(stringResource(R.string.field_requirement_category), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RequirementCategory.entries.forEach { category ->
                    FilterChip(
                        selected = viewModel.category == category,
                        onClick = { viewModel.onCategoryChange(category) },
                        label = { Text(stringResource(category.labelRes())) },
                        enabled = enabled,
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.field_requirement_title)) },
                isError = DraftInputError.TITLE_INVALID in errors,
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.category != null) {
                Text(stringResource(R.string.field_requirement_tags), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    viewModel.suggestions.forEach { tag ->
                        FilterChip(
                            selected = tag in viewModel.tags,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag) },
                            enabled = enabled,
                        )
                    }
                    viewModel.customTags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    AppIcons.Close,
                                    contentDescription = stringResource(R.string.cd_remove_tag, tag),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            enabled = enabled,
                        )
                    }
                }
                OutlinedTextField(
                    value = viewModel.customTag,
                    onValueChange = viewModel::onCustomTagChange,
                    label = { Text(stringResource(R.string.field_requirement_custom_tag)) },
                    isError = DraftInputError.TAGS_INVALID in errors,
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.addCustomTag() }),
                    trailingIcon = {
                        TextButton(onClick = viewModel::addCustomTag, enabled = enabled && viewModel.customTag.isNotBlank()) {
                            Text(stringResource(R.string.action_add_tag))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.field_requirement_description)) },
                isError = DraftInputError.DESCRIPTION_INVALID in errors,
                supportingText = { Text("${viewModel.description.trim().length} / ${RequirementValidator.MAX_DESCRIPTION_LENGTH}") },
                minLines = 3,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            TimeFields(viewModel, enabled)

            OutlinedTextField(
                value = viewModel.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text(stringResource(R.string.field_requirement_location)) },
                leadingIcon = { Icon(AppIcons.LocationOn, contentDescription = null) },
                isError = DraftInputError.LOCATION_INVALID in errors,
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.participantsText,
                onValueChange = viewModel::onParticipantsChange,
                label = { Text(stringResource(R.string.field_requirement_participants)) },
                isError = DraftInputError.PARTICIPANTS_INVALID in errors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            if (DraftInputError.TIME_INVALID in errors) {
                Text(
                    stringResource(R.string.error_requirement_time_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (errors.isNotEmpty()) {
                Text(
                    stringResource(R.string.error_invalid_input),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            viewModel.error?.let { error ->
                Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryButton(
                text = stringResource(R.string.action_publish_requirement),
                onClick = { viewModel.publish() },
                enabled = viewModel.canPublish,
                loading = viewModel.isWorking,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeFields(viewModel: CreateRequirementViewModel, enabled: Boolean) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { pickDate = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(AppIcons.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                viewModel.date?.let(DATE::format) ?: stringResource(R.string.requirement_pick_date),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        OutlinedButton(onClick = { pickTime = true }, enabled = enabled && viewModel.date != null, modifier = Modifier.weight(1f)) {
            Icon(AppIcons.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                viewModel.time?.let(TIME::format) ?: stringResource(R.string.requirement_pick_time),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        if (viewModel.date != null) {
            IconButton(onClick = { viewModel.onDateChange(null) }, enabled = enabled) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.cd_remove_time))
            }
        }
    }

    if (pickDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.date?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                // The picker works in UTC midnight milliseconds.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !day.isBefore(today) && !day.isAfter(today.plusYears(1))
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDateChange(
                        state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() },
                    )
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        val initial = viewModel.time ?: LocalTime.of(18, 0)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onTimeChange(LocalTime.of(state.hour, state.minute))
                    pickTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

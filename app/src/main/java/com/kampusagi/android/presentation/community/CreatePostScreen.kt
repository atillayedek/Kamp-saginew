package com.kampusagi.android.presentation.community

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.usecase.NewPostValidator
import com.kampusagi.android.domain.usecase.PostTextValidator
import com.kampusagi.android.presentation.common.messageRes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePostScreen(
    onBack: () -> Unit,
    onCreated: (PostScope) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.createdIn) { viewModel.createdIn?.let(onCreated) }
    val enabled = !viewModel.isSubmitting
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(NewPostValidator.MAX_PHOTOS),
    ) { uris -> viewModel.onPhotosPicked(uris.map { it.toString() }) }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.create_post_title)) },
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
            OutlinedTextField(
                value = viewModel.body,
                onValueChange = viewModel::onBodyChange,
                placeholder = { Text(stringResource(R.string.create_post_placeholder)) },
                supportingText = { Text("${viewModel.body.length} / ${PostTextValidator.MAX_POST_LENGTH}") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )

            PhotoPicker(
                photos = viewModel.photos,
                preparing = viewModel.isPreparingPhotos,
                canAdd = enabled && viewModel.remainingPhotos > 0,
                onAdd = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = viewModel::removePhoto,
            )

            Text(stringResource(R.string.create_post_category), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                PostCategory.entries.forEach { option ->
                    CategoryChip(
                        category = option,
                        selected = viewModel.category == option,
                        onClick = { viewModel.onCategoryChange(option) },
                        enabled = enabled,
                    )
                }
            }

            if (viewModel.category == PostCategory.EVENT) EventFields(viewModel, enabled)
            if (viewModel.category == PostCategory.MARKETPLACE) {
                OutlinedTextField(
                    value = viewModel.priceText,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text(stringResource(R.string.create_post_price)) },
                    suffix = { Text("₺") },
                    isError = !viewModel.priceValid,
                    supportingText = {
                        Text(stringResource(if (viewModel.priceValid) R.string.create_post_price_hint else R.string.create_post_price_invalid))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PollFields(viewModel, enabled)

            Text(stringResource(R.string.create_post_audience), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PostScope.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = viewModel.scope == option,
                        onClick = { viewModel.onScopeChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PostScope.entries.size),
                        enabled = enabled,
                    ) { Text(stringResource(option.labelRes())) }
                }
            }
            Text(
                stringResource(
                    if (viewModel.scope == PostScope.GENERAL) R.string.create_post_general_hint else R.string.create_post_university_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            viewModel.error?.let { error ->
                Text(
                    stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PrimaryButton(
                text = stringResource(R.string.action_share),
                onClick = viewModel::submit,
                enabled = viewModel.canSubmit,
                loading = viewModel.isSubmitting,
            )
        }
    }
}

@Composable
private fun PhotoPicker(
    photos: List<PickedPhoto>,
    preparing: Boolean,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                itemsIndexed(photos) { index, photo ->
                    Box(modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium)) {
                        Image(photo.preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp))
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
                        ) {
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(AppIcons.Close, contentDescription = stringResource(R.string.create_post_remove_photo), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onAdd, enabled = canAdd && !preparing) {
            Icon(AppIcons.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (preparing) stringResource(R.string.create_post_preparing_photos)
                else stringResource(R.string.create_post_add_photos, photos.size, NewPostValidator.MAX_PHOTOS),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PollFields(viewModel: CreatePostViewModel, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.BarChart, contentDescription = null)
        Text(
            stringResource(R.string.create_post_add_poll),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(start = Spacing.sm),
        )
        Switch(checked = viewModel.pollEnabled, onCheckedChange = viewModel::setPollEnabled, enabled = enabled)
    }
    if (!viewModel.pollEnabled) return
    viewModel.pollOptions.forEachIndexed { index, option ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = option,
                onValueChange = { viewModel.onPollOptionChange(index, it) },
                label = { Text(stringResource(R.string.create_post_poll_option, index + 1)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            if (viewModel.pollOptions.size > NewPostValidator.MIN_POLL_OPTIONS) {
                IconButton(onClick = { viewModel.removePollOption(index) }, enabled = enabled) {
                    Icon(AppIcons.Remove, contentDescription = stringResource(R.string.create_post_poll_remove_option))
                }
            }
        }
    }
    if (viewModel.pollOptions.size < NewPostValidator.MAX_POLL_OPTIONS) {
        TextButton(onClick = viewModel::addPollOption, enabled = enabled) {
            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.create_post_poll_add_option), modifier = Modifier.padding(start = Spacing.xs))
        }
    }
    if (!viewModel.pollValid) {
        Text(stringResource(R.string.create_post_poll_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Text(stringResource(R.string.create_post_poll_duration), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PollDuration.entries.forEach { duration ->
            FilterChip(
                selected = viewModel.pollDuration == duration,
                onClick = { viewModel.onPollDurationChange(duration) },
                enabled = enabled,
                label = { Text(stringResource(duration.labelRes())) },
            )
        }
    }
}

private fun PollDuration.labelRes(): Int = when (this) {
    PollDuration.OPEN -> R.string.poll_duration_open
    PollDuration.ONE_DAY -> R.string.poll_duration_day
    PollDuration.THREE_DAYS -> R.string.poll_duration_three_days
    PollDuration.ONE_WEEK -> R.string.poll_duration_week
}

private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("tr-TR"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventFields(viewModel: CreatePostViewModel, enabled: Boolean) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.create_post_event_details), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = { pickDate = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(AppIcons.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                viewModel.eventDate?.let(DATE::format) ?: stringResource(R.string.create_post_event_date),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        OutlinedButton(onClick = { pickTime = true }, enabled = enabled && viewModel.eventDate != null, modifier = Modifier.weight(1f)) {
            Icon(AppIcons.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                viewModel.eventTime?.let(TIME::format) ?: stringResource(R.string.create_post_event_time),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
    if (!viewModel.eventValid) {
        Text(stringResource(R.string.create_post_event_time_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    OutlinedTextField(
        value = viewModel.eventLocation,
        onValueChange = viewModel::onEventLocationChange,
        label = { Text(stringResource(R.string.create_post_event_location)) },
        leadingIcon = { Icon(AppIcons.LocationOn, contentDescription = null) },
        singleLine = true,
        enabled = enabled && viewModel.eventDate != null,
        modifier = Modifier.fillMaxWidth(),
    )

    if (pickDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.eventDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
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
                    viewModel.onEventDateChange(
                        state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() },
                    )
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        val initial = viewModel.eventTime ?: LocalTime.of(18, 0)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEventTimeChange(LocalTime.of(state.hour, state.minute))
                    pickTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

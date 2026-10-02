package com.kampusagi.android.presentation.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.model.NewGroup
import com.kampusagi.android.domain.repository.GroupRepository
import com.kampusagi.android.domain.usecase.GroupRules
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.main.CreateGroupRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Whether the person may create a channel: known only after asking the server. */
sealed interface PremiumCheck {
    data object Checking : PremiumCheck
    data class Known(val premium: Boolean) : PremiumCheck
    data class Failed(val error: AppError) : PremiumCheck
}

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupRepository,
) : ViewModel() {

    val kind: GroupKind = savedStateHandle.toRoute<CreateGroupRoute>().kind

    var premium by mutableStateOf<PremiumCheck>(
        if (kind == GroupKind.CHANNEL) PremiumCheck.Checking else PremiumCheck.Known(premium = false),
    )
        private set

    var name by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var courseCode by mutableStateOf("")
        private set
    var universityOnly by mutableStateOf(true)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var created by mutableStateOf<String?>(null)
        private set

    val canSubmit: Boolean
        get() = !isSubmitting && GroupRules.isValidName(name) && description.length <= GroupRules.MAX_DESCRIPTION &&
            (courseCode.isBlank() || GroupRules.normalizeCourseCode(courseCode) != null)

    init {
        if (kind == GroupKind.CHANNEL) checkPremium()
    }

    fun checkPremium() {
        premium = PremiumCheck.Checking
        viewModelScope.launch {
            premium = when (val result = repository.isPremium()) {
                is AppResult.Success -> PremiumCheck.Known(result.value)
                is AppResult.Failure -> PremiumCheck.Failed(result.error)
            }
        }
    }

    fun onNameChange(value: String) {
        if (value.length <= GroupRules.MAX_NAME) name = value
        error = null
    }

    fun onDescriptionChange(value: String) {
        if (value.length <= GroupRules.MAX_DESCRIPTION) description = value
    }

    fun onCourseCodeChange(value: String) {
        if (value.length <= 20) courseCode = value
    }

    fun onUniversityOnlyChange(value: Boolean) {
        universityOnly = value
    }

    fun submit() {
        if (!canSubmit) return
        isSubmitting = true
        error = null
        viewModelScope.launch {
            val group = NewGroup(kind, name, description.ifBlank { null }, courseCode.ifBlank { null }, universityOnly)
            when (val result = repository.create(group)) {
                is AppResult.Success -> created = result.value
                is AppResult.Failure -> error = result.error
            }
            isSubmitting = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onOpenPremium: () -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.created) { viewModel.created?.let(onCreated) }
    val channel = viewModel.kind == GroupKind.CHANNEL
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(if (channel) R.string.channel_create else R.string.group_create)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            },
        )
        when (val premium = viewModel.premium) {
            PremiumCheck.Checking -> LoadingView()
            is PremiumCheck.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.premium_load_failed),
                body = stringResource(premium.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::checkPremium)
            }
            is PremiumCheck.Known -> if (channel && !premium.premium) {
                MessageView(
                    icon = AppIcons.WorkspacePremium,
                    title = stringResource(R.string.channel_premium_title),
                    body = stringResource(R.string.channel_premium_body),
                ) {
                    PrimaryButton(text = stringResource(R.string.channel_premium_action), onClick = onOpenPremium)
                }
            } else {
                Form(viewModel, channel)
            }
        }
    }
}

@Composable
private fun Form(viewModel: CreateGroupViewModel, channel: Boolean) {
    val enabled = !viewModel.isSubmitting
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(if (channel) R.string.channel_create_intro else R.string.group_create_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = viewModel.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.group_name)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = viewModel.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text(stringResource(R.string.group_description)) },
            supportingText = { Text("${viewModel.description.length} / ${GroupRules.MAX_DESCRIPTION}") },
            minLines = 3,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = viewModel.courseCode,
            onValueChange = viewModel::onCourseCodeChange,
            label = { Text(stringResource(R.string.group_course_code)) },
            supportingText = { Text(stringResource(R.string.group_course_code_hint)) },
            isError = viewModel.courseCode.isNotBlank() && GroupRules.normalizeCourseCode(viewModel.courseCode) == null,
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        if (channel) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.channel_university_only), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (viewModel.universityOnly) R.string.channel_scope_university_hint else R.string.channel_scope_all_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = viewModel.universityOnly, onCheckedChange = viewModel::onUniversityOnlyChange, enabled = enabled)
            }
        }
        viewModel.error?.let {
            Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(
            text = stringResource(R.string.action_create),
            onClick = viewModel::submit,
            enabled = viewModel.canSubmit,
            loading = viewModel.isSubmitting,
        )
    }
}

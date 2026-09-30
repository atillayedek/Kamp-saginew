package com.kampusagi.android.presentation.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.kampusagi.android.domain.model.GroupDetail
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.model.GroupMember
import com.kampusagi.android.domain.model.GroupRole
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.repository.GroupRepository
import com.kampusagi.android.domain.repository.ImageEncoder
import com.kampusagi.android.domain.repository.ModerationRepository
import com.kampusagi.android.domain.usecase.GroupRules
import com.kampusagi.android.presentation.common.avatar.UserAvatar
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.main.GroupInfoRoute
import com.kampusagi.android.presentation.moderation.MenuAction
import com.kampusagi.android.presentation.moderation.OverflowMenu
import com.kampusagi.android.presentation.moderation.ReportDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

sealed interface GroupInfoState {
    data object Loading : GroupInfoState
    data class Loaded(val group: GroupDetail, val members: List<GroupMember>) : GroupInfoState
    data class Failed(val error: AppError) : GroupInfoState
}

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupRepository,
    private val moderation: ModerationRepository,
    private val imageEncoder: ImageEncoder,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupInfoRoute>().groupId

    var state by mutableStateOf<GroupInfoState>(GroupInfoState.Loading)
        private set

    var isWorking by mutableStateOf(false)
        private set

    var actionError by mutableStateOf<AppError?>(null)
        private set

    var reportSent by mutableStateOf(false)
        private set

    /** Set when the person left or deleted the group, so the screens close. */
    var closed by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { state = fetch() ?: state }
    }

    fun changePhoto(uri: String) = act {
        when (val jpeg = imageEncoder.avatarJpeg(uri)) {
            is AppResult.Success -> repository.setPhoto(groupId, jpeg.value).afterSuccess()
            is AppResult.Failure -> jpeg.error
        }
    }

    fun removePhoto() = act { repository.setPhoto(groupId, null).afterSuccess() }

    fun update(name: String, description: String, courseCode: String) = act {
        repository.update(groupId, name, description.ifBlank { null }, courseCode.ifBlank { null }).afterSuccess()
    }

    fun setRole(member: GroupMember, role: GroupRole) = act { repository.setRole(groupId, member.userId, role).afterSuccess() }

    fun remove(member: GroupMember) = act { repository.removeMember(groupId, member.userId).afterSuccess() }

    fun leave() = act {
        when (val result = repository.leave(groupId)) {
            is AppResult.Success -> {
                closed = true
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun delete() = act {
        when (val result = repository.delete(groupId)) {
            is AppResult.Success -> {
                closed = true
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    fun report(reason: ReportReason, details: String?) = act {
        reportSent = false
        when (val result = moderation.report(ReportTarget.GROUP, groupId, reason, details)) {
            is AppResult.Success -> {
                reportSent = true
                null
            }
            is AppResult.Failure -> result.error
        }
    }

    private suspend fun AppResult<Unit>.afterSuccess(): AppError? = when (this) {
        is AppResult.Success -> {
            fetch()?.let { state = it }
            null
        }
        is AppResult.Failure -> error
    }

    /** Null keeps what is on screen when a reload after an action fails. */
    private suspend fun fetch(): GroupInfoState? = coroutineScope {
        val group = async { repository.group(groupId) }
        val members = async { repository.members(groupId) }
        when (val g = group.await()) {
            is AppResult.Failure -> if (state is GroupInfoState.Loaded) {
                actionError = g.error
                null
            } else {
                GroupInfoState.Failed(g.error)
            }
            is AppResult.Success -> when (val m = members.await()) {
                is AppResult.Success -> GroupInfoState.Loaded(g.value, m.value)
                // Someone who has not joined cannot list members; they still see the group.
                is AppResult.Failure -> GroupInfoState.Loaded(g.value, emptyList())
            }
        }
    }

    private fun act(action: suspend () -> AppError?) {
        if (isWorking) return
        isWorking = true
        actionError = null
        viewModelScope.launch {
            actionError = action()
            isWorking = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onOpenPerson: (String) -> Unit,
    viewModel: GroupInfoViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel.closed) { if (viewModel.closed) onClosed() }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var reporting by rememberSaveable { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.changePhoto(it.toString()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.group_info_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            },
        )
        when (val state = viewModel.state) {
            GroupInfoState.Loading -> LoadingView()
            is GroupInfoState.Failed -> MessageView(
                icon = AppIcons.CloudOff,
                title = stringResource(R.string.groups_load_failed),
                body = stringResource(state.error.messageRes()),
            ) {
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
            }
            is GroupInfoState.Loaded -> {
                val group = state.group
                val channel = group.kind == GroupKind.CHANNEL
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        ) {
                            GroupAvatar(group.kind, group.photoPath, size = 96.dp)
                            if (group.canManage) {
                                Row {
                                    TextButton(
                                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        enabled = !viewModel.isWorking,
                                    ) { Text(stringResource(R.string.profile_change_photo)) }
                                    if (group.photoPath != null) {
                                        TextButton(onClick = viewModel::removePhoto, enabled = !viewModel.isWorking) {
                                            Text(stringResource(R.string.profile_remove_photo))
                                        }
                                    }
                                }
                                if (channel) {
                                    Text(
                                        stringResource(R.string.channel_photo_premium),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(group.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            val details = buildList {
                                add(stringResource(if (channel) R.string.inbox_channels else R.string.inbox_groups))
                                group.courseCode?.let { add(it) }
                                add(pluralStringResource(R.plurals.group_members, group.memberCount, group.memberCount))
                                if (channel) add(stringResource(if (group.isGlobal) R.string.channel_scope_all else R.string.channel_scope_university))
                            }
                            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            group.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                            group.ownerName?.let {
                                Text(
                                    stringResource(R.string.group_owner, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (group.canManage) {
                                OutlinedButton(onClick = { editing = true }, enabled = !viewModel.isWorking) {
                                    Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.group_edit), modifier = Modifier.padding(start = Spacing.xs))
                                }
                            }
                            if (viewModel.reportSent) {
                                Text(stringResource(R.string.report_sent), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                            }
                            viewModel.actionError?.let {
                                Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (state.members.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.group_members_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.xs),
                            )
                        }
                    }
                    items(state.members, key = { it.userId }) { member ->
                        MemberRow(member, group, viewModel, onOpenPerson)
                    }
                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = Spacing.sm))
                        when {
                            group.myRole == GroupRole.OWNER -> DangerRow(AppIcons.Delete, stringResource(if (channel) R.string.channel_delete else R.string.group_delete)) {
                                confirmDelete = true
                            }
                            group.myRole != null -> DangerRow(AppIcons.ExitToApp, stringResource(if (channel) R.string.channel_unfollow else R.string.group_leave)) {
                                confirmLeave = true
                            }
                        }
                        if (group.myRole != GroupRole.OWNER) {
                            DangerRow(AppIcons.Error, stringResource(R.string.report_group)) { reporting = true }
                        }
                    }
                }
                if (editing) EditDialog(group, onDismiss = { editing = false }) { name, description, code ->
                    editing = false
                    viewModel.update(name, description, code)
                }
            }
        }
    }

    if (confirmLeave) ConfirmDialog(R.string.group_leave_confirm, onConfirm = { confirmLeave = false; viewModel.leave() }, onDismiss = { confirmLeave = false })
    if (confirmDelete) ConfirmDialog(R.string.group_delete_confirm, onConfirm = { confirmDelete = false; viewModel.delete() }, onDismiss = { confirmDelete = false })
    if (reporting) {
        ReportDialog(
            target = ReportTarget.GROUP,
            submitting = viewModel.isWorking,
            onSubmit = { reason, details ->
                reporting = false
                viewModel.report(reason, details)
            },
            onDismiss = { reporting = false },
        )
    }
}

@Composable
private fun MemberRow(member: GroupMember, group: GroupDetail, viewModel: GroupInfoViewModel, onOpenPerson: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onOpenPerson(member.userId) }.padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        UserAvatar(member.userId, member.fullName, member.username, size = 40.dp)
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(member.fullName ?: member.username.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            member.username?.let { Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when (member.role) {
            GroupRole.OWNER -> Text(stringResource(R.string.group_role_owner), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            GroupRole.ADMIN -> Text(stringResource(R.string.group_role_admin), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            GroupRole.MEMBER -> Unit
        }
        val actions = buildList {
            if (group.myRole == GroupRole.OWNER && member.role != GroupRole.OWNER) {
                add(
                    if (member.role == GroupRole.ADMIN) MenuAction(R.string.group_make_member) { viewModel.setRole(member, GroupRole.MEMBER) }
                    else MenuAction(R.string.group_make_admin) { viewModel.setRole(member, GroupRole.ADMIN) },
                )
                add(MenuAction(R.string.group_remove_member) { viewModel.remove(member) })
            } else if (group.myRole == GroupRole.ADMIN && member.role == GroupRole.MEMBER) {
                add(MenuAction(R.string.group_remove_member) { viewModel.remove(member) })
            }
        }
        if (actions.isNotEmpty()) OverflowMenu(actions = actions, enabled = !viewModel.isWorking)
    }
}

@Composable
private fun DangerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Spacing.md))
    }
}

@Composable
private fun ConfirmDialog(message: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EditDialog(group: GroupDetail, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(group.name) }
    var description by remember { mutableStateOf(group.description.orEmpty()) }
    var code by remember { mutableStateOf(group.courseCode.orEmpty()) }
    val valid = GroupRules.isValidName(name) && (code.isBlank() || GroupRules.normalizeCourseCode(code) != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= GroupRules.MAX_NAME) name = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= GroupRules.MAX_DESCRIPTION) description = it },
                    label = { Text(stringResource(R.string.group_description)) },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 20) code = it },
                    label = { Text(stringResource(R.string.group_course_code)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, description, code) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

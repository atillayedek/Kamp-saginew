package com.kampusagi.android.presentation.notes

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.component.LoadingView
import com.kampusagi.android.core.designsystem.component.MessageView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.icon.AppIcons
import com.kampusagi.android.core.designsystem.theme.Spacing
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.CourseNote
import com.kampusagi.android.domain.model.NewCourseNote
import com.kampusagi.android.domain.model.NoteCourse
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.repository.DocumentReader
import com.kampusagi.android.domain.repository.ModerationRepository
import com.kampusagi.android.domain.repository.NoteRepository
import com.kampusagi.android.domain.usecase.GroupRules
import com.kampusagi.android.presentation.common.messageRes
import com.kampusagi.android.presentation.common.relativeTime
import com.kampusagi.android.presentation.moderation.MenuAction
import com.kampusagi.android.presentation.moderation.OverflowMenu
import com.kampusagi.android.presentation.moderation.ReportDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface NotesState {
    data object Loading : NotesState
    data class Loaded(val notes: List<CourseNote>) : NotesState
    data class Failed(val error: AppError) : NotesState
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val moderation: ModerationRepository,
) : ViewModel() {

    var state by mutableStateOf<NotesState>(NotesState.Loading)
        private set
    var courses by mutableStateOf<List<NoteCourse>>(emptyList())
        private set
    var selectedCourse by mutableStateOf<String?>(null)
        private set
    var query by mutableStateOf("")
        private set
    var openingId by mutableStateOf<String?>(null)
        private set
    var actionError by mutableStateOf<AppError?>(null)
        private set
    var reportSent by mutableStateOf(false)
        private set

    private val opened = Channel<File>(Channel.BUFFERED)

    /** PDFs downloaded for reading; the screen hands each one to a PDF viewer. */
    val filesToOpen = opened.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            when (val result = repository.courses()) {
                is AppResult.Success -> courses = result.value
                is AppResult.Failure -> Log.w(TAG, "Course list not loaded: ${result.error}")
            }
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { fetch() }
    }

    fun selectCourse(code: String?) {
        selectedCourse = if (selectedCourse == code) null else code
        searchJob?.cancel()
        searchJob = viewModelScope.launch { fetch() }
    }

    fun onQueryChange(value: String) {
        if (value.length > 64) return
        query = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            fetch()
        }
    }

    fun open(note: CourseNote) {
        if (openingId != null) return
        openingId = note.id
        actionError = null
        viewModelScope.launch {
            when (val result = repository.open(note.id)) {
                is AppResult.Success -> {
                    opened.send(result.value)
                    if (!note.isMine) fetch()
                }
                is AppResult.Failure -> actionError = result.error
            }
            openingId = null
        }
    }

    fun delete(note: CourseNote) {
        viewModelScope.launch {
            when (val result = repository.delete(note.id)) {
                is AppResult.Success -> load()
                is AppResult.Failure -> actionError = result.error
            }
        }
    }

    fun report(note: CourseNote, reason: ReportReason, details: String?) {
        reportSent = false
        viewModelScope.launch {
            when (val result = moderation.report(ReportTarget.NOTE, note.id, reason, details)) {
                is AppResult.Success -> reportSent = true
                is AppResult.Failure -> actionError = result.error
            }
        }
    }

    fun onViewerMissing() {
        actionError = AppError.DOCUMENT_UNREADABLE
    }

    private suspend fun fetch() {
        state = when (val result = repository.notes(selectedCourse, query.ifBlank { null })) {
            is AppResult.Success -> NotesState.Loaded(result.value)
            is AppResult.Failure -> (state as? NotesState.Loaded)?.also { actionError = result.error } ?: NotesState.Failed(result.error)
        }
    }

    private companion object {
        const val TAG = "Notes"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onOpenPerson: (String) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var reporting by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.filesToOpen.collect { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w("Notes", "No PDF viewer installed", e)
                viewModel.onViewerMissing()
            }
        }
    }
    val notes = (viewModel.state as? NotesState.Loaded)?.notes.orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
            )
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            if (viewModel.courses.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                ) {
                    items(viewModel.courses, key = { it.code }) { course ->
                        FilterChip(
                            selected = viewModel.selectedCourse == course.code,
                            onClick = { viewModel.selectCourse(course.code) },
                            label = { Text("${course.code} (${course.noteCount})") },
                        )
                    }
                }
            }
            if (viewModel.reportSent) {
                Text(stringResource(R.string.report_sent), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Spacing.md))
            }
            viewModel.actionError?.let {
                Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Spacing.md))
            }
            when (val state = viewModel.state) {
                NotesState.Loading -> LoadingView()
                is NotesState.Failed -> MessageView(
                    icon = AppIcons.CloudOff,
                    title = stringResource(R.string.notes_load_failed),
                    body = stringResource(state.error.messageRes()),
                ) {
                    PrimaryButton(text = stringResource(R.string.action_retry), onClick = viewModel::load)
                }
                is NotesState.Loaded -> if (state.notes.isEmpty()) {
                    MessageView(
                        icon = AppIcons.MenuBook,
                        title = stringResource(R.string.notes_empty_title),
                        body = stringResource(R.string.notes_empty_body),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(state.notes, key = { it.id }) { note ->
                            NoteRow(
                                note = note,
                                opening = viewModel.openingId == note.id,
                                onOpen = { viewModel.open(note) },
                                onOpenAuthor = { onOpenPerson(note.author.id) },
                                actions = if (note.isMine) {
                                    listOf(MenuAction(R.string.notes_delete) { deleting = note.id })
                                } else {
                                    listOf(MenuAction(R.string.report_note) { reporting = note.id })
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onUpload,
            icon = { Icon(AppIcons.Upload, contentDescription = null) },
            text = { Text(stringResource(R.string.notes_upload)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
        )
    }

    reporting?.let { id ->
        notes.firstOrNull { it.id == id }?.let { note ->
            ReportDialog(
                target = ReportTarget.NOTE,
                submitting = false,
                onSubmit = { reason, details ->
                    reporting = null
                    viewModel.report(note, reason, details)
                },
                onDismiss = { reporting = null },
            )
        }
    }
    deleting?.let { id ->
        notes.firstOrNull { it.id == id }?.let { note ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                text = { Text(stringResource(R.string.notes_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleting = null
                        viewModel.delete(note)
                    }) { Text(stringResource(R.string.notes_delete)) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun NoteRow(note: CourseNote, opening: Boolean, onOpen: () -> Unit, onOpenAuthor: () -> Unit, actions: List<MenuAction>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !opening, onClick = onOpen).padding(Spacing.md),
    ) {
        Icon(AppIcons.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${note.courseCode} · ${note.courseName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            note.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(
                listOf(
                    note.author.fullName ?: note.author.username.orEmpty(),
                    relativeTime(note.createdAt),
                    formatSize(note.fileSize),
                    pluralStringResource(R.plurals.notes_opens, note.downloadCount, note.downloadCount),
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenAuthor),
            )
            if (opening) Text(stringResource(R.string.notes_opening), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        OverflowMenu(actions = actions)
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format(java.util.Locale.forLanguageTag("tr"), "%.1f MB", bytes / 1048576.0) else "${(bytes / 1024).coerceAtLeast(1)} KB"

@HiltViewModel
class UploadNoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val documentReader: DocumentReader,
) : ViewModel() {

    var courseCode by mutableStateOf("")
        private set
    var courseName by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var pdf by mutableStateOf<ByteArray?>(null)
        private set
    var isReading by mutableStateOf(false)
        private set
    var isUploading by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var uploaded by mutableStateOf(false)
        private set

    /** "Bu içeriğin hak sahibiyim veya paylaşma iznim var…" — required, never pre-ticked. */
    var rightsDeclared by mutableStateOf(false)
        private set

    fun onRightsDeclaredChange(value: Boolean) { rightsDeclared = value }

    val canSubmit: Boolean
        get() = !isUploading && rightsDeclared && pdf != null && GroupRules.normalizeCourseCode(courseCode) != null &&
            GroupRules.isValidNoteText(courseName, GroupRules.MAX_COURSE_NAME) &&
            GroupRules.isValidNoteText(title, GroupRules.MAX_NOTE_TITLE) && description.length <= GroupRules.MAX_DESCRIPTION

    fun onCourseCodeChange(value: String) { if (value.length <= 20) courseCode = value }
    fun onCourseNameChange(value: String) { if (value.length <= GroupRules.MAX_COURSE_NAME) courseName = value }
    fun onTitleChange(value: String) { if (value.length <= GroupRules.MAX_NOTE_TITLE) title = value }
    fun onDescriptionChange(value: String) { if (value.length <= GroupRules.MAX_DESCRIPTION) description = value }

    fun onPdfPicked(uri: String) {
        isReading = true
        error = null
        viewModelScope.launch {
            when (val result = documentReader.read(uri, GroupRules.MAX_NOTE_BYTES)) {
                is AppResult.Success -> pdf = result.value
                is AppResult.Failure -> error = result.error
            }
            isReading = false
        }
    }

    fun submit() {
        val bytes = pdf ?: return
        if (!canSubmit) return
        isUploading = true
        error = null
        viewModelScope.launch {
            when (val result = repository.upload(NewCourseNote(courseCode, courseName, title, description.ifBlank { null }, bytes, rightsDeclared))) {
                is AppResult.Success -> uploaded = true
                is AppResult.Failure -> error = result.error
            }
            isUploading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadNoteScreen(onBack: () -> Unit, onUploaded: () -> Unit, viewModel: UploadNoteViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel.uploaded) { if (viewModel.uploaded) onUploaded() }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onPdfPicked(it.toString()) }
    }
    val enabled = !viewModel.isUploading
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.notes_upload)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            },
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.notes_upload_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }, enabled = enabled && !viewModel.isReading) {
                Icon(AppIcons.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    when {
                        viewModel.isReading -> stringResource(R.string.notes_reading_pdf)
                        viewModel.pdf != null -> stringResource(R.string.notes_pdf_selected, formatSize(viewModel.pdf!!.size.toLong()))
                        else -> stringResource(R.string.notes_pick_pdf)
                    },
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
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
            OutlinedTextField(
                value = viewModel.courseName,
                onValueChange = viewModel::onCourseNameChange,
                label = { Text(stringResource(R.string.notes_course_name)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.notes_note_title)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.group_description)) },
                minLines = 2,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = viewModel.rightsDeclared, onCheckedChange = viewModel::onRightsDeclaredChange, enabled = enabled)
                Text(
                    stringResource(R.string.notes_rights_declaration),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            viewModel.error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                text = stringResource(R.string.notes_upload),
                onClick = viewModel::submit,
                enabled = viewModel.canSubmit,
                loading = viewModel.isUploading,
            )
        }
    }
}

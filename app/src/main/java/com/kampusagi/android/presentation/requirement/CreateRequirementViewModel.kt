package com.kampusagi.android.presentation.requirement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.usecase.AnalyzeRequirementUseCase
import com.kampusagi.android.domain.usecase.DraftInputError
import com.kampusagi.android.domain.usecase.FormResult
import com.kampusagi.android.domain.usecase.PublishRequirementUseCase
import com.kampusagi.android.domain.usecase.RequirementValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** The editable form of a draft; numbers and tags are kept as typed until publishing. */
data class DraftForm(
    val title: String,
    val description: String,
    val category: RequirementCategory,
    val tagsText: String,
    val locationText: String,
    val startsAt: String?,
    val participantsText: String,
)

@HiltViewModel
class CreateRequirementViewModel @Inject constructor(
    private val analyzeRequirement: AnalyzeRequirementUseCase,
    private val publishRequirement: PublishRequirementUseCase,
) : ViewModel() {

    var text by mutableStateOf("")
        private set

    /** Null until the AI analysis succeeded. */
    var form by mutableStateOf<DraftForm?>(null)
        private set

    var inputErrors by mutableStateOf<Set<DraftInputError>>(emptySet())
        private set

    var isWorking by mutableStateOf(false)
        private set

    var error by mutableStateOf<AppError?>(null)
        private set

    var publishedId by mutableStateOf<String?>(null)
        private set

    val canAnalyze: Boolean get() = !isWorking && RequirementValidator.isValidText(text)

    fun onTextChange(value: String) {
        if (value.length <= RequirementValidator.TEXT_LENGTH.last) text = value
        error = null
    }

    fun analyze() {
        if (!canAnalyze) return
        isWorking = true
        error = null
        viewModelScope.launch {
            when (val result = analyzeRequirement(text)) {
                is AppResult.Success -> {
                    form = result.value.toForm()
                    inputErrors = emptySet()
                }
                is AppResult.Failure -> error = result.error
            }
            isWorking = false
        }
    }

    /** Back to the text, keeping it, to analyse again. */
    fun editText() {
        form = null
        inputErrors = emptySet()
        error = null
    }

    fun updateForm(change: (DraftForm) -> DraftForm) {
        form = form?.let(change)
        inputErrors = emptySet()
        error = null
    }

    fun publish() {
        val current = form ?: return
        if (isWorking) return
        val participants = current.participantsText.trim().let { if (it.isEmpty()) null else it.toIntOrNull() }
        if (current.participantsText.isNotBlank() && participants == null) {
            inputErrors = setOf(DraftInputError.PARTICIPANTS_INVALID)
            return
        }
        val draft = RequirementDraft(
            title = current.title,
            description = current.description,
            category = current.category,
            tags = RequirementValidator.parseTags(current.tagsText),
            locationText = current.locationText.ifBlank { null },
            startsAt = current.startsAt,
            participantsNeeded = participants,
        )
        isWorking = true
        error = null
        viewModelScope.launch {
            when (val result = publishRequirement(text, draft)) {
                is FormResult.Invalid -> inputErrors = result.errors
                is FormResult.Failed -> error = result.error
                is FormResult.Success -> publishedId = result.value
            }
            isWorking = false
        }
    }

    private fun RequirementDraft.toForm() = DraftForm(
        title = title,
        description = description,
        category = category,
        tagsText = tags.joinToString(", "),
        locationText = locationText.orEmpty(),
        startsAt = startsAt,
        participantsText = participantsNeeded?.toString().orEmpty(),
    )
}

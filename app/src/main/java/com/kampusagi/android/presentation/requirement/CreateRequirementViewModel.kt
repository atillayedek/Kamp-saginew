package com.kampusagi.android.presentation.requirement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.usecase.CreateRequirementUseCase
import com.kampusagi.android.domain.usecase.DraftInputError
import com.kampusagi.android.domain.usecase.FormResult
import com.kampusagi.android.domain.usecase.RequirementTags
import com.kampusagi.android.domain.usecase.RequirementValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.launch

/** The requirement form: the student picks a category and tags; matching is done by rules in the database. */
@HiltViewModel
class CreateRequirementViewModel @Inject constructor(
    private val createRequirement: CreateRequirementUseCase,
) : ViewModel() {

    var category by mutableStateOf<RequirementCategory?>(null)
        private set

    var title by mutableStateOf("")
        private set

    var description by mutableStateOf("")
        private set

    var tags by mutableStateOf<List<String>>(emptyList())
        private set

    var customTag by mutableStateOf("")
        private set

    var date by mutableStateOf<LocalDate?>(null)
        private set

    /** Only with a date; noon is used when the date has no time. */
    var time by mutableStateOf<LocalTime?>(null)
        private set

    var location by mutableStateOf("")
        private set

    var participantsText by mutableStateOf("")
        private set

    var inputErrors by mutableStateOf<Set<DraftInputError>>(emptySet())
        private set

    var isWorking by mutableStateOf(false)
        private set

    var error by mutableStateOf<AppError?>(null)
        private set

    var publishedId by mutableStateOf<String?>(null)
        private set

    val suggestions: List<String> get() = category?.let(RequirementTags::suggestions).orEmpty()

    /** Tags the student typed that are not among the suggestions, shown as removable chips. */
    val customTags: List<String> get() = tags.filterNot { it in suggestions }

    val canPublish: Boolean
        get() = !isWorking && category != null && title.trim().length in RequirementValidator.TITLE_LENGTH

    fun onCategoryChange(value: RequirementCategory) = edit { category = value }

    fun onTitleChange(value: String) = edit { title = value.take(RequirementValidator.TITLE_LENGTH.last) }

    fun onDescriptionChange(value: String) = edit { description = value.take(RequirementValidator.MAX_DESCRIPTION_LENGTH) }

    fun toggleTag(tag: String) = edit {
        tags = when {
            tag in tags -> tags - tag
            tags.size < RequirementValidator.MAX_TAGS -> tags + tag
            else -> tags.also { inputErrors = setOf(DraftInputError.TAGS_INVALID) }
        }
    }

    fun onCustomTagChange(value: String) = edit { customTag = value.take(RequirementValidator.MAX_TAG_LENGTH + 1) }

    /** Adds what was typed (comma-separated allowed); too long or too many tags are reported, not cut. */
    fun addCustomTag() = edit {
        val added = RequirementValidator.parseTags(customTag)
        val merged = (tags + added).distinct()
        if (added.any { it.length > RequirementValidator.MAX_TAG_LENGTH } || merged.size > RequirementValidator.MAX_TAGS) {
            inputErrors = setOf(DraftInputError.TAGS_INVALID)
        } else {
            tags = merged
            customTag = ""
        }
    }

    fun onDateChange(value: LocalDate?) = edit {
        date = value
        if (value == null) time = null
    }

    fun onTimeChange(value: LocalTime?) = edit { time = value }

    fun onLocationChange(value: String) = edit { location = value.take(RequirementValidator.MAX_LOCATION_LENGTH) }

    fun onParticipantsChange(value: String) = edit { participantsText = value.filter(Char::isDigit).take(2) }

    fun publish(zone: ZoneId = ZoneId.systemDefault()) {
        val chosen = category ?: return
        if (isWorking) return
        val participants = participantsText.trim().let { if (it.isEmpty()) null else it.toIntOrNull() }
        val draft = RequirementDraft(
            title = title,
            description = description,
            category = chosen,
            tags = tags,
            locationText = location.ifBlank { null },
            startsAt = date?.let { ZonedDateTime.of(it, time ?: LocalTime.NOON, zone).toInstant().toString() },
            participantsNeeded = participants,
        )
        isWorking = true
        error = null
        viewModelScope.launch {
            when (val result = createRequirement(draft)) {
                is FormResult.Invalid -> inputErrors = result.errors
                is FormResult.Failed -> error = result.error
                is FormResult.Success -> publishedId = result.value
            }
            isWorking = false
        }
    }

    private inline fun edit(change: () -> Unit) {
        inputErrors = emptySet()
        error = null
        change()
    }
}

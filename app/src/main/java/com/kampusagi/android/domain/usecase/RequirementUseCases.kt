package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.repository.RequirementRepository
import java.util.Locale
import javax.inject.Inject

enum class DraftInputError { TITLE_INVALID, DESCRIPTION_INVALID, LOCATION_INVALID, PARTICIPANTS_INVALID, TAGS_INVALID }

/** Same rules as `_shared/requirement.ts` and the requirements table. */
object RequirementValidator {
    val TEXT_LENGTH = 10..1000
    val TITLE_LENGTH = 3..120
    val DESCRIPTION_LENGTH = 1..1000
    const val MAX_LOCATION_LENGTH = 120
    val PARTICIPANTS = 1..50
    const val MAX_TAGS = 8
    const val MAX_TAG_LENGTH = 30
    private val TURKISH = Locale.forLanguageTag("tr-TR")

    fun isValidText(text: String): Boolean = text.trim().length in TEXT_LENGTH

    /** "Spor, basket , spor" -> [spor, basket] */
    fun parseTags(input: String): List<String> =
        input.split(',').map { it.trim().lowercase(TURKISH) }.filter { it.isNotEmpty() }.distinct()

    fun validate(draft: RequirementDraft): Set<DraftInputError> = buildSet {
        if (draft.title.trim().length !in TITLE_LENGTH) add(DraftInputError.TITLE_INVALID)
        if (draft.description.trim().length !in DESCRIPTION_LENGTH) add(DraftInputError.DESCRIPTION_INVALID)
        val location = draft.locationText
        if (location != null && (location.isBlank() || location.trim().length > MAX_LOCATION_LENGTH)) {
            add(DraftInputError.LOCATION_INVALID)
        }
        val participants = draft.participantsNeeded
        if (participants != null && participants !in PARTICIPANTS) add(DraftInputError.PARTICIPANTS_INVALID)
        if (draft.tags.size > MAX_TAGS || draft.tags.any { it.isBlank() || it.length > MAX_TAG_LENGTH }) {
            add(DraftInputError.TAGS_INVALID)
        }
    }
}

class AnalyzeRequirementUseCase @Inject constructor(private val repository: RequirementRepository) {
    suspend operator fun invoke(text: String): AppResult<RequirementDraft> {
        if (!RequirementValidator.isValidText(text)) return AppResult.Failure(AppError.INVALID_INPUT)
        return repository.analyze(text.trim())
    }
}

class PublishRequirementUseCase @Inject constructor(private val repository: RequirementRepository) {
    suspend operator fun invoke(originalText: String, draft: RequirementDraft): FormResult<String, DraftInputError> {
        val errors = RequirementValidator.validate(draft)
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        val cleaned = draft.copy(
            title = draft.title.trim(),
            description = draft.description.trim(),
            locationText = draft.locationText?.trim(),
        )
        return when (val result = repository.publish(originalText.trim(), cleaned)) {
            is AppResult.Success -> FormResult.Success(result.value)
            is AppResult.Failure -> FormResult.Failed(result.error)
        }
    }
}

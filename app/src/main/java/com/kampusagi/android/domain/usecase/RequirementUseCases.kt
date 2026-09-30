package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.repository.RequirementRepository
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject

enum class DraftInputError { TITLE_INVALID, DESCRIPTION_INVALID, LOCATION_INVALID, PARTICIPANTS_INVALID, TAGS_INVALID, TIME_INVALID }

/** Same rules as `create_requirement` in the database. */
object RequirementValidator {
    val TITLE_LENGTH = 3..120
    const val MAX_DESCRIPTION_LENGTH = 1000
    const val MAX_LOCATION_LENGTH = 120
    val PARTICIPANTS = 1..50
    const val MAX_TAGS = 8
    const val MAX_TAG_LENGTH = 30
    private val TURKISH = Locale.forLanguageTag("tr-TR")

    /** "Spor, basket , spor" -> [spor, basket] */
    fun parseTags(input: String): List<String> =
        input.split(',').map { normalizeTag(it) }.filter { it.isNotEmpty() }.distinct()

    /** Trimmed, single-spaced, Turkish lower case — how the database stores a tag before synonyms. */
    fun normalizeTag(tag: String): String = tag.trim().replace(Regex("\\s+"), " ").lowercase(TURKISH)

    fun validate(draft: RequirementDraft, now: Instant = Instant.now()): Set<DraftInputError> = buildSet {
        if (draft.title.trim().length !in TITLE_LENGTH) add(DraftInputError.TITLE_INVALID)
        if (draft.description.trim().length > MAX_DESCRIPTION_LENGTH) add(DraftInputError.DESCRIPTION_INVALID)
        val location = draft.locationText
        if (location != null && (location.isBlank() || location.trim().length > MAX_LOCATION_LENGTH)) {
            add(DraftInputError.LOCATION_INVALID)
        }
        val participants = draft.participantsNeeded
        if (participants != null && participants !in PARTICIPANTS) add(DraftInputError.PARTICIPANTS_INVALID)
        if (draft.tags.size > MAX_TAGS || draft.tags.any { it.isBlank() || it.length > MAX_TAG_LENGTH }) {
            add(DraftInputError.TAGS_INVALID)
        }
        val startsAt = draft.startsAt?.let(::parseInstant)
        if (draft.startsAt != null &&
            (startsAt == null || startsAt.isBefore(now.minus(Duration.ofDays(1))) || startsAt.isAfter(now.plus(Duration.ofDays(365))))
        ) {
            add(DraftInputError.TIME_INVALID)
        }
    }
}

/** Null for text that is not an ISO-8601 instant; the caller reports it as TIME_INVALID. */
private fun parseInstant(value: String): Instant? = try {
    Instant.parse(value)
} catch (e: DateTimeParseException) {
    null
}

/**
 * Tags offered as chips for each category. They are the canonical names in
 * `requirement_synonyms`, so two students picking them always match.
 */
object RequirementTags {
    fun suggestions(category: RequirementCategory): List<String> = when (category) {
        RequirementCategory.SPORTS -> listOf("futbol", "basketbol", "voleybol", "tenis", "masa tenisi", "koşu", "spor salonu")
        RequirementCategory.STUDY -> listOf("ders çalışma", "sınav", "kütüphane", "özel ders", "ders notu", "ödev")
        RequirementCategory.PROJECT -> listOf("proje", "bitirme projesi", "hackathon", "yazılım", "tasarım")
        RequirementCategory.TRANSPORT -> listOf("yolculuk", "araç paylaşımı", "taksi paylaşımı", "havalimanı")
        RequirementCategory.ITEM -> listOf("ikinci el", "kitap", "elektronik", "bisiklet", "eşya")
        RequirementCategory.EVENT -> listOf("konser", "festival", "parti", "sinema", "kahve", "yemek", "oyun")
        RequirementCategory.HOUSING -> listOf("ev arkadaşı", "yurt", "kiralık ev", "eşya")
        RequirementCategory.OTHER -> listOf("kahve", "yemek", "oyun")
    }
}

class CreateRequirementUseCase @Inject constructor(private val repository: RequirementRepository) {
    suspend operator fun invoke(draft: RequirementDraft): FormResult<String, DraftInputError> {
        val errors = RequirementValidator.validate(draft)
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        val cleaned = draft.copy(
            title = draft.title.trim(),
            description = draft.description.trim(),
            locationText = draft.locationText?.trim(),
        )
        return when (val result = repository.create(cleaned)) {
            is AppResult.Success -> FormResult.Success(result.value)
            is AppResult.Failure -> FormResult.Failed(result.error)
        }
    }
}

package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.domain.model.Requirement
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.repository.RequirementRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val draft = RequirementDraft(
    title = "  Basketbol maçı ",
    description = " Kampüs sahasında maç ",
    category = RequirementCategory.SPORTS,
    tags = listOf("spor"),
    locationText = " Kampüs sahası ",
    startsAt = "2026-09-27T15:00:00.000Z",
    participantsNeeded = 2,
)

/** Test-only repository that records what would be sent to the backend. */
private class RecordingRequirementRepository : RequirementRepository {
    var analyzed: String? = null
    var published: Pair<String, RequirementDraft>? = null
    override suspend fun analyze(text: String): AppResult<RequirementDraft> =
        AppResult.Success(draft).also { analyzed = text }
    override suspend fun publish(originalText: String, draft: RequirementDraft): AppResult<String> =
        AppResult.Success("id-1").also { published = originalText to draft }
    override suspend fun myRequirements(): AppResult<List<Requirement>> = AppResult.Success(emptyList())
    override suspend fun close(requirementId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun matches(requirementId: String): AppResult<List<Match>> = AppResult.Success(emptyList())
}

class RequirementUseCasesTest {

    @Test
    fun `tags are split, trimmed, lowercased in Turkish and deduplicated`() {
        assertEquals(listOf("spor", "ıslak", "istanbul"), RequirementValidator.parseTags("Spor, ISLAK , spor,, İstanbul"))
    }

    @Test
    fun `draft limits match the backend`() {
        assertEquals(emptySet<DraftInputError>(), RequirementValidator.validate(draft))
        assertEquals(setOf(DraftInputError.TITLE_INVALID), RequirementValidator.validate(draft.copy(title = "ab")))
        assertEquals(setOf(DraftInputError.PARTICIPANTS_INVALID), RequirementValidator.validate(draft.copy(participantsNeeded = 51)))
        assertEquals(setOf(DraftInputError.LOCATION_INVALID), RequirementValidator.validate(draft.copy(locationText = " ")))
        assertEquals(
            setOf(DraftInputError.TAGS_INVALID),
            RequirementValidator.validate(draft.copy(tags = List(9) { "etiket$it" })),
        )
        assertEquals(
            setOf(DraftInputError.DESCRIPTION_INVALID),
            RequirementValidator.validate(draft.copy(description = "a".repeat(1001))),
        )
    }

    @Test
    fun `short text is not sent for analysis`() = runTest {
        val repository = RecordingRequirementRepository()
        assertEquals(AppResult.Failure(AppError.INVALID_INPUT), AnalyzeRequirementUseCase(repository)("  kısa  "))
        assertNull(repository.analyzed)
        AnalyzeRequirementUseCase(repository)("  Basketbol oynayacak iki kişi arıyorum  ")
        assertEquals("Basketbol oynayacak iki kişi arıyorum", repository.analyzed)
    }

    @Test
    fun `publish trims the edited draft and never sends an invalid one`() = runTest {
        val repository = RecordingRequirementRepository()
        val useCase = PublishRequirementUseCase(repository)
        assertEquals(
            FormResult.Invalid(setOf(DraftInputError.TITLE_INVALID)),
            useCase("Basketbol oynayacak iki kişi", draft.copy(title = "")),
        )
        assertNull(repository.published)

        assertEquals(FormResult.Success("id-1"), useCase(" Basketbol oynayacak iki kişi ", draft))
        val (text, sent) = repository.published!!
        assertEquals("Basketbol oynayacak iki kişi", text)
        assertEquals("Basketbol maçı", sent.title)
        assertEquals("Kampüs sahasında maç", sent.description)
        assertEquals("Kampüs sahası", sent.locationText)
    }
}

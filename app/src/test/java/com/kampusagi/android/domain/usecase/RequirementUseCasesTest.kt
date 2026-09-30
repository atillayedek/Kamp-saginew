package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.domain.model.Requirement
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.repository.RequirementRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    var created: RequirementDraft? = null
    override suspend fun create(draft: RequirementDraft): AppResult<String> =
        AppResult.Success("id-1").also { created = draft }
    override suspend fun myRequirements(): AppResult<List<Requirement>> = AppResult.Success(emptyList())
    override suspend fun close(requirementId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun matches(requirementId: String): AppResult<List<Match>> = AppResult.Success(emptyList())
}

class RequirementUseCasesTest {

    private val now = Instant.parse("2026-09-26T12:00:00Z")

    @Test
    fun `tags are split, trimmed, single-spaced, lowercased in Turkish and deduplicated`() {
        assertEquals(
            listOf("spor", "ıslak", "istanbul", "ev arkadaşı"),
            RequirementValidator.parseTags("Spor, ISLAK , spor,, İstanbul, EV   ARKADAŞI"),
        )
    }

    @Test
    fun `draft limits match the backend`() {
        assertEquals(emptySet<DraftInputError>(), RequirementValidator.validate(draft, now))
        assertEquals(emptySet<DraftInputError>(), RequirementValidator.validate(draft.copy(description = ""), now))
        assertEquals(setOf(DraftInputError.TITLE_INVALID), RequirementValidator.validate(draft.copy(title = "ab"), now))
        assertEquals(setOf(DraftInputError.PARTICIPANTS_INVALID), RequirementValidator.validate(draft.copy(participantsNeeded = 51), now))
        assertEquals(setOf(DraftInputError.LOCATION_INVALID), RequirementValidator.validate(draft.copy(locationText = " "), now))
        assertEquals(
            setOf(DraftInputError.TAGS_INVALID),
            RequirementValidator.validate(draft.copy(tags = List(9) { "etiket$it" }), now),
        )
        assertEquals(
            setOf(DraftInputError.DESCRIPTION_INVALID),
            RequirementValidator.validate(draft.copy(description = "a".repeat(1001)), now),
        )
        assertEquals(
            setOf(DraftInputError.TIME_INVALID),
            RequirementValidator.validate(draft.copy(startsAt = "2026-09-24T12:00:00Z"), now),
        )
        assertEquals(
            setOf(DraftInputError.TIME_INVALID),
            RequirementValidator.validate(draft.copy(startsAt = "2027-10-01T12:00:00Z"), now),
        )
        assertEquals(setOf(DraftInputError.TIME_INVALID), RequirementValidator.validate(draft.copy(startsAt = "yarın"), now))
    }

    @Test
    fun `every category offers tags that fit the backend limits`() {
        RequirementCategory.entries.forEach { category ->
            val tags = RequirementTags.suggestions(category)
            assertTrue(tags.isNotEmpty())
            assertTrue(tags.all { it == RequirementValidator.normalizeTag(it) && it.length <= RequirementValidator.MAX_TAG_LENGTH })
        }
    }

    @Test
    fun `create trims the draft and never sends an invalid one`() = runTest {
        val repository = RecordingRequirementRepository()
        val useCase = CreateRequirementUseCase(repository)
        // The use case validates against the real clock, so the time must be in the future.
        val future = draft.copy(startsAt = Instant.now().plusSeconds(3600).toString())
        assertEquals(FormResult.Invalid(setOf(DraftInputError.TITLE_INVALID)), useCase(future.copy(title = "")))
        assertNull(repository.created)

        assertEquals(FormResult.Success("id-1"), useCase(future))
        val sent = repository.created!!
        assertEquals("Basketbol maçı", sent.title)
        assertEquals("Kampüs sahasında maç", sent.description)
        assertEquals("Kampüs sahası", sent.locationText)
    }
}

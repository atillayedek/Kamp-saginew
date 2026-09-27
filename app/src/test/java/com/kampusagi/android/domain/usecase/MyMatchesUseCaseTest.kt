package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.domain.model.MatchOwner
import com.kampusagi.android.domain.model.Requirement
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.model.RequirementStatus
import com.kampusagi.android.domain.repository.RequirementRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun requirement(id: String, status: RequirementStatus = RequirementStatus.ACTIVE) = Requirement(
    id = id, title = "İhtiyaç $id", description = "açıklama", category = RequirementCategory.STUDY, tags = emptyList(),
    locationText = null, startsAt = null, participantsNeeded = null, status = status, createdAt = "2026-09-27T10:00:00Z",
)

private fun match(id: String, score: Int) = Match(
    requirementId = id, title = "Eşleşme $id", description = "d", category = RequirementCategory.STUDY, tags = emptyList(),
    locationText = null, startsAt = null, participantsNeeded = null, createdAt = "2026-09-27T10:00:00Z", score = score,
    owner = MatchOwner("u-$id", "Öğrenci $id", "ogrenci$id", "Hukuk"),
)

/** Test-only repository with scripted requirements and matches. */
private class ScriptedRequirements(
    private val mine: AppResult<List<Requirement>>,
    private val matchesById: Map<String, AppResult<List<Match>>>,
) : RequirementRepository {
    val matchCalls = mutableListOf<String>()
    override suspend fun analyze(text: String): AppResult<RequirementDraft> = AppResult.Failure(AppError.UNKNOWN)
    override suspend fun publish(originalText: String, draft: RequirementDraft): AppResult<String> = AppResult.Failure(AppError.UNKNOWN)
    override suspend fun myRequirements(): AppResult<List<Requirement>> = mine
    override suspend fun close(requirementId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun matches(requirementId: String): AppResult<List<Match>> {
        matchCalls += requirementId
        return matchesById[requirementId] ?: AppResult.Success(emptyList())
    }
}

class MyMatchesUseCaseTest {

    @Test
    fun `groups matches of active requirements, best first`() = runTest {
        val repository = ScriptedRequirements(
            AppResult.Success(listOf(requirement("a"), requirement("b"), requirement("closed", RequirementStatus.CLOSED))),
            mapOf(
                "a" to AppResult.Success(listOf(match("1", 40), match("2", 70))),
                "b" to AppResult.Success(listOf(match("3", 90))),
            ),
        )
        val groups = (MyMatchesUseCase(repository)() as AppResult.Success).value
        assertEquals(listOf("b", "a"), groups.map { it.requirement.id })
        assertEquals(listOf(70, 40), groups[1].matches.map { it.score })
        assertEquals(setOf("a", "b"), repository.matchCalls.toSet())
    }

    @Test
    fun `requirements without matches come last`() = runTest {
        val repository = ScriptedRequirements(
            AppResult.Success(listOf(requirement("empty"), requirement("full"))),
            mapOf("full" to AppResult.Success(listOf(match("1", 10)))),
        )
        val groups = (MyMatchesUseCase(repository)() as AppResult.Success).value
        assertEquals(listOf("full", "empty"), groups.map { it.requirement.id })
        assertTrue(groups[1].matches.isEmpty())
    }

    @Test
    fun `any failure fails the whole load`() = runTest {
        val failing = ScriptedRequirements(
            AppResult.Success(listOf(requirement("a"), requirement("b"))),
            mapOf("b" to AppResult.Failure(AppError.NETWORK)),
        )
        assertEquals(AppResult.Failure(AppError.NETWORK), MyMatchesUseCase(failing)())
        val noList = ScriptedRequirements(AppResult.Failure(AppError.SESSION_EXPIRED), emptyMap())
        assertEquals(AppResult.Failure(AppError.SESSION_EXPIRED), MyMatchesUseCase(noList)())
    }
}

package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.domain.model.Requirement
import com.kampusagi.android.domain.model.RequirementStatus
import com.kampusagi.android.domain.repository.RequirementRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Matches found for one of the person's active requirements. */
data class MatchGroup(val requirement: Requirement, val matches: List<Match>)

/**
 * Everything the "Eşleşmeler" tab shows: the matches of each active
 * requirement, best-matched requirements first. Any failed call fails the
 * whole load, so the screen never shows a partial list as if it were complete.
 */
class MyMatchesUseCase @Inject constructor(private val repository: RequirementRepository) {

    suspend operator fun invoke(): AppResult<List<MatchGroup>> {
        val requirements = when (val result = repository.myRequirements()) {
            is AppResult.Success -> result.value.filter { it.status == RequirementStatus.ACTIVE }
            is AppResult.Failure -> return result
        }
        val results = coroutineScope {
            requirements.map { requirement -> async { requirement to repository.matches(requirement.id) } }.awaitAll()
        }
        val groups = results.map { (requirement, result) ->
            when (result) {
                is AppResult.Success -> MatchGroup(requirement, result.value.sortedByDescending { it.score })
                is AppResult.Failure -> return result
            }
        }
        return AppResult.Success(groups.sortedByDescending { group -> group.matches.firstOrNull()?.score ?: -1 })
    }
}

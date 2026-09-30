package com.kampusagi.android.data.repository

import com.kampusagi.android.data.remote.MatchDto
import com.kampusagi.android.data.remote.RequirementDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Match
import com.kampusagi.android.domain.model.MatchOwner
import com.kampusagi.android.domain.model.Requirement
import com.kampusagi.android.domain.model.RequirementCategory
import com.kampusagi.android.domain.model.RequirementDraft
import com.kampusagi.android.domain.model.RequirementStatus
import com.kampusagi.android.domain.repository.RequirementRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@Singleton
class RequirementRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : RequirementRepository {

    override suspend fun create(draft: RequirementDraft): AppResult<String> = call { client ->
        client.postgrest.rpc(
            "create_requirement",
            buildJsonObject {
                put("p_title", draft.title)
                put("p_description", draft.description)
                put("p_category", draft.category.name)
                putJsonArray("p_tags") { draft.tags.forEach { add(it) } }
                put("p_location_text", draft.locationText)
                put("p_starts_at", draft.startsAt)
                put("p_participants_needed", draft.participantsNeeded)
            },
        ).decodeAs<String>()
    }

    override suspend fun myRequirements(): AppResult<List<Requirement>> = call { client ->
        client.postgrest.rpc("list_my_requirements", JsonObject(emptyMap()))
            .decodeList<RequirementDto>()
            .map {
                Requirement(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    category = parseCategory(it.category),
                    tags = it.tags,
                    locationText = it.locationText,
                    startsAt = it.startsAt,
                    participantsNeeded = it.participantsNeeded,
                    status = RequirementStatus.entries.firstOrNull { s -> s.name == it.status }
                        ?: throw UnknownStatusException(it.status),
                    createdAt = it.createdAt,
                )
            }
    }

    override suspend fun close(requirementId: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("close_requirement", JsonObject(mapOf("p_requirement_id" to JsonPrimitive(requirementId))))
        Unit
    }

    override suspend fun matches(requirementId: String): AppResult<List<Match>> = call { client ->
        client.postgrest.rpc(
            "find_matches",
            JsonObject(mapOf("p_requirement_id" to JsonPrimitive(requirementId))),
        ).decodeList<MatchDto>().map {
            Match(
                requirementId = it.requirementId,
                title = it.title,
                description = it.description,
                category = parseCategory(it.category),
                tags = it.tags,
                locationText = it.locationText,
                startsAt = it.startsAt,
                participantsNeeded = it.participantsNeeded,
                createdAt = it.createdAt,
                score = it.score.coerceIn(0, 100),
                sharedTags = it.sharedTags,
                owner = MatchOwner(it.ownerId, it.ownerFullName, it.ownerUsername, it.ownerDepartment),
            )
        }
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    private fun parseCategory(value: String) =
        RequirementCategory.entries.firstOrNull { it.name == value } ?: throw UnknownStatusException(value)
}

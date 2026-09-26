package com.kampusagi.android.data.repository

import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.AnalyzeResponseDto
import com.kampusagi.android.data.remote.MatchDto
import com.kampusagi.android.data.remote.PublishRequestDto
import com.kampusagi.android.data.remote.PublishResponseDto
import com.kampusagi.android.data.remote.RequirementDraftDto
import com.kampusagi.android.data.remote.RequirementDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.functionError
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
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class RequirementRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : RequirementRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(text: String): AppResult<RequirementDraft> = call { client ->
        val response = client.functions.invoke(
            function = AppConfig.ANALYZE_REQUIREMENT_FUNCTION,
            body = buildJsonObject { put("text", text) },
        )
        json.decodeFromString<AnalyzeResponseDto>(successBody(response)).draft.toDomain()
    }

    override suspend fun publish(originalText: String, draft: RequirementDraft): AppResult<String> = call { client ->
        val response = client.functions.invoke(
            function = AppConfig.PUBLISH_REQUIREMENT_FUNCTION,
            body = PublishRequestDto(originalText, draft.toDto()),
        )
        json.decodeFromString<PublishResponseDto>(successBody(response)).id
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
                owner = MatchOwner(it.ownerId, it.ownerFullName, it.ownerUsername, it.ownerDepartment),
            )
        }
    }

    /** Throws [FunctionFailure] for a non-2xx answer so [call] maps its error code. */
    private suspend fun successBody(response: HttpResponse): String {
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw FunctionFailure(functionError(response.status.value, body))
        return body
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(if (it is FunctionFailure) it.error else it.toAppError()) },
        )
    }

    private fun parseCategory(value: String) =
        RequirementCategory.entries.firstOrNull { it.name == value } ?: throw UnknownStatusException(value)

    private fun RequirementDraftDto.toDomain() = RequirementDraft(
        title = title,
        description = description,
        category = parseCategory(category),
        tags = tags,
        locationText = locationText,
        startsAt = startsAt,
        participantsNeeded = participantsNeeded,
    )

    private fun RequirementDraft.toDto() = RequirementDraftDto(
        title = title,
        description = description,
        category = category.name,
        tags = tags,
        locationText = locationText,
        startsAt = startsAt,
        participantsNeeded = participantsNeeded,
    )

    private class FunctionFailure(val error: AppError) : IllegalStateException("Edge Function failed: $error")
}

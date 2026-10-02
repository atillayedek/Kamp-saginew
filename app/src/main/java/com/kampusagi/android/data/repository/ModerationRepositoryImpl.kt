package com.kampusagi.android.data.repository

import com.kampusagi.android.data.remote.BlockedUserDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.BlockedUser
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.repository.ModerationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class ModerationRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : ModerationRepository {

    override suspend fun block(userId: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("block_user", buildJsonObject { put("p_user_id", userId) })
        Unit
    }

    override suspend fun unblock(userId: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("unblock_user", buildJsonObject { put("p_user_id", userId) })
        Unit
    }

    override suspend fun blockedUsers(): AppResult<List<BlockedUser>> = call { client ->
        client.postgrest.rpc("list_blocked_users", JsonObject(emptyMap()))
            .decodeList<BlockedUserDto>()
            .map { BlockedUser(it.userId, it.fullName, it.username, it.blockedAt) }
    }

    override suspend fun report(
        target: ReportTarget,
        targetId: String,
        reason: ReportReason,
        details: String?,
    ): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "report_content",
            buildJsonObject {
                put("p_target_kind", target.name)
                put("p_target_id", targetId)
                put("p_reason", reason.name)
                put("p_details", details?.trim()?.ifEmpty { null })
            },
        )
        Unit
    }

    override suspend fun conversationPartner(conversationId: String): AppResult<String> = call { client ->
        client.postgrest.rpc("conversation_partner", buildJsonObject { put("p_conversation_id", conversationId) })
            .decodeAs<String>()
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }
}

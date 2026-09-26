package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.data.remote.NotificationDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppNotification
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.NotificationKind
import com.kampusagi.android.domain.repository.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : NotificationRepository {

    override suspend fun notifications(): AppResult<List<AppNotification>> = call { client ->
        client.postgrest.rpc("list_notifications", JsonObject(emptyMap()))
            .decodeList<NotificationDto>()
            .map {
                AppNotification(
                    id = it.id,
                    kind = NotificationKind.entries.firstOrNull { k -> k.name == it.kind } ?: throw UnknownStatusException(it.kind),
                    createdAt = it.createdAt,
                    read = it.readAt != null,
                    actorName = it.actorFullName ?: it.actorUsername?.let { name -> "@$name" },
                    conversationId = it.conversationId,
                    postId = it.postId,
                )
            }
    }

    override suspend fun markRead(ids: List<String>?): AppResult<Unit> = call { client ->
        val param = ids?.let { list -> JsonArray(list.map { JsonPrimitive(it) }) } ?: JsonNull
        client.postgrest.rpc("mark_notifications_read", JsonObject(mapOf("p_ids" to param)))
        Unit
    }

    override fun changes(): Flow<Unit> = channelFlow {
        val client = provider.client ?: return@channelFlow
        val channel = client.channel("notifications-${UUID.randomUUID()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "notifications" }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            // RLS delivers only the person's own rows.
            changes.collect { send(Unit) }
        } finally {
            withContext(NonCancellable) {
                safeCall { client.realtime.removeChannel(channel) }
                    .onFailure { Log.w(TAG, "Realtime channel could not be removed", it) }
            }
        }
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    private companion object {
        const val TAG = "NotificationRepository"
    }
}

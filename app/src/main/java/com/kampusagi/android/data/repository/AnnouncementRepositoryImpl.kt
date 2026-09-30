package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.data.remote.AnnouncementDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.Announcement
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.AnnouncementRepository
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class AnnouncementRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : AnnouncementRepository {

    override suspend fun active(): AppResult<List<Announcement>> = call { client ->
        client.postgrest.rpc("active_announcements", JsonObject(emptyMap())).decodeList<AnnouncementDto>().map {
            Announcement(it.id, it.title, it.body, it.createdAt, it.endsAt)
        }
    }

    override suspend fun dismiss(announcementId: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("dismiss_announcement", JsonObject(mapOf("p_announcement_id" to JsonPrimitive(announcementId))))
        Unit
    }

    override fun changes(): Flow<Unit> = channelFlow {
        val client = provider.client ?: return@channelFlow
        val channel = client.channel("announcements-${UUID.randomUUID()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "announcements" }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            changes.collect { send(Unit) }
        } finally {
            withContext(NonCancellable) {
                safeCall { client.realtime.removeChannel(channel) }
                    .onFailure { Log.w(TAG, "Realtime channel could not be removed", it) }
            }
        }
    }

    override suspend fun touchActivity(): AppResult<Unit> = call { client ->
        client.postgrest.rpc("touch_activity", JsonObject(emptyMap()))
        Unit
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    private companion object {
        const val TAG = "Announcements"
    }
}

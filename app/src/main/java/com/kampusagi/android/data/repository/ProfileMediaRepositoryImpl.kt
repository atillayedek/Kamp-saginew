package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.AvatarPathDto
import com.kampusagi.android.data.remote.ProfileStatsDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.model.ProfileStats
import com.kampusagi.android.domain.repository.ProfileMediaRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class ProfileMediaRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    private val profileRepository: ProfileRepository,
) : ProfileMediaRepository {

    override suspend fun updateBio(bio: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("update_bio", JsonObject(mapOf("p_bio" to JsonPrimitive(bio))))
        profileRepository.refresh()
    }

    override suspend fun uploadAvatar(jpeg: ByteArray): AppResult<Unit> = call { client ->
        val userId = client.auth.currentUserOrNull()?.id ?: throw SessionMissingException()
        val previous = currentAvatarPath()
        val path = "$userId/${UUID.randomUUID()}.jpg"
        client.storage.from(AppConfig.AVATARS_BUCKET).upload(path, jpeg) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        client.postgrest.rpc("set_avatar", JsonObject(mapOf("p_path" to JsonPrimitive(path))))
        profileRepository.refresh()
        previous?.let { deleteQuietly(client, it) }
        Unit
    }

    override suspend fun removeAvatar(): AppResult<Unit> = call { client ->
        val previous = currentAvatarPath()
        client.postgrest.rpc("remove_avatar", JsonObject(emptyMap()))
        profileRepository.refresh()
        previous?.let { deleteQuietly(client, it) }
        Unit
    }

    override suspend fun stats(): AppResult<ProfileStats> = call { client ->
        val row = client.postgrest.rpc("my_profile_stats", JsonObject(emptyMap())).decodeList<ProfileStatsDto>().first()
        ProfileStats(row.postCount, row.activeRequirementCount, row.conversationCount)
    }

    override suspend fun avatarPaths(userIds: Collection<String>): AppResult<Map<String, String>> = call { client ->
        if (userIds.isEmpty()) return@call emptyMap()
        client.postgrest.rpc(
            "avatar_paths",
            buildJsonObject { put("p_user_ids", buildJsonArray { userIds.forEach { add(JsonPrimitive(it)) } }) },
        ).decodeList<AvatarPathDto>().associate { it.userId to it.avatarPath }
    }

    override suspend fun downloadAvatar(path: String): AppResult<ByteArray> = call { client ->
        client.storage.from(AppConfig.AVATARS_BUCKET).downloadAuthenticated(path)
    }

    private fun currentAvatarPath(): String? =
        (profileRepository.profileState.value as? ProfileState.Loaded)?.profile?.avatarPath

    /** The old photo is no longer referenced; if deleting it fails it only costs storage. */
    private suspend fun deleteQuietly(client: SupabaseClient, path: String) {
        safeCall { client.storage.from(AppConfig.AVATARS_BUCKET).delete(path) }
            .onFailure { Log.w(TAG, "Previous profile photo could not be deleted", it) }
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(if (it is SessionMissingException) AppError.SESSION_EXPIRED else it.toAppError()) },
        )
    }

    private class SessionMissingException : IllegalStateException("No signed-in user")

    private companion object {
        const val TAG = "ProfileMedia"
    }
}

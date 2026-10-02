package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.GroupDetailDto
import com.kampusagi.android.data.remote.GroupMemberDto
import com.kampusagi.android.data.remote.GroupMessageDto
import com.kampusagi.android.data.remote.GroupSummaryDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.data.remote.toDomain
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.GroupDetail
import com.kampusagi.android.domain.model.GroupKind
import com.kampusagi.android.domain.model.GroupMember
import com.kampusagi.android.domain.model.GroupMessage
import com.kampusagi.android.domain.model.GroupRole
import com.kampusagi.android.domain.model.GroupSummary
import com.kampusagi.android.domain.model.MessageCursor
import com.kampusagi.android.domain.model.NewGroup
import com.kampusagi.android.domain.model.NewGroupMessage
import com.kampusagi.android.domain.repository.GroupRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : GroupRepository {

    override suspend fun isPremium(): AppResult<Boolean> = call { client ->
        client.postgrest.rpc("am_i_premium", JsonObject(emptyMap())).decodeAs<Boolean>()
    }

    override suspend fun myGroups(kind: GroupKind): AppResult<List<GroupSummary>> = call { client ->
        client.postgrest.rpc("list_my_groups", params("p_kind" to kind.name)).decodeList<GroupSummaryDto>().map { it.toDomain() }
    }

    override suspend fun discover(kind: GroupKind, query: String?): AppResult<List<GroupSummary>> = call { client ->
        client.postgrest.rpc(
            "discover_groups",
            buildJsonObject {
                put("p_kind", kind.name)
                put("p_query", query?.trim()?.ifEmpty { null })
            },
        ).decodeList<GroupSummaryDto>().map { it.toDomain() }
    }

    override suspend fun group(groupId: String): AppResult<GroupDetail> = call { client ->
        val row = client.postgrest.rpc("get_group", params("p_group_id" to groupId)).decodeList<GroupDetailDto>().firstOrNull()
            ?: throw GroupMissingException()
        GroupDetail(
            id = row.groupId,
            kind = parseKind(row.kind),
            name = row.name,
            description = row.description,
            courseCode = row.courseCode,
            photoPath = row.photoPath,
            memberCount = row.memberCount,
            isGlobal = row.isGlobal,
            ownerId = row.ownerId,
            ownerName = row.ownerFullName,
            myRole = row.myRole?.let(::parseRole),
            canPost = row.canPost,
            ownerIsPremium = row.ownerIsPremium,
            pinnedMessageId = row.pinnedMessageId,
            pinnedMessageBody = row.pinnedMessageBody,
        )
    }

    override suspend fun members(groupId: String): AppResult<List<GroupMember>> = call { client ->
        client.postgrest.rpc("list_group_members", params("p_group_id" to groupId)).decodeList<GroupMemberDto>().map {
            GroupMember(it.userId, it.fullName, it.username, parseRole(it.role), it.joinedAt)
        }
    }

    override suspend fun create(group: NewGroup): AppResult<String> = call { client ->
        client.postgrest.rpc(
            "create_group",
            buildJsonObject {
                put("p_kind", group.kind.name)
                put("p_name", group.name.trim())
                put("p_description", group.description?.trim()?.ifEmpty { null })
                put("p_course_code", group.courseCode?.trim()?.ifEmpty { null })
                put("p_university_only", group.universityOnly)
            },
        ).decodeAs<String>()
    }

    override suspend fun update(groupId: String, name: String, description: String?, courseCode: String?): AppResult<Unit> =
        call { client ->
            client.postgrest.rpc(
                "update_group",
                buildJsonObject {
                    put("p_group_id", groupId)
                    put("p_name", name.trim())
                    put("p_description", description?.trim()?.ifEmpty { null })
                    put("p_course_code", courseCode?.trim()?.ifEmpty { null })
                },
            )
            Unit
        }

    override suspend fun setPhoto(groupId: String, jpeg: ByteArray?): AppResult<Unit> = call { client ->
        val path = jpeg?.let { upload(client, it) }
        try {
            client.postgrest.rpc(
                "set_group_photo",
                buildJsonObject {
                    put("p_group_id", groupId)
                    put("p_path", path)
                },
            )
        } catch (e: Throwable) {
            path?.let { deleteQuietly(client, it) }
            throw e
        }
        Unit
    }

    override suspend fun delete(groupId: String): AppResult<Unit> = rpcUnit("delete_group", "p_group_id" to groupId)

    override suspend fun join(groupId: String): AppResult<Unit> = rpcUnit("join_group", "p_group_id" to groupId)

    override suspend fun leave(groupId: String): AppResult<Unit> = rpcUnit("leave_group", "p_group_id" to groupId)

    override suspend fun setRole(groupId: String, userId: String, role: GroupRole): AppResult<Unit> =
        rpcUnit("set_group_member_role", "p_group_id" to groupId, "p_user_id" to userId, "p_role" to role.name)

    override suspend fun removeMember(groupId: String, userId: String): AppResult<Unit> =
        rpcUnit("remove_group_member", "p_group_id" to groupId, "p_user_id" to userId)

    override suspend fun messages(groupId: String, before: MessageCursor?): AppResult<List<GroupMessage>> = call { client ->
        client.postgrest.rpc(
            "list_group_messages",
            buildJsonObject {
                put("p_group_id", groupId)
                put("p_before_created_at", before?.createdAt)
                put("p_before_id", before?.id)
                put("p_limit", PAGE_SIZE)
            },
        ).decodeList<GroupMessageDto>().map {
            GroupMessage(
                id = it.id,
                sender = Author(it.senderId, it.senderFullName, it.senderUsername, university = null),
                body = it.body,
                mediaPath = it.mediaPath,
                createdAt = it.createdAt,
                likeCount = it.likeCount,
                likedByMe = it.likedByMe,
                isMine = it.isMine,
                poll = it.poll?.toDomain(),
            )
        }
    }

    override suspend fun send(groupId: String, messageId: String, message: NewGroupMessage): AppResult<Unit> = call { client ->
        val path = message.photo?.let { upload(client, it) }
        try {
            client.postgrest.rpc(
                "send_group_message",
                buildJsonObject {
                    put("p_group_id", groupId)
                    put("p_message_id", messageId)
                    put("p_body", message.body?.trim()?.ifEmpty { null })
                    put("p_media_path", path)
                    put("p_poll_options", message.pollOptions?.let { options -> JsonArray(options.map { JsonPrimitive(it.trim()) }) } ?: JsonNull)
                    put("p_poll_closes_at", message.pollClosesAt)
                },
            )
        } catch (e: Throwable) {
            path?.let { deleteQuietly(client, it) }
            throw e
        }
        Unit
    }

    override suspend fun deleteMessage(messageId: String): AppResult<Unit> = rpcUnit("delete_group_message", "p_message_id" to messageId)

    override suspend fun setLiked(messageId: String, liked: Boolean): AppResult<Int> = call { client ->
        client.postgrest.rpc(
            "set_group_message_liked",
            buildJsonObject {
                put("p_message_id", messageId)
                put("p_liked", liked)
            },
        ).decodeAs<Int>()
    }

    override suspend fun pin(groupId: String, messageId: String?): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "pin_group_message",
            buildJsonObject {
                put("p_group_id", groupId)
                put("p_message_id", messageId)
            },
        )
        Unit
    }

    override suspend fun markRead(groupId: String): AppResult<Unit> = rpcUnit("mark_group_read", "p_group_id" to groupId)

    override fun changes(groupId: String?): Flow<Unit> = channelFlow {
        val client = provider.client ?: return@channelFlow
        // A unique topic per subscriber: two screens may watch at the same time.
        val channel = client.channel("groups-${groupId ?: "all"}-${UUID.randomUUID()}")
        val messages = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "group_messages" }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            messages.collect { action ->
                // RLS limits events to the person's groups; this narrows to one of them.
                if (groupId == null || action.groupId() == groupId) send(Unit)
            }
        } finally {
            withContext(NonCancellable) {
                safeCall { client.realtime.removeChannel(channel) }
                    .onFailure { Log.w(TAG, "Realtime channel could not be removed", it) }
            }
        }
    }

    override suspend fun downloadPhoto(path: String): AppResult<ByteArray> = call { client ->
        client.storage.from(AppConfig.GROUP_MEDIA_BUCKET).downloadAuthenticated(path)
    }

    private suspend fun upload(client: SupabaseClient, jpeg: ByteArray): String {
        val userId = client.auth.currentUserOrNull()?.id ?: throw SessionMissingException()
        val path = "$userId/${UUID.randomUUID()}.jpg"
        client.storage.from(AppConfig.GROUP_MEDIA_BUCKET).upload(path, jpeg) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        return path
    }

    /** A photo that was uploaded but not attached would never be shown; removing it only saves storage. */
    private suspend fun deleteQuietly(client: SupabaseClient, path: String) {
        safeCall { client.storage.from(AppConfig.GROUP_MEDIA_BUCKET).delete(path) }
            .onFailure { Log.w(TAG, "Unused group photo could not be deleted", it) }
    }

    private fun PostgresAction.groupId(): String? {
        val record = when (this) {
            is PostgresAction.Insert -> record
            is PostgresAction.Update -> record
            else -> null
        }
        return (record?.get("group_id") as? JsonPrimitive)?.contentOrNull
    }

    private fun GroupSummaryDto.toDomain() = GroupSummary(
        id = groupId,
        kind = parseKind(kind),
        name = name,
        description = description,
        courseCode = courseCode,
        photoPath = photoPath,
        memberCount = memberCount,
        isGlobal = isGlobal,
        myRole = myRole?.let(::parseRole),
        lastMessageBody = lastMessageBody,
        lastMessageAt = lastMessageAt,
        unreadCount = unreadCount,
        ownerName = ownerFullName,
    )

    private fun parseKind(value: String) = GroupKind.entries.firstOrNull { it.name == value } ?: throw UnknownStatusException(value)

    private fun parseRole(value: String) = GroupRole.entries.firstOrNull { it.name == value } ?: throw UnknownStatusException(value)

    private suspend fun rpcUnit(function: String, vararg pairs: Pair<String, String>): AppResult<Unit> = call { client ->
        client.postgrest.rpc(function, params(*pairs))
        Unit
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                AppResult.Failure(
                    when (it) {
                        is GroupMissingException -> AppError.NOT_FOUND
                        is SessionMissingException -> AppError.SESSION_EXPIRED
                        else -> it.toAppError()
                    },
                )
            },
        )
    }

    private fun params(vararg pairs: Pair<String, String>) =
        JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) })

    private class GroupMissingException : IllegalStateException("Group not returned")
    private class SessionMissingException : IllegalStateException("No signed-in user")

    companion object {
        const val PAGE_SIZE = 50
        private const val TAG = "GroupRepository"
    }
}

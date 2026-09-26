package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.data.remote.ChatMessageDto
import com.kampusagi.android.data.remote.ConversationDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.ChatMessage
import com.kampusagi.android.domain.model.Conversation
import com.kampusagi.android.domain.model.MessageCursor
import com.kampusagi.android.domain.repository.ChatRepository
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : ChatRepository {

    override suspend fun conversations(): AppResult<List<Conversation>> = call { client ->
        client.postgrest.rpc("list_conversations", JsonObject(emptyMap()))
            .decodeList<ConversationDto>()
            .map {
                Conversation(
                    id = it.conversationId,
                    other = Author(it.otherUserId, it.otherFullName, it.otherUsername, it.otherUniversity),
                    lastMessageBody = it.lastMessageBody,
                    lastMessageAt = it.lastMessageAt,
                    lastMessageIsMine = it.lastMessageIsMine == true,
                    unreadCount = it.unreadCount,
                )
            }
    }

    override suspend fun startConversation(otherUserId: String): AppResult<String> = call { client ->
        client.postgrest.rpc("start_conversation", params("p_other_user_id" to otherUserId)).decodeAs<String>()
    }

    override suspend fun messages(conversationId: String, before: MessageCursor?): AppResult<List<ChatMessage>> =
        call { client ->
            client.postgrest.rpc(
                "list_messages",
                buildJsonObject {
                    put("p_conversation_id", conversationId)
                    put("p_before_created_at", before?.createdAt)
                    put("p_before_id", before?.id)
                    put("p_limit", PAGE_SIZE)
                },
            ).decodeList<ChatMessageDto>().map {
                ChatMessage(it.id, it.body, it.createdAt, it.isMine, it.readByOther)
            }
        }

    override suspend fun send(conversationId: String, messageId: String, body: String): AppResult<String> =
        call { client ->
            client.postgrest.rpc(
                "send_message",
                buildJsonObject {
                    put("p_conversation_id", conversationId)
                    put("p_message_id", messageId)
                    put("p_body", body)
                },
            ).decodeAs<String>()
        }

    override suspend fun markRead(conversationId: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc("mark_conversation_read", params("p_conversation_id" to conversationId))
        Unit
    }

    override fun changes(conversationId: String?): Flow<Unit> = channelFlow {
        val client = provider.client ?: return@channelFlow
        // A unique topic per subscriber: two screens may watch at the same time.
        val channel = client.channel("chat-${conversationId ?: "inbox"}-${UUID.randomUUID()}")
        val messages = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "messages" }
        val receipts = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "conversation_members" }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            merge(messages, receipts).collect { action ->
                // RLS already limits events to the person's conversations; this narrows to one of them.
                if (conversationId == null || action.conversationId() == conversationId) send(Unit)
            }
        } finally {
            withContext(NonCancellable) {
                safeCall { client.realtime.removeChannel(channel) }
                    .onFailure { Log.w(TAG, "Realtime channel could not be removed", it) }
            }
        }
    }

    private fun PostgresAction.conversationId(): String? {
        val record = when (this) {
            is PostgresAction.Insert -> record
            is PostgresAction.Update -> record
            else -> null
        }
        return (record?.get("conversation_id") as? JsonPrimitive)?.contentOrNull
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    private fun params(vararg pairs: Pair<String, String>) =
        JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) })

    companion object {
        const val PAGE_SIZE = 50
        private const val TAG = "ChatRepository"
    }
}

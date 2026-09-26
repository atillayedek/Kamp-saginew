package com.kampusagi.android.domain.model

data class Conversation(
    val id: String,
    val other: Author,
    val lastMessageBody: String?,
    val lastMessageAt: String?,
    val lastMessageIsMine: Boolean,
    val unreadCount: Int,
)

data class ChatMessage(
    val id: String,
    val body: String,
    val createdAt: String,
    val isMine: Boolean,
    /** True once the other member's last_read_at reached this message (server data). */
    val readByOther: Boolean,
)

data class MessageCursor(val createdAt: String, val id: String)

/** Mirrors `public.notification_kind`. */
enum class NotificationKind { NEW_MESSAGE, NEW_COMMENT, VERIFICATION_APPROVED, VERIFICATION_REJECTED }

data class AppNotification(
    val id: String,
    val kind: NotificationKind,
    val createdAt: String,
    val read: Boolean,
    val actorName: String?,
    val conversationId: String?,
    val postId: String?,
)

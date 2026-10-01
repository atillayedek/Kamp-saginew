package com.kampusagi.android.domain.model

/** Mirrors `public.group_kind`: a Premium broadcast channel or a free study group. */
enum class GroupKind { STUDY_GROUP, CHANNEL }

/** Mirrors `public.group_role`. */
enum class GroupRole { OWNER, ADMIN, MEMBER }

/** A group in a list: the person's own groups (with unread count) or ones to discover. */
data class GroupSummary(
    val id: String,
    val kind: GroupKind,
    val name: String,
    val description: String?,
    val courseCode: String?,
    val photoPath: String?,
    val memberCount: Int,
    /** Open to every university (channels only). */
    val isGlobal: Boolean,
    val myRole: GroupRole? = null,
    val lastMessageBody: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int = 0,
    val ownerName: String? = null,
)

data class GroupDetail(
    val id: String,
    val kind: GroupKind,
    val name: String,
    val description: String?,
    val courseCode: String?,
    val photoPath: String?,
    val memberCount: Int,
    val isGlobal: Boolean,
    val ownerId: String,
    val ownerName: String?,
    /** Null when the person has not joined. */
    val myRole: GroupRole?,
    /** Decided by the server: members of a study group; owner/admins of a channel whose owner has Premium. */
    val canPost: Boolean,
    val ownerIsPremium: Boolean,
    val pinnedMessageId: String?,
    val pinnedMessageBody: String?,
) {
    val canManage: Boolean get() = myRole == GroupRole.OWNER || myRole == GroupRole.ADMIN
}

data class GroupMember(
    val userId: String,
    val fullName: String?,
    val username: String?,
    val role: GroupRole,
    val joinedAt: String,
)

data class GroupMessage(
    val id: String,
    val sender: Author,
    val body: String?,
    val mediaPath: String?,
    val createdAt: String,
    val likeCount: Int,
    val likedByMe: Boolean,
    val isMine: Boolean,
    val poll: Poll?,
)

/** A message to send: text, an optional JPEG photo and, in channels, an optional poll. */
data class NewGroupMessage(
    val body: String?,
    val photo: ByteArray? = null,
    val pollOptions: List<String>? = null,
    val pollClosesAt: String? = null,
)

data class NewGroup(
    val kind: GroupKind,
    val name: String,
    val description: String?,
    val courseCode: String?,
    /** Channels only: false opens the channel to every university. */
    val universityOnly: Boolean,
)

data class CourseNote(
    val id: String,
    val courseCode: String,
    val courseName: String,
    val title: String,
    val description: String?,
    val fileSize: Long,
    val downloadCount: Int,
    val createdAt: String,
    val isMine: Boolean,
    val author: Author,
)

data class NoteCourse(val code: String, val name: String, val noteCount: Int)

data class NewCourseNote(
    val courseCode: String,
    val courseName: String,
    val title: String,
    val description: String?,
    val pdf: ByteArray,
    /** The uploader declared holding the rights or a permission to share (FSEK); required. */
    val rightsDeclared: Boolean,
)

/** Badges the server computes from real activity (`public.user_badges`). */
enum class Badge { PREMIUM, CHANNEL_OWNER, HELPFUL, NOTE_SHARER, POPULAR, EVENT_ORGANIZER, ACTIVE_MEMBER }

data class Reputation(val points: Int, val badges: List<Badge>)

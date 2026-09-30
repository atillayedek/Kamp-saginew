package com.kampusagi.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val email: String,
    @SerialName("full_name") val fullName: String? = null,
    val username: String? = null,
    @SerialName("university_id") val universityId: String? = null,
    val department: String? = null,
    @SerialName("account_status") val accountStatus: String,
    val bio: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    /** Embedded through the `university_id` foreign key. */
    val universities: UniversityNameDto? = null,
) {
    companion object {
        const val COLUMNS =
            "id,email,full_name,username,university_id,department,account_status,bio,avatar_path,universities(name)"
    }
}

@Serializable
data class UniversityNameDto(val name: String)

@Serializable
data class UniversityDto(
    val id: String,
    val name: String,
    val city: String,
)

@Serializable
data class VerificationDto(
    val id: String,
    val status: String,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PendingVerificationDto(
    @SerialName("verification_id") val verificationId: String,
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("full_name") val fullName: String? = null,
    val username: String? = null,
    @SerialName("university_name") val universityName: String? = null,
    val department: String? = null,
    @SerialName("document_path") val documentPath: String,
    @SerialName("submitted_at") val submittedAt: String,
)

@Serializable
data class PostDto(
    val id: String,
    val scope: String,
    val category: String = "GENERAL",
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("like_count") val likeCount: Int,
    @SerialName("comment_count") val commentCount: Int,
    @SerialName("liked_by_me") val likedByMe: Boolean,
    @SerialName("is_mine") val isMine: Boolean,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_full_name") val authorFullName: String? = null,
    @SerialName("author_username") val authorUsername: String? = null,
    @SerialName("author_university") val authorUniversity: String? = null,
    @SerialName("saved_by_me") val savedByMe: Boolean = false,
    val media: List<String> = emptyList(),
    val poll: PollDto? = null,
    val event: PostEventDto? = null,
    val listing: ListingDto? = null,
)

@Serializable
data class PollDto(
    val id: String,
    @SerialName("closes_at") val closesAt: String? = null,
    @SerialName("total_votes") val totalVotes: Int = 0,
    @SerialName("my_option_id") val myOptionId: String? = null,
    val options: List<PollOptionDto> = emptyList(),
)

@Serializable
data class PollOptionDto(val id: String, val label: String, val votes: Int)

@Serializable
data class PostEventDto(
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String? = null,
    val location: String? = null,
    @SerialName("attendee_count") val attendeeCount: Int = 0,
    val attending: Boolean = false,
)

@Serializable
data class ListingDto(
    @SerialName("price_kurus") val priceKurus: Long,
    val sold: Boolean = false,
)

@Serializable
data class PersonDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    val username: String? = null,
    val university: String? = null,
    val department: String? = null,
)

@Serializable
data class UserProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    val username: String? = null,
    val university: String? = null,
    val department: String? = null,
    val bio: String? = null,
    @SerialName("joined_at") val joinedAt: String,
    @SerialName("post_count") val postCount: Int,
    @SerialName("is_me") val isMe: Boolean,
)

@Serializable
data class CommentDto(
    val id: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_mine") val isMine: Boolean,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_full_name") val authorFullName: String? = null,
    @SerialName("author_username") val authorUsername: String? = null,
)

/** Draft exchanged with the analyze/publish Edge Functions (snake_case like the backend). */
@Serializable
data class RequirementDraftDto(
    val title: String,
    val description: String,
    val category: String,
    val tags: List<String>,
    @SerialName("location_text") val locationText: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("participants_needed") val participantsNeeded: Int? = null,
)

@Serializable
data class AnalyzeResponseDto(val draft: RequirementDraftDto)

@Serializable
data class PublishRequestDto(
    @SerialName("original_text") val originalText: String,
    val draft: RequirementDraftDto,
)

@Serializable
data class PublishResponseDto(val id: String)

@Serializable
data class RequirementDto(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: List<String> = emptyList(),
    @SerialName("location_text") val locationText: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("participants_needed") val participantsNeeded: Int? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MatchDto(
    @SerialName("requirement_id") val requirementId: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: List<String> = emptyList(),
    @SerialName("location_text") val locationText: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("participants_needed") val participantsNeeded: Int? = null,
    @SerialName("created_at") val createdAt: String,
    val score: Int,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("owner_full_name") val ownerFullName: String? = null,
    @SerialName("owner_username") val ownerUsername: String? = null,
    @SerialName("owner_department") val ownerDepartment: String? = null,
)

@Serializable
data class ConversationDto(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("other_user_id") val otherUserId: String,
    @SerialName("other_full_name") val otherFullName: String? = null,
    @SerialName("other_username") val otherUsername: String? = null,
    @SerialName("other_university") val otherUniversity: String? = null,
    @SerialName("last_message_body") val lastMessageBody: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_is_mine") val lastMessageIsMine: Boolean? = null,
    @SerialName("unread_count") val unreadCount: Int,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_mine") val isMine: Boolean,
    @SerialName("read_by_other") val readByOther: Boolean,
)

@Serializable
data class NotificationDto(
    val id: String,
    val kind: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("actor_full_name") val actorFullName: String? = null,
    @SerialName("actor_username") val actorUsername: String? = null,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("post_id") val postId: String? = null,
)

@Serializable
data class PlanDto(
    val id: String,
    @SerialName("play_product_id") val playProductId: String,
    val name: String,
    val description: String,
    @SerialName("ai_analyze_daily") val aiAnalyzeDaily: Int,
    @SerialName("ai_publish_daily") val aiPublishDaily: Int,
    @SerialName("max_active_requirements") val maxActiveRequirements: Int,
)

@Serializable
data class SubscriptionDto(
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("play_product_id") val playProductId: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("ai_analyze_daily") val aiAnalyzeDaily: Int,
    @SerialName("ai_publish_daily") val aiPublishDaily: Int,
    @SerialName("max_active_requirements") val maxActiveRequirements: Int,
)

@Serializable
data class VerifyPurchaseRequestDto(
    @SerialName("product_id") val productId: String,
    @SerialName("purchase_token") val purchaseToken: String,
)

@Serializable
data class BlockedUserDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    val username: String? = null,
    @SerialName("blocked_at") val blockedAt: String,
)

@Serializable
data class OpenReportDto(
    @SerialName("report_id") val reportId: String,
    @SerialName("target_kind") val targetKind: String,
    @SerialName("target_excerpt") val targetExcerpt: String,
    val reason: String,
    val details: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("report_count") val reportCount: Int,
    @SerialName("reporter_username") val reporterUsername: String? = null,
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("target_full_name") val targetFullName: String? = null,
    @SerialName("target_username") val targetUsername: String? = null,
    @SerialName("target_account_status") val targetAccountStatus: String,
)

@Serializable
data class DeleteAccountRequestDto(val confirm: String)

@Serializable
data class MarketingConsentDto(@SerialName("marketing_opt_in") val marketingOptIn: Boolean)

@Serializable
data class AvatarPathDto(
    @SerialName("user_id") val userId: String,
    @SerialName("avatar_path") val avatarPath: String,
)

@Serializable
data class ProfileStatsDto(
    @SerialName("post_count") val postCount: Int,
    @SerialName("active_requirement_count") val activeRequirementCount: Int,
    @SerialName("conversation_count") val conversationCount: Int,
)

@Serializable
data class GroupSummaryDto(
    @SerialName("group_id") val groupId: String,
    val kind: String,
    val name: String,
    val description: String? = null,
    @SerialName("course_code") val courseCode: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("member_count") val memberCount: Int,
    @SerialName("is_global") val isGlobal: Boolean,
    @SerialName("my_role") val myRole: String? = null,
    @SerialName("last_message_body") val lastMessageBody: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("owner_full_name") val ownerFullName: String? = null,
)

@Serializable
data class GroupDetailDto(
    @SerialName("group_id") val groupId: String,
    val kind: String,
    val name: String,
    val description: String? = null,
    @SerialName("course_code") val courseCode: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("member_count") val memberCount: Int,
    @SerialName("is_global") val isGlobal: Boolean,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("owner_full_name") val ownerFullName: String? = null,
    @SerialName("my_role") val myRole: String? = null,
    @SerialName("can_post") val canPost: Boolean,
    @SerialName("owner_is_premium") val ownerIsPremium: Boolean,
    @SerialName("pinned_message_id") val pinnedMessageId: String? = null,
    @SerialName("pinned_message_body") val pinnedMessageBody: String? = null,
)

@Serializable
data class GroupMemberDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    val username: String? = null,
    val role: String,
    @SerialName("joined_at") val joinedAt: String,
)

@Serializable
data class GroupMessageDto(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_full_name") val senderFullName: String? = null,
    @SerialName("sender_username") val senderUsername: String? = null,
    val body: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("like_count") val likeCount: Int,
    @SerialName("liked_by_me") val likedByMe: Boolean,
    @SerialName("is_mine") val isMine: Boolean,
    val poll: PollDto? = null,
)

@Serializable
data class CourseNoteDto(
    @SerialName("note_id") val noteId: String,
    @SerialName("course_code") val courseCode: String,
    @SerialName("course_name") val courseName: String,
    val title: String,
    val description: String? = null,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("download_count") val downloadCount: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_mine") val isMine: Boolean,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_full_name") val authorFullName: String? = null,
    @SerialName("author_username") val authorUsername: String? = null,
)

@Serializable
data class NoteCourseDto(
    @SerialName("course_code") val courseCode: String,
    @SerialName("course_name") val courseName: String,
    @SerialName("note_count") val noteCount: Int,
)

@Serializable
data class ReputationDto(val points: Int, val badges: List<String> = emptyList())

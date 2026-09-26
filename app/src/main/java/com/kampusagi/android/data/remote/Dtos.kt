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
    /** Embedded through the `university_id` foreign key. */
    val universities: UniversityNameDto? = null,
) {
    companion object {
        const val COLUMNS =
            "id,email,full_name,username,university_id,department,account_status,universities(name)"
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

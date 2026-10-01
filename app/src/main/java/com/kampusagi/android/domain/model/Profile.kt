package com.kampusagi.android.domain.model

/** Mirrors `public.account_status` in the database. */
enum class AccountStatus {
    PROFILE_INCOMPLETE,
    DOCUMENT_REQUIRED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    SUSPENDED,
}

data class Profile(
    val id: String,
    val email: String,
    val fullName: String?,
    val username: String?,
    val universityId: String?,
    val universityName: String?,
    val department: String?,
    val status: AccountStatus,
    val bio: String? = null,
    /** Path in the private `avatars` bucket; null shows the initials avatar. */
    val avatarPath: String? = null,
    /** Whether other students see the surname; off by default ("Ayşe Y."). */
    val showFullName: Boolean = false,
)

data class ProfileStats(
    val postCount: Int,
    val activeRequirementCount: Int,
    val conversationCount: Int,
)

data class ProfileDraft(
    val fullName: String,
    val username: String,
    val universityId: String?,
    val department: String,
)

data class University(
    val id: String,
    val name: String,
    val city: String,
)

/** The signed-in person's profile as last read from the backend. */
sealed interface ProfileState {
    data object Loading : ProfileState
    data class Loaded(val profile: Profile) : ProfileState
    data class Failed(val error: AppError) : ProfileState
}

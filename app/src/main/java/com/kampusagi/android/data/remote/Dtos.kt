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

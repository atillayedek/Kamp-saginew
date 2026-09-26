package com.kampusagi.android.domain.model

/** Mirrors `public.verification_status`. */
enum class VerificationStatus { PENDING, APPROVED, REJECTED }

data class Verification(
    val id: String,
    val status: VerificationStatus,
    val rejectionReason: String?,
    val createdAt: String,
)

/** A request waiting for an admin, as returned by `list_pending_verifications()`. */
data class PendingVerification(
    val verificationId: String,
    val userId: String,
    val email: String,
    val fullName: String?,
    val username: String?,
    val universityName: String?,
    val department: String?,
    val documentPath: String,
    val submittedAt: String,
)

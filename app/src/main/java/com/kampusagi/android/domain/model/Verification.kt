package com.kampusagi.android.domain.model

/** Mirrors `public.verification_status`. */
enum class VerificationStatus { PENDING, APPROVED, REJECTED }

data class Verification(
    val id: String,
    val status: VerificationStatus,
    val rejectionReason: String?,
    val createdAt: String,
)

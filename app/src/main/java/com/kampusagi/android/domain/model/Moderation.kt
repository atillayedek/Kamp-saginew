package com.kampusagi.android.domain.model

/** Mirrors `public.report_target`. */
enum class ReportTarget { POST, COMMENT, USER, MESSAGE, GROUP_MESSAGE, GROUP, NOTE }

/**
 * Mirrors `public.report_reason`, in the order the report dialog lists them. INAPPROPRIATE is kept
 * for reports made with older app versions; new reports use the specific categories.
 */
enum class ReportReason {
    HARASSMENT,
    HATE_SPEECH,
    PERSONAL_DATA_LEAK,
    PERSONALITY_RIGHTS,
    SEXUAL_CONTENT,
    COPYRIGHT,
    FAKE_PROFILE,
    SPAM,
    INAPPROPRIATE,
    OTHER,
}

data class BlockedUser(
    val userId: String,
    val fullName: String?,
    val username: String?,
    val blockedAt: String,
)

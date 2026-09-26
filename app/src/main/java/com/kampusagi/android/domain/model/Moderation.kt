package com.kampusagi.android.domain.model

/** Mirrors `public.report_target`. */
enum class ReportTarget { POST, COMMENT, USER, MESSAGE }

/** Mirrors `public.report_reason`. */
enum class ReportReason { SPAM, HARASSMENT, INAPPROPRIATE, FAKE_PROFILE, OTHER }

/** What an admin does with an open report (validated again by `resolve_report`). */
enum class ReportAction { DISMISS, REMOVE_CONTENT, SUSPEND_USER }

data class BlockedUser(
    val userId: String,
    val fullName: String?,
    val username: String?,
    val blockedAt: String,
)

data class OpenReport(
    val id: String,
    val target: ReportTarget,
    /** Snapshot of the reported text taken when the report was made. */
    val excerpt: String,
    val reason: ReportReason,
    val details: String?,
    val createdAt: String,
    /** Open reports about the same content, this one included. */
    val reportCount: Int,
    val reporterUsername: String?,
    val targetUserId: String,
    val targetFullName: String?,
    val targetUsername: String?,
    val targetSuspended: Boolean,
)

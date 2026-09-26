package com.kampusagi.android.domain.model

/** Mirrors `public.requirement_category`. */
enum class RequirementCategory { SPORTS, STUDY, PROJECT, TRANSPORT, ITEM, EVENT, HOUSING, OTHER }

/** Mirrors `public.requirement_status`. */
enum class RequirementStatus { ACTIVE, CLOSED }

/** What the AI suggested, as the student edits it before publishing. */
data class RequirementDraft(
    val title: String,
    val description: String,
    val category: RequirementCategory,
    val tags: List<String>,
    val locationText: String?,
    /** ISO-8601 instant, or null when no time was mentioned. */
    val startsAt: String?,
    val participantsNeeded: Int?,
)

data class Requirement(
    val id: String,
    val title: String,
    val description: String,
    val category: RequirementCategory,
    val tags: List<String>,
    val locationText: String?,
    val startsAt: String?,
    val participantsNeeded: Int?,
    val status: RequirementStatus,
    val createdAt: String,
)

data class MatchOwner(
    val id: String,
    val fullName: String?,
    val username: String?,
    val department: String?,
)

/** Another student's requirement similar to one of ours; [score] is 0-100 from pgvector cosine similarity. */
data class Match(
    val requirementId: String,
    val title: String,
    val description: String,
    val category: RequirementCategory,
    val tags: List<String>,
    val locationText: String?,
    val startsAt: String?,
    val participantsNeeded: Int?,
    val createdAt: String,
    val score: Int,
    val owner: MatchOwner,
)

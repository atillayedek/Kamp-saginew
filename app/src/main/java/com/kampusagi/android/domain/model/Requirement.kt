package com.kampusagi.android.domain.model

/** Mirrors `public.requirement_category`. */
enum class RequirementCategory { SPORTS, STUDY, PROJECT, TRANSPORT, ITEM, EVENT, HOUSING, OTHER }

/** Mirrors `public.requirement_status`. */
enum class RequirementStatus { ACTIVE, CLOSED }

/** The requirement form as the student fills it in. */
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

/**
 * Another student's requirement similar to one of ours. [score] (0-100) comes from `find_matches`:
 * shared tags, shared words, time and place; [sharedTags] are the tags both have.
 */
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
    val sharedTags: List<String>,
    val owner: MatchOwner,
)

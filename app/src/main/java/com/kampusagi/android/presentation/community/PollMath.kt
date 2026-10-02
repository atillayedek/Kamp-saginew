package com.kampusagi.android.presentation.community

import com.kampusagi.android.domain.model.Poll
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Pure helpers for showing poll results. */
object PollMath {

    /** Whole percentages per option that add up to 100 (largest remainder); all zero without votes. */
    fun percentages(votes: List<Int>): List<Int> {
        val total = votes.sum()
        if (total <= 0) return votes.map { 0 }
        val exact = votes.map { it * 100.0 / total }
        val floors = exact.map { it.toInt() }.toMutableList()
        var missing = 100 - floors.sum()
        exact.indices.sortedByDescending { exact[it] - floors[it] }.forEach { index ->
            if (missing > 0) {
                floors[index] += 1
                missing -= 1
            }
        }
        return floors
    }

    /** Closed once its closing time has passed; an unreadable time counts as open (the server decides). */
    fun isClosed(poll: Poll, now: Instant): Boolean {
        val closesAt = poll.closesAt ?: return false
        return try {
            !OffsetDateTime.parse(closesAt).toInstant().isAfter(now)
        } catch (e: DateTimeParseException) {
            false
        }
    }
}

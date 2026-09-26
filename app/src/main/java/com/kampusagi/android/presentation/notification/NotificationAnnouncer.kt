package com.kampusagi.android.presentation.notification

import com.kampusagi.android.domain.model.AppNotification
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Decides which notifications deserve a system notification: unread rows newer
 * than [baseline] that were not announced before. A NEW_MESSAGE row is
 * refreshed (new created_at) for every further message, so it is announced
 * again. Times are server times, compared as instants.
 */
class NotificationAnnouncer(private val baseline: Instant) {

    private val announced = mutableMapOf<String, Instant>()

    fun select(items: List<AppNotification>): List<AppNotification> =
        items.filter { item ->
            val at = instantOf(item.createdAt) ?: return@filter false
            !item.read && at.isAfter(baseline) && announced[item.id]?.let { at.isAfter(it) } != false
        }.onEach { announced[it.id] = instantOf(it.createdAt) ?: baseline }

    companion object {
        /** The newest notification already on the server; nothing up to it is announced. */
        fun baselineOf(items: List<AppNotification>, fallback: Instant): Instant =
            items.mapNotNull { instantOf(it.createdAt) }.maxOrNull() ?: fallback

        private fun instantOf(timestamp: String): Instant? =
            try {
                OffsetDateTime.parse(timestamp).toInstant()
            } catch (e: DateTimeParseException) {
                null
            }
    }
}

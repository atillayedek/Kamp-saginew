package com.kampusagi.android.presentation.notification

import com.kampusagi.android.domain.model.AppNotification
import com.kampusagi.android.domain.model.NotificationKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAnnouncerTest {

    private fun notification(id: String, createdAt: String, read: Boolean = false) = AppNotification(
        id = id,
        kind = NotificationKind.NEW_MESSAGE,
        createdAt = createdAt,
        read = read,
        actorName = "Ayşe",
        conversationId = "c1",
        postId = null,
    )

    @Test
    fun `nothing up to the baseline is announced`() {
        val existing = listOf(notification("1", "2026-09-26T12:00:00+00:00"), notification("2", "2026-09-26T12:05:00.5+00:00"))
        val announcer = NotificationAnnouncer(NotificationAnnouncer.baselineOf(existing, Instant.EPOCH))
        assertEquals(emptyList<AppNotification>(), announcer.select(existing))
    }

    @Test
    fun `new unread rows are announced once`() {
        val announcer = NotificationAnnouncer(Instant.parse("2026-09-26T12:00:00Z"))
        val fresh = notification("3", "2026-09-26T15:00:01.25+03:00")
        assertEquals(listOf("3"), announcer.select(listOf(fresh)).map { it.id })
        assertEquals(emptyList<AppNotification>(), announcer.select(listOf(fresh)))
    }

    @Test
    fun `a refreshed message notification is announced again`() {
        val announcer = NotificationAnnouncer(Instant.parse("2026-09-26T12:00:00Z"))
        announcer.select(listOf(notification("4", "2026-09-26T12:01:00Z")))
        assertEquals(listOf("4"), announcer.select(listOf(notification("4", "2026-09-26T12:02:00Z"))).map { it.id })
    }

    @Test
    fun `read rows and unparseable times are skipped`() {
        val announcer = NotificationAnnouncer(Instant.parse("2026-09-26T12:00:00Z"))
        val items = listOf(notification("5", "2026-09-26T13:00:00Z", read = true), notification("6", "not a time"))
        assertEquals(emptyList<AppNotification>(), announcer.select(items))
    }

    @Test
    fun `baseline falls back when the server has no notifications`() {
        val fallback = Instant.parse("2026-09-26T10:00:00Z")
        assertEquals(fallback, NotificationAnnouncer.baselineOf(emptyList(), fallback))
    }
}

package com.kampusagi.android.presentation.group

import com.kampusagi.android.domain.model.Author
import com.kampusagi.android.domain.model.GroupMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupMergeTest {

    private fun message(id: String, at: String, body: String = id) = GroupMessage(
        id = id, sender = Author("u", "Ali", "ali", null), body = body, mediaPath = null,
        createdAt = at, likeCount = 0, likedByMe = false, isMine = false, poll = null,
    )

    @Test
    fun `fresh rows win and older pages are kept`() {
        val known = listOf(message("3", "2026-10-01T10:03:00+00:00", "eski"), message("1", "2026-10-01T10:01:00+00:00"))
        val fresh = listOf(message("4", "2026-10-01T10:04:00Z"), message("3", "2026-10-01T10:03:00Z", "yeni"))
        val merged = GroupChatViewModel.mergeNewestFirst(fresh, known)
        assertEquals(listOf("4", "3", "1"), merged.map { it.id })
        assertEquals("yeni", merged[1].body)
    }

    @Test
    fun `a message missing from the newest page was deleted`() {
        val known = listOf(message("3", "2026-10-01T10:03:00Z"), message("2", "2026-10-01T10:02:00Z"), message("1", "2026-10-01T10:01:00Z"))
        val fresh = listOf(message("3", "2026-10-01T10:03:00Z"), message("1", "2026-10-01T10:01:00Z"))
        assertEquals(listOf("3", "1"), GroupChatViewModel.mergeNewestFirst(fresh, known).map { it.id })
        assertEquals(emptyList<String>(), GroupChatViewModel.mergeNewestFirst(emptyList(), known).map { it.id })
    }
}

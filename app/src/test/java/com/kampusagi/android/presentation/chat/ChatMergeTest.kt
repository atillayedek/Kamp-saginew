package com.kampusagi.android.presentation.chat

import com.kampusagi.android.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMergeTest {
    private fun message(id: String, createdAt: String, read: Boolean = false) =
        ChatMessage(id = id, body = id, createdAt = createdAt, isMine = true, readByOther = read)

    @Test
    fun `orders by server time even when postgres trims fractional zeros`() {
        // As strings "…00+00:00" < "…00.5+00:00" < "…00.12+00:00" would be wrong for .12 vs .5.
        val merged = ChatViewModel.mergeNewestFirst(
            fresh = listOf(message("b", "2026-09-26T10:00:00.5+00:00"), message("a", "2026-09-26T10:00:00.12+00:00")),
            known = listOf(message("z", "2026-09-26T10:00:00+00:00")),
        )
        assertEquals(listOf("b", "a", "z"), merged.map { it.id })
    }

    @Test
    fun `fresh rows replace known ones so read receipts update`() {
        val merged = ChatViewModel.mergeNewestFirst(
            fresh = listOf(message("a", "2026-09-26T10:00:00+00:00", read = true)),
            known = listOf(message("a", "2026-09-26T10:00:00+00:00", read = false), message("old", "2026-09-25T10:00:00+00:00")),
        )
        assertEquals(listOf("a" to true, "old" to false), merged.map { it.id to it.readByOther })
    }

    @Test
    fun `offsets are compared as instants`() {
        val merged = ChatViewModel.mergeNewestFirst(
            fresh = listOf(message("istanbul", "2026-09-26T12:30:00+03:00"), message("utc", "2026-09-26T10:00:00+00:00")),
            known = emptyList(),
        )
        assertEquals(listOf("utc", "istanbul"), merged.map { it.id })
    }
}

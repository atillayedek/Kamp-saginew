package com.kampusagi.android.presentation.community

import com.kampusagi.android.domain.model.Poll
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollMathTest {

    @Test
    fun `percentages always add up to one hundred`() {
        assertEquals(listOf(34, 33, 33), PollMath.percentages(listOf(1, 1, 1)))
        assertEquals(listOf(67, 33), PollMath.percentages(listOf(2, 1)))
        assertEquals(listOf(100, 0), PollMath.percentages(listOf(5, 0)))
        assertEquals(listOf(0, 0, 0), PollMath.percentages(listOf(0, 0, 0)))
        listOf(listOf(3, 3, 1), listOf(7, 2, 2, 2), listOf(1, 2, 3, 4)).forEach {
            assertEquals(100, PollMath.percentages(it).sum())
        }
    }

    @Test
    fun `a poll is closed once its closing time has passed`() {
        val now = Instant.parse("2026-10-01T12:00:00Z")
        fun poll(closesAt: String?) = Poll("p", emptyList(), 0, null, closesAt)
        assertFalse(PollMath.isClosed(poll(null), now))
        assertFalse(PollMath.isClosed(poll("2026-10-01T15:00:01+03:00"), now))
        assertTrue(PollMath.isClosed(poll("2026-10-01T15:00:00+03:00"), now))
        assertTrue(PollMath.isClosed(poll("2026-09-30T12:00:00+00:00"), now))
        assertFalse(PollMath.isClosed(poll("yarın"), now))
    }
}

package com.kampusagi.android.presentation.common

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = Instant.parse("2026-09-26T12:00:00Z")
    private val istanbul = ZoneId.of("Europe/Istanbul")

    private fun of(timestamp: String) = RelativeTime.of(timestamp, now, istanbul)

    @Test
    fun `buckets elapsed time`() {
        assertEquals(RelativeTime.JustNow, of("2026-09-26T11:59:30+00:00"))
        assertEquals(RelativeTime.Minutes(5), of("2026-09-26T11:55:00+00:00"))
        assertEquals(RelativeTime.Hours(3), of("2026-09-26T08:59:00+00:00"))
        assertEquals(RelativeTime.Days(2), of("2026-09-24T11:00:00+00:00"))
    }

    @Test
    fun `parses postgrest timestamps with fractions and offsets`() {
        // 11:57:59.5Z -> 2 min 0.5 s before now; the offset and the fraction must both be honoured.
        assertEquals(RelativeTime.Minutes(2), of("2026-09-26T14:57:59.500000+03:00"))
        // 1 min 59.88 s is still "1 minute": elapsed time is truncated, not rounded.
        assertEquals(RelativeTime.Minutes(1), of("2026-09-26T14:58:00.123456+03:00"))
    }

    @Test
    fun `future clocks read as just now`() {
        assertEquals(RelativeTime.JustNow, of("2026-09-26T12:05:00+00:00"))
    }

    @Test
    fun `older than a week shows the local date`() {
        assertEquals(RelativeTime.Date("1 Eyl 2026"), of("2026-08-31T22:30:00+00:00"))
    }

    @Test
    fun `garbage is unknown`() {
        assertEquals(RelativeTime.Unknown, of("dün"))
    }
}

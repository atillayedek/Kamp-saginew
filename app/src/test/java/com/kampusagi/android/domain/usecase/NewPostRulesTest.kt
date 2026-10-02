package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.NewPost
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.model.PostScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPostRulesTest {

    private fun post(category: PostCategory = PostCategory.GENERAL) = NewPost(PostScope.GENERAL, category, "Merhaba")

    @Test
    fun `lira amounts parse the way people type them`() {
        assertEquals(25_000L, PriceFormat.parseLira("250"))
        assertEquals(9_990L, PriceFormat.parseLira("99,90"))
        assertEquals(9_950L, PriceFormat.parseLira("99,5"))
        assertEquals(125_000L, PriceFormat.parseLira("1.250"))
        assertEquals(125_050L, PriceFormat.parseLira("1.250,50 ₺"))
        assertEquals(0L, PriceFormat.parseLira("0"))
        assertEquals(1_000_000_000L, PriceFormat.parseLira("10.000.000"))
    }

    @Test
    fun `invalid amounts are rejected`() {
        assertNull(PriceFormat.parseLira(""))
        assertNull(PriceFormat.parseLira("-5"))
        assertNull(PriceFormat.parseLira("12,345"))
        assertNull(PriceFormat.parseLira("1.25"))
        assertNull(PriceFormat.parseLira("abc"))
        assertNull(PriceFormat.parseLira("10.000.001"))
        assertNull(PriceFormat.parseLira("99999999999999999999"))
    }

    @Test
    fun `prices are shown with Turkish grouping`() {
        assertEquals("250 ₺", PriceFormat.format(25_000))
        assertEquals("1.250 ₺", PriceFormat.format(125_000))
        assertEquals("99,90 ₺", PriceFormat.format(9_990))
        assertEquals("1.000.000,05 ₺", PriceFormat.format(100_000_005))
    }

    @Test
    fun `poll options must be two to four distinct labels`() {
        assertTrue(NewPostValidator.isValidPollOptions(listOf("Evet", "Hayır")))
        assertFalse(NewPostValidator.isValidPollOptions(listOf("Evet")))
        assertFalse(NewPostValidator.isValidPollOptions(listOf("a", "b", "c", "d", "e")))
        assertFalse(NewPostValidator.isValidPollOptions(listOf("İstanbul", " istanbul ")))
        assertFalse(NewPostValidator.isValidPollOptions(listOf("Evet", "  ")))
        assertFalse(NewPostValidator.isValidPollOptions(listOf("Evet", "x".repeat(81))))
    }

    @Test
    fun `extras must match the category`() {
        assertTrue(NewPostValidator.isValid(post(PostCategory.EVENT).copy(eventStartsAt = "2026-10-01T18:00:00+03:00", eventLocation = "Kütüphane")))
        assertFalse(NewPostValidator.isValid(post().copy(eventStartsAt = "2026-10-01T18:00:00+03:00")))
        assertTrue(NewPostValidator.isValid(post(PostCategory.MARKETPLACE).copy(priceKurus = 0)))
        assertFalse(NewPostValidator.isValid(post().copy(priceKurus = 100)))
        assertFalse(NewPostValidator.isValid(post(PostCategory.MARKETPLACE).copy(priceKurus = -1)))
        assertFalse(NewPostValidator.isValid(post().copy(pollClosesAt = "2026-10-01T18:00:00+03:00")))
        assertFalse(NewPostValidator.isValid(post().copy(photos = List(5) { ByteArray(1) })))
        assertTrue(NewPostValidator.isValid(post().copy(photos = List(4) { ByteArray(1) }, pollOptions = listOf("A", "B"))))
    }
}

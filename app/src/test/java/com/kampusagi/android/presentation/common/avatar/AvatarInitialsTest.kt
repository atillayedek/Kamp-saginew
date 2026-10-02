package com.kampusagi.android.presentation.common.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarInitialsTest {

    @Test
    fun `first and last name give two letters`() {
        assertEquals("AT", AvatarInitials.of("atilla taşdemir", "xeozrs"))
        assertEquals("ZD", AvatarInitials.of("  Zeynep  Nur   Demir ", null))
    }

    @Test
    fun `turkish letters are uppercased the turkish way`() {
        assertEquals("İÖ", AvatarInitials.of("ilker ömer", null))
        assertEquals("Ç", AvatarInitials.of("çağla", null))
    }

    @Test
    fun `falls back to the username, then to a question mark`() {
        assertEquals("X", AvatarInitials.of(null, "xeozrs"))
        assertEquals("X", AvatarInitials.of("   ", "xeozrs"))
        assertEquals("?", AvatarInitials.of(null, null))
    }

    @Test
    fun `colour index is stable and in range`() {
        val id = "515f57f1-3c53-46b6-ba32-c61da410de3a"
        assertEquals(AvatarInitials.colorIndex(id, 8), AvatarInitials.colorIndex(id, 8))
        repeat(50) { assertTrue(AvatarInitials.colorIndex("user-$it", 8) in 0 until 8) }
    }
}

package com.kampusagi.android.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRulesTest {

    @Test
    fun `course codes are normalized like the database does`() {
        assertEquals("BLM101", GroupRules.normalizeCourseCode("blm 101"))
        assertEquals("BLM101", GroupRules.normalizeCourseCode("Blm-101"))
        assertEquals("FIZ101", GroupRules.normalizeCourseCode("FİZ 101"))
        assertEquals("ISL201", GroupRules.normalizeCourseCode("işl201"))
        assertNull(GroupRules.normalizeCourseCode("B"))
        assertNull(GroupRules.normalizeCourseCode("  -  "))
        assertNull(GroupRules.normalizeCourseCode("ÇOKUZUNDERSKODU123"))
    }

    @Test
    fun `names need two to sixty characters`() {
        assertTrue(GroupRules.isValidName("Fizik"))
        assertFalse(GroupRules.isValidName(" x "))
        assertFalse(GroupRules.isValidName("a".repeat(61)))
        assertTrue(GroupRules.isValidNoteText("Vize özeti", GroupRules.MAX_NOTE_TITLE))
        assertFalse(GroupRules.isValidNoteText("V", GroupRules.MAX_NOTE_TITLE))
    }
}

package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.usecase.PersonalDataDetector.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalDataDetectorTest {

    @Test
    fun `identity numbers are recognised by their checksum`() {
        assertEquals(true, PersonalDataDetector.isTcIdentityNumber("10000000146"))
        assertEquals(false, PersonalDataDetector.isTcIdentityNumber("10000000147"))
        assertEquals(false, PersonalDataDetector.isTcIdentityNumber("01234567890"))
        assertEquals(setOf(Kind.TC_IDENTITY_NUMBER), PersonalDataDetector.find("TC: 10000000146 bende"))
        assertEquals(emptySet<Kind>(), PersonalDataDetector.find("Sipariş no 12345678901"))
    }

    @Test
    fun `turkish ibans are recognised with or without spaces`() {
        assertEquals(true, PersonalDataDetector.isValidTrIban("TR33 0006 1005 1978 6457 8413 26"))
        assertEquals(false, PersonalDataDetector.isValidTrIban("TR33 0006 1005 1978 6457 8413 27"))
        assertEquals(setOf(Kind.IBAN), PersonalDataDetector.find("IBAN: tr330006100519786457841326 lütfen"))
    }

    @Test
    fun `mobile numbers in common spellings`() {
        for (text in listOf("0555 555 55 55", "+90 532 123 45 67", "5321234567", "(532) 123-45-67")) {
            assertEquals(text, setOf(Kind.PHONE_NUMBER), PersonalDataDetector.find("Ara: $text"))
        }
        assertEquals(emptySet<Kind>(), PersonalDataDetector.find("Saat 14:30'da 3 kişi, 2026 yılı"))
    }

    @Test
    fun `fold and whole-word matching follow the database`() {
        assertEquals("ogrenci isik cay", TurkishFold.fold("ÖĞRENCİ Işık Çay"))
        assertEquals(listOf("alevi", "dem parti"), TurkishFold.matches("Alevi ev arkadaşı, DEM Parti'li olmasın", listOf("alevi", "dem parti", "din")))
        assertEquals(emptyList<String>(), TurkishFold.matches("Dinlenme odası", listOf("din")))
    }
}

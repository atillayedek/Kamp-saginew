package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.ProfileDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileInputValidatorTest {

    private val valid = ProfileDraft(
        fullName = "Ayşe Yılmaz",
        username = "ayse_y",
        universityId = "00000000-0000-0000-0000-00000000000a",
        department = "Bilgisayar Mühendisliği",
    )

    @Test
    fun `a complete draft is valid`() {
        assertEquals(emptySet<ProfileInputError>(), ProfileInputValidator.validate(valid))
    }

    @Test
    fun `normalize trims, collapses spaces and lowercases the username`() {
        val normalized = ProfileInputValidator.normalize(
            valid.copy(fullName = "  Ayşe   Yılmaz ", username = " Ayse_Y ", department = " Hukuk "),
        )
        assertEquals("Ayşe Yılmaz", normalized.fullName)
        assertEquals("ayse_y", normalized.username)
        assertEquals("Hukuk", normalized.department)
    }

    @Test
    fun `mixed case username is accepted after normalisation`() {
        assertEquals(emptySet<ProfileInputError>(), ProfileInputValidator.validate(valid.copy(username = "Ayse.Y")))
    }

    @Test
    fun `username rules match the database check`() {
        listOf("ab", "a".repeat(31), "ayşe", "ali veli", "ali-veli").forEach { username ->
            assertEquals(username, setOf(ProfileInputError.USERNAME_INVALID), ProfileInputValidator.validate(valid.copy(username = username)))
        }
    }

    @Test
    fun `every missing field is reported`() {
        assertEquals(
            ProfileInputError.entries.toSet(),
            ProfileInputValidator.validate(ProfileDraft(fullName = " ", username = "", universityId = null, department = "x")),
        )
    }
}

package com.kampusagi.android.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputValidatorTest {

    @Test
    fun `accepts ordinary and university email addresses`() {
        assertTrue(AuthInputValidator.isValidEmail("ayse.yilmaz@ogr.example.edu.tr"))
        assertTrue(AuthInputValidator.isValidEmail("  ali+kampus@example.com  "))
    }

    @Test
    fun `rejects malformed email addresses`() {
        assertFalse(AuthInputValidator.isValidEmail(""))
        assertFalse(AuthInputValidator.isValidEmail("ayse"))
        assertFalse(AuthInputValidator.isValidEmail("ayse@"))
        assertFalse(AuthInputValidator.isValidEmail("ayse@edu"))
        assertFalse(AuthInputValidator.isValidEmail("ay se@example.com"))
    }

    @Test
    fun `sign in only requires a password to be present`() {
        assertEquals(emptySet<AuthInputError>(), AuthInputValidator.validateSignIn("a@example.com", "x"))
        assertEquals(setOf(AuthInputError.PASSWORD_REQUIRED), AuthInputValidator.validateSignIn("a@example.com", ""))
    }

    @Test
    fun `sign up reports every problem at once`() {
        assertEquals(
            setOf(AuthInputError.EMAIL_INVALID, AuthInputError.PASSWORD_TOO_SHORT, AuthInputError.PASSWORDS_DO_NOT_MATCH),
            AuthInputValidator.validateSignUp("nope", "short", "other"),
        )
    }

    @Test
    fun `sign up password length boundary`() {
        val seven = "a".repeat(AuthInputValidator.MIN_PASSWORD_LENGTH - 1)
        val eight = "a".repeat(AuthInputValidator.MIN_PASSWORD_LENGTH)
        assertEquals(setOf(AuthInputError.PASSWORD_TOO_SHORT), AuthInputValidator.validateSignUp("a@example.com", seven, seven))
        assertEquals(emptySet<AuthInputError>(), AuthInputValidator.validateSignUp("a@example.com", eight, eight))
    }
}

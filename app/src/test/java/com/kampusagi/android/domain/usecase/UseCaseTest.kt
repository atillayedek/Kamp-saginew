package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthRedirectResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.ProfileDraft
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Test doubles live only in src/test. They record calls and return scripted results. */
private class RecordingAuthRepository(
    private val signInResult: AppResult<Unit> = AppResult.Success(Unit),
    private val signUpResult: AppResult<SignUpResult> = AppResult.Success(SignUpResult.VERIFICATION_REQUIRED),
) : AuthRepository {
    var signInCalls = 0
    var signUpCalls = 0
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override val isBackendConfigured: Boolean = true
    override suspend fun signIn(email: String, password: String): AppResult<Unit> = signInResult.also { signInCalls++ }
    override suspend fun signUp(email: String, password: String): AppResult<SignUpResult> =
        signUpResult.also { signUpCalls++ }
    override suspend fun resendVerificationEmail(email: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun updatePassword(newPassword: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun handleAuthRedirect(url: String): AuthRedirectResult = AuthRedirectResult.NotAnAuthLink
    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)
}

private class RecordingProfileRepository(private val result: AppResult<Unit>) : ProfileRepository {
    var lastDraft: ProfileDraft? = null
    override val profileState: StateFlow<ProfileState?> = MutableStateFlow(null)
    override suspend fun refresh() = Unit
    override suspend fun completeProfile(draft: ProfileDraft): AppResult<Unit> = result.also { lastDraft = draft }
}

class UseCaseTest {

    @Test
    fun `invalid sign in never reaches the backend`() = runTest {
        val repository = RecordingAuthRepository()
        val result = SignInUseCase(repository)("not-an-email", "")
        assertEquals(FormResult.Invalid(setOf(AuthInputError.EMAIL_INVALID, AuthInputError.PASSWORD_REQUIRED)), result)
        assertEquals(0, repository.signInCalls)
    }

    @Test
    fun `backend errors are passed through unchanged`() = runTest {
        val repository = RecordingAuthRepository(signInResult = AppResult.Failure(AppError.INVALID_CREDENTIALS))
        assertEquals(FormResult.Failed(AppError.INVALID_CREDENTIALS), SignInUseCase(repository)("a@example.com", "secret"))
        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `sign up returns the backend outcome`() = runTest {
        val repository = RecordingAuthRepository()
        assertEquals(
            FormResult.Success(SignUpResult.VERIFICATION_REQUIRED),
            SignUpUseCase(repository)("a@example.com", "password1", "password1"),
        )
    }

    @Test
    fun `complete profile sends the normalised draft`() = runTest {
        val repository = RecordingProfileRepository(AppResult.Success(Unit))
        val result = CompleteProfileUseCase(repository)(
            ProfileDraft(fullName = " Ali  Veli ", username = "AliVeli", universityId = "u1", department = " Hukuk "),
        )
        assertEquals(FormResult.Success(Unit), result)
        assertEquals(ProfileDraft("Ali Veli", "aliveli", "u1", "Hukuk"), repository.lastDraft)
    }

    @Test
    fun `invalid profile is not sent`() = runTest {
        val repository = RecordingProfileRepository(AppResult.Success(Unit))
        val result = CompleteProfileUseCase(repository)(ProfileDraft("Ali Veli", "aliveli", null, "Hukuk"))
        assertEquals(FormResult.Invalid(setOf(ProfileInputError.UNIVERSITY_REQUIRED)), result)
        assertNull(repository.lastDraft)
    }

    @Test
    fun `username conflict from the database surfaces as a failure`() = runTest {
        val repository = RecordingProfileRepository(AppResult.Failure(AppError.USERNAME_TAKEN))
        val result = CompleteProfileUseCase(repository)(ProfileDraft("Ali Veli", "aliveli", "u1", "Hukuk"))
        assertEquals(FormResult.Failed(AppError.USERNAME_TAKEN), result)
    }
}

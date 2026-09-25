package com.kampusagi.android.domain.repository

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthRedirectResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.ProfileDraft
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.model.University
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val isBackendConfigured: Boolean

    suspend fun signIn(email: String, password: String): AppResult<Unit>
    suspend fun signUp(email: String, password: String): AppResult<SignUpResult>
    suspend fun resendVerificationEmail(email: String): AppResult<Unit>
    suspend fun sendPasswordReset(email: String): AppResult<Unit>
    suspend fun updatePassword(newPassword: String): AppResult<Unit>
    suspend fun handleAuthRedirect(url: String): AuthRedirectResult
    suspend fun signOut(): AppResult<Unit>
}

interface ProfileRepository {
    /** Null while nobody is signed in. */
    val profileState: StateFlow<ProfileState?>

    /** Reloads the signed-in person's profile from the backend. */
    suspend fun refresh()

    suspend fun completeProfile(draft: ProfileDraft): AppResult<Unit>
}

interface UniversityRepository {
    suspend fun getActiveUniversities(): AppResult<List<University>>
}

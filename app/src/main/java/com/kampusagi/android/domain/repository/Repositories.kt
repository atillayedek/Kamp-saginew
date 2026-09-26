package com.kampusagi.android.domain.repository

import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthRedirectResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.PendingVerification
import com.kampusagi.android.domain.model.ProfileDraft
import com.kampusagi.android.domain.model.ProfileState
import com.kampusagi.android.domain.model.SignUpResult
import com.kampusagi.android.domain.model.University
import com.kampusagi.android.domain.model.Verification
import java.io.File
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

interface VerificationRepository {
    /** Uploads the PDF and asks the backend to validate and record it. */
    suspend fun submitDocument(bytes: ByteArray): AppResult<Unit>

    /** The signed-in person's most recent request, or null if they never submitted one. */
    suspend fun latestVerification(): AppResult<Verification?>
}

/** Reads a document the person picked with the system file picker. */
interface DocumentReader {
    suspend fun read(uri: String, maxBytes: Int): AppResult<ByteArray>
}

interface AdminRepository {
    suspend fun pendingVerifications(): AppResult<List<PendingVerification>>

    /** Downloads the document into the app's private cache for viewing. */
    suspend fun downloadDocument(path: String): AppResult<File>

    suspend fun review(verificationId: String, approve: Boolean, reason: String?): AppResult<Unit>
}

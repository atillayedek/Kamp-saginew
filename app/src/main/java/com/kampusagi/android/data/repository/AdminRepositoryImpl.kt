package com.kampusagi.android.data.repository

import android.content.Context
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.PendingVerificationDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.PendingVerification
import com.kampusagi.android.domain.repository.AdminRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    @ApplicationContext private val context: Context,
) : AdminRepository {

    override suspend fun pendingVerifications(): AppResult<List<PendingVerification>> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall {
            client.postgrest.rpc("list_pending_verifications", JsonObject(emptyMap()))
                .decodeList<PendingVerificationDto>()
                .map {
                    PendingVerification(
                        verificationId = it.verificationId,
                        userId = it.userId,
                        email = it.email,
                        fullName = it.fullName,
                        username = it.username,
                        universityName = it.universityName,
                        department = it.department,
                        documentPath = it.documentPath,
                        submittedAt = it.submittedAt,
                    )
                }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    /**
     * Kept in the app's private cache under one fixed name, so only the
     * document currently being reviewed is ever stored on the device.
     */
    override suspend fun downloadDocument(path: String): AppResult<File> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        val bytes = safeCall {
            client.storage.from(AppConfig.STUDENT_DOCUMENTS_BUCKET).downloadAuthenticated(path)
        }.getOrElse { return AppResult.Failure(it.toAppError()) }
        return withContext(Dispatchers.IO) {
            safeCall {
                val directory = File(context.cacheDir, REVIEW_DIRECTORY).apply { mkdirs() }
                File(directory, REVIEW_FILE).apply { writeBytes(bytes) }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppError.DOCUMENT_UNREADABLE) },
            )
        }
    }

    override suspend fun review(verificationId: String, approve: Boolean, reason: String?): AppResult<Unit> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall {
            client.postgrest.rpc(
                "review_student_verification",
                buildJsonObject {
                    put("p_verification_id", verificationId)
                    put("p_approve", approve)
                    put("p_reason", reason)
                },
            )
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    companion object {
        /** Must match res/xml/file_paths.xml. */
        const val REVIEW_DIRECTORY = "review"
        private const val REVIEW_FILE = "document.pdf"
    }
}

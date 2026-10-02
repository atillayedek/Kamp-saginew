package com.kampusagi.android.data.repository

import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.VerificationDto
import com.kampusagi.android.data.remote.functionError
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.Verification
import com.kampusagi.android.domain.model.VerificationStatus
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import com.kampusagi.android.domain.repository.VerificationRepository
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class VerificationRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : VerificationRepository {

    override suspend fun submitDocument(bytes: ByteArray): AppResult<Unit> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        val userId = (authRepository.authState.value as? AuthState.SignedIn)?.userId
            ?: return AppResult.Failure(AppError.SESSION_EXPIRED)
        // A fresh name per attempt: uploads are never overwritten (they are review evidence).
        val path = "$userId/${UUID.randomUUID()}.pdf"

        safeCall {
            client.storage.from(AppConfig.STUDENT_DOCUMENTS_BUCKET).upload(path, bytes) {
                upsert = false
                contentType = ContentType.Application.Pdf
            }
        }.onFailure { return AppResult.Failure(it.toAppError()) }

        val response = safeCall {
            client.functions.invoke(
                function = AppConfig.SUBMIT_STUDENT_DOCUMENT_FUNCTION,
                body = buildJsonObject { put("path", path) },
            )
        }.getOrElse { return AppResult.Failure(it.toAppError()) }

        if (!response.status.isSuccess()) {
            val body = safeCall { response.bodyAsText() }.getOrDefault("")
            return AppResult.Failure(functionError(response.status.value, body))
        }

        profileRepository.refresh()
        return AppResult.Success(Unit)
    }

    override suspend fun latestVerification(): AppResult<Verification?> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall {
            client.postgrest.from("student_verifications")
                .select(Columns.list("id", "status", "rejection_reason", "created_at")) {
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<VerificationDto>()
                .firstOrNull()
                ?.let { dto ->
                    Verification(
                        id = dto.id,
                        status = VerificationStatus.entries.firstOrNull { it.name == dto.status }
                            ?: throw UnknownStatusException(dto.status),
                        rejectionReason = dto.rejectionReason,
                        createdAt = dto.createdAt,
                    )
                }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }
}

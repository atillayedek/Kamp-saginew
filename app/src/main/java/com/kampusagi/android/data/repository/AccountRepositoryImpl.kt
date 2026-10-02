package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.DeleteAccountRequestDto
import com.kampusagi.android.data.remote.DeletionScheduleDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.functionError
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.AccountRepository
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : AccountRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun deleteAccount(): AppResult<String> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall {
            val response = client.functions.invoke(
                function = AppConfig.DELETE_ACCOUNT_FUNCTION,
                body = DeleteAccountRequestDto(AppConfig.DELETE_ACCOUNT_CONFIRMATION),
            )
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) throw DeletionFailure(functionError(response.status.value, body))
            json.decodeFromString(DeletionScheduleDto.serializer(), body).scheduledFor
                ?: throw DeletionFailure(AppError.SERVER)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                Log.w(TAG, "Account deletion request failed", it)
                AppResult.Failure(if (it is DeletionFailure) it.error else it.toAppError())
            },
        )
    }

    private class DeletionFailure(val error: AppError) : IllegalStateException("delete-account failed: $error")

    private companion object {
        const val TAG = "Account"
    }
}

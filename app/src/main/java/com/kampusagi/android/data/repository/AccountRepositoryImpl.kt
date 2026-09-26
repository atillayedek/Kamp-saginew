package com.kampusagi.android.data.repository

import android.util.Log
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.DeleteAccountRequestDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.functionError
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.AccountRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : AccountRepository {

    override suspend fun deleteAccount(): AppResult<Unit> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        val deleted = safeCall {
            val response = client.functions.invoke(
                function = AppConfig.DELETE_ACCOUNT_FUNCTION,
                body = DeleteAccountRequestDto(AppConfig.DELETE_ACCOUNT_CONFIRMATION),
            )
            if (!response.status.isSuccess()) {
                throw DeletionFailure(functionError(response.status.value, response.bodyAsText()))
            }
        }.exceptionOrNull()
        if (deleted != null) {
            return AppResult.Failure(if (deleted is DeletionFailure) deleted.error else deleted.toAppError())
        }
        // The account no longer exists on the server, so only the local session is left to clear.
        return safeCall { client.auth.clearSession() }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = {
                Log.e(TAG, "Account deleted but the local session could not be cleared", it)
                AppResult.Failure(it.toAppError())
            },
        )
    }

    private class DeletionFailure(val error: AppError) : IllegalStateException("delete-account failed: $error")

    private companion object {
        const val TAG = "Account"
    }
}

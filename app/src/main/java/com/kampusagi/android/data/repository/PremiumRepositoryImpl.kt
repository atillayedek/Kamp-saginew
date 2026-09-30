package com.kampusagi.android.data.repository

import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.PlanDto
import com.kampusagi.android.data.remote.SubscriptionDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.VerifyPurchaseRequestDto
import com.kampusagi.android.data.remote.functionError
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.Plan
import com.kampusagi.android.domain.model.StorePurchase
import com.kampusagi.android.domain.model.SubscriptionStatus
import com.kampusagi.android.domain.repository.PremiumRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class PremiumRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : PremiumRepository {

    override suspend fun plans(): AppResult<List<Plan>> = call { client ->
        client.postgrest.rpc("list_plans", JsonObject(emptyMap())).decodeList<PlanDto>().map {
            Plan(it.id, it.playProductId, it.name, it.description, it.aiAnalyzeDaily, it.aiPublishDaily, it.maxActiveRequirements)
        }
    }

    override suspend fun subscription(): AppResult<SubscriptionStatus> = call { client ->
        val row = client.postgrest.rpc("my_subscription", JsonObject(emptyMap())).decodeList<SubscriptionDto>().firstOrNull()
            ?: throw MissingRowException()
        SubscriptionStatus(row.planName, row.playProductId, row.expiresAt, row.aiAnalyzeDaily, row.aiPublishDaily, row.maxActiveRequirements, row.source)
    }

    override suspend fun verify(purchase: StorePurchase): AppResult<Unit> = call { client ->
        val response = client.functions.invoke(
            function = AppConfig.VERIFY_PURCHASE_FUNCTION,
            body = VerifyPurchaseRequestDto(purchase.productId, purchase.purchaseToken),
        )
        if (!response.status.isSuccess()) {
            throw VerificationFailure(functionError(response.status.value, response.bodyAsText()))
        }
        Unit
    }

    override suspend fun redeemPromoCode(code: String): AppResult<String> = call { client ->
        client.postgrest.rpc("redeem_promo_code", JsonObject(mapOf("p_code" to JsonPrimitive(code.trim())))).decodeAs<String>()
    }

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                AppResult.Failure(
                    when (it) {
                        is VerificationFailure -> it.error
                        // my_subscription returns no row for accounts that are not approved students.
                        is MissingRowException -> AppError.ACCOUNT_NOT_APPROVED
                        else -> it.toAppError()
                    },
                )
            },
        )
    }

    private class VerificationFailure(val error: AppError) : IllegalStateException("verify-purchase failed: $error")
    private class MissingRowException : IllegalStateException("my_subscription returned no row")
}

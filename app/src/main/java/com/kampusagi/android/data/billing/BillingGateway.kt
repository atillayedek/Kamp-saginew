package com.kampusagi.android.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.StoreOffer
import com.kampusagi.android.domain.model.StorePurchase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of a purchase flow reported by Google Play. */
sealed interface PurchaseUpdate {
    data class Purchased(val purchases: List<StorePurchase>) : PurchaseUpdate
    data object Pending : PurchaseUpdate
    data class Failed(val error: AppError) : PurchaseUpdate
}

/**
 * Google Play Billing. Purchases are never acknowledged here: the backend
 * acknowledges after it has verified the token (see verify-purchase).
 */
@Singleton
class BillingGateway @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val purchaseUpdates: SharedFlow<PurchaseUpdate> = updates.asSharedFlow()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val update = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val bought = purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (bought.isEmpty()) PurchaseUpdate.Pending else PurchaseUpdate.Purchased(bought.flatMap(::toStorePurchases))
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Failed(AppError.PURCHASE_CANCELLED)
            else -> PurchaseUpdate.Failed(AppError.BILLING_UNAVAILABLE)
        }
        if (!updates.tryEmit(update)) Log.w(TAG, "Purchase update dropped")
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val connection = Mutex()
    private val productCache = mutableMapOf<String, ProductDetails>()

    /** Prices for the given subscription products; products unknown to Play are left out. */
    suspend fun offers(productIds: List<String>): AppResult<List<StoreOffer>> {
        if (productIds.isEmpty()) return AppResult.Success(emptyList())
        if (!ensureConnected()) return AppResult.Failure(AppError.BILLING_UNAVAILABLE)
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
            return AppResult.Failure(AppError.BILLING_UNAVAILABLE)
        }
        val offers = result.productDetailsList.orEmpty().mapNotNull { details ->
            productCache[details.productId] = details
            val phase = details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()
                ?: return@mapNotNull null
            StoreOffer(details.productId, phase.formattedPrice, phase.billingPeriod)
        }
        return AppResult.Success(offers)
    }

    /** Starts Google Play's purchase screen; the result arrives on [purchaseUpdates]. */
    fun launchPurchase(activity: Activity, productId: String, accountId: String): AppResult<Unit> {
        val details = productCache[productId] ?: return AppResult.Failure(AppError.PLAN_NOT_AVAILABLE)
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return AppResult.Failure(AppError.PLAN_NOT_AVAILABLE)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            // Binds the purchase to this account; the backend checks it.
            .setObfuscatedAccountId(accountId)
            .build()
        val result = client.launchBillingFlow(activity, params)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            AppResult.Success(Unit)
        } else {
            Log.w(TAG, "launchBillingFlow failed: ${result.debugMessage}")
            AppResult.Failure(AppError.BILLING_UNAVAILABLE)
        }
    }

    /** Active subscriptions Google Play knows for this Play account (restore / renewals). */
    suspend fun ownedPurchases(): AppResult<List<StorePurchase>> {
        if (!ensureConnected()) return AppResult.Failure(AppError.BILLING_UNAVAILABLE)
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return AppResult.Failure(AppError.BILLING_UNAVAILABLE)
        }
        return AppResult.Success(
            result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.flatMap(::toStorePurchases),
        )
    }

    private fun toStorePurchases(purchase: Purchase): List<StorePurchase> =
        purchase.products.map { StorePurchase(it, purchase.purchaseToken) }

    private suspend fun ensureConnected(): Boolean = connection.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (!ok) Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    if (continuation.isActive) continuation.resume(ok)
                }

                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "Billing service disconnected; the next call reconnects")
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    private companion object {
        const val TAG = "Billing"
    }
}

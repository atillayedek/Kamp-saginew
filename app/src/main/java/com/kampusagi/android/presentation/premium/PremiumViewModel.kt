package com.kampusagi.android.presentation.premium

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.data.billing.BillingGateway
import com.kampusagi.android.data.billing.PurchaseUpdate
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.AuthState
import com.kampusagi.android.domain.model.LegalDocumentInfo
import com.kampusagi.android.domain.model.LegalKind
import com.kampusagi.android.domain.model.Plan
import com.kampusagi.android.domain.model.StoreOffer
import com.kampusagi.android.domain.model.StorePurchase
import com.kampusagi.android.domain.model.SubscriptionStatus
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ComplianceRepository
import com.kampusagi.android.domain.repository.PremiumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class PlanItem(val plan: Plan, val offer: StoreOffer?)

sealed interface PremiumState {
    data object Loading : PremiumState
    data class Loaded(
        val subscription: SubscriptionStatus,
        val plans: List<PlanItem>,
        /** False when Google Play could not be reached; prices and buying are unavailable. */
        val storeAvailable: Boolean,
    ) : PremiumState
    data class Failed(val error: AppError) : PremiumState
}

enum class PremiumMessage { PURCHASE_ACTIVATED, PURCHASE_PENDING, NOTHING_TO_RESTORE, PROMO_REDEEMED }

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val premiumRepository: PremiumRepository,
    private val billing: BillingGateway,
    private val authRepository: AuthRepository,
    private val compliance: ComplianceRepository,
) : ViewModel() {

    /** Pre-information form and distance contract (6502; Mesafeli Sözleşmeler Yönetmeliği). */
    var purchaseDocuments by mutableStateOf<List<LegalDocumentInfo>>(emptyList())
        private set

    /** "I read and accept the pre-information form and the distance contract"; never pre-ticked. */
    var contractAccepted by mutableStateOf(false)
        private set

    /** Express consent to immediate performance and loss of the right of withdrawal (md.15/1-ğ). */
    var withdrawalWaived by mutableStateOf(false)
        private set

    val canBuy: Boolean
        get() = purchaseDocuments.isNotEmpty() && contractAccepted && withdrawalWaived

    fun onContractAcceptedChange(value: Boolean) {
        contractAccepted = value
    }

    fun onWithdrawalWaivedChange(value: Boolean) {
        withdrawalWaived = value
    }


    var state by mutableStateOf<PremiumState>(PremiumState.Loading)
        private set

    var isWorking by mutableStateOf(false)
        private set

    var error by mutableStateOf<AppError?>(null)
        private set

    var message by mutableStateOf<PremiumMessage?>(null)
        private set

    init {
        load()
        viewModelScope.launch {
            billing.purchaseUpdates.collect { update ->
                when (update) {
                    is PurchaseUpdate.Purchased -> verifyAll(update.purchases, activatedMessage = true)
                    PurchaseUpdate.Pending -> message = PremiumMessage.PURCHASE_PENDING
                    // Cancelling is the person's choice, not an error to show.
                    is PurchaseUpdate.Failed -> if (update.error != AppError.PURCHASE_CANCELLED) error = update.error
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            when (val documents = compliance.legalDocuments()) {
                is AppResult.Success -> purchaseDocuments = documents.value.filter { it.kind == LegalKind.PURCHASE }
                is AppResult.Failure -> error = documents.error
            }
        }
        viewModelScope.launch {
            val subscription = premiumRepository.subscription()
            val plans = premiumRepository.plans()
            state = when {
                subscription is AppResult.Failure -> PremiumState.Failed(subscription.error)
                plans is AppResult.Failure -> PremiumState.Failed(plans.error)
                subscription is AppResult.Success && plans is AppResult.Success -> {
                    val offers: List<StoreOffer>? = when (val result = billing.offers(plans.value.map { it.productId })) {
                        is AppResult.Success -> result.value
                        is AppResult.Failure -> null
                    }
                    val byProduct = offers.orEmpty().associateBy { it.productId }
                    PremiumState.Loaded(
                        subscription = subscription.value,
                        plans = plans.value.map { PlanItem(it, byProduct[it.productId]) },
                        storeAvailable = offers != null,
                    )
                }
                else -> PremiumState.Loading
            }
        }
    }

    /** Records the acceptance of the purchase texts (version + SHA-256), then opens Google Play. */
    fun buy(activity: Activity, plan: Plan) {
        val userId = (authRepository.authState.value as? AuthState.SignedIn)?.userId ?: return
        if (!canBuy || isWorking) return
        error = null
        message = null
        isWorking = true
        viewModelScope.launch {
            for (document in purchaseDocuments) {
                val recorded = compliance.acknowledge(document, channel = "purchase")
                if (recorded is AppResult.Failure) {
                    error = recorded.error
                    isWorking = false
                    return@launch
                }
            }
            isWorking = false
            val result = billing.launchPurchase(activity, plan.productId, userId)
            if (result is AppResult.Failure) error = result.error
        }
    }

    /** Re-sends purchases Google Play knows about (new phone, renewal, interrupted verification). */
    fun restore() {
        if (isWorking) return
        error = null
        message = null
        viewModelScope.launch {
            when (val owned = billing.ownedPurchases()) {
                is AppResult.Failure -> error = owned.error
                is AppResult.Success -> if (owned.value.isEmpty()) {
                    message = PremiumMessage.NOTHING_TO_RESTORE
                } else {
                    verifyAll(owned.value, activatedMessage = false)
                }
            }
        }
    }

    fun dismissMessage() {
        message = null
    }

    /** Promo codes are case-insensitive; the server decides whether one is valid. */
    var promoCode by mutableStateOf("")
        private set

    fun onPromoCodeChange(value: String) {
        if (value.length <= 32) promoCode = value.uppercase()
    }

    fun redeemPromoCode() {
        val code = promoCode.trim()
        if (code.length < 4 || isWorking) return
        isWorking = true
        error = null
        message = null
        viewModelScope.launch {
            when (val result = premiumRepository.redeemPromoCode(code)) {
                is AppResult.Success -> {
                    promoCode = ""
                    message = PremiumMessage.PROMO_REDEEMED
                    load()
                }
                is AppResult.Failure -> error = result.error
            }
            isWorking = false
        }
    }

    private suspend fun verifyAll(purchases: List<StorePurchase>, activatedMessage: Boolean) {
        isWorking = true
        var failure: AppError? = null
        purchases.forEach { purchase ->
            val result = premiumRepository.verify(purchase)
            if (result is AppResult.Failure) failure = result.error
        }
        isWorking = false
        error = failure
        if (failure == null && activatedMessage) message = PremiumMessage.PURCHASE_ACTIVATED
        load()
    }
}

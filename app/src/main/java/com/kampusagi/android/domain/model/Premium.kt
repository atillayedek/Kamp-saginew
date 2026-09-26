package com.kampusagi.android.domain.model

/** A plan offered by the product owner (from `subscription_plans`); limits are enforced by the database. */
data class Plan(
    val id: String,
    val productId: String,
    val name: String,
    val description: String,
    val aiAnalyzeDaily: Int,
    val aiPublishDaily: Int,
    val maxActiveRequirements: Int,
)

/** The person's current limits and, if any, the plan that grants them. */
data class SubscriptionStatus(
    val planName: String?,
    val productId: String?,
    val expiresAt: String?,
    val aiAnalyzeDaily: Int,
    val aiPublishDaily: Int,
    val maxActiveRequirements: Int,
) {
    val isPremium: Boolean get() = planName != null
}

/** Price and offer from Google Play for one product. */
data class StoreOffer(val productId: String, val formattedPrice: String, val billingPeriod: String)

/** A purchase reported by Google Play that the backend still has to verify. */
data class StorePurchase(val productId: String, val purchaseToken: String)

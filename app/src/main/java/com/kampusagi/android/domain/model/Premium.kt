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
    /** PLAY for a Google Play subscription; ADMIN or PROMO for Premium given without payment. */
    val source: String? = null,
) {
    val isPremium: Boolean get() = planName != null
    val isGift: Boolean get() = source == "ADMIN" || source == "PROMO"
}

/** A message from the team shown on top of the feed until the person closes it. */
data class Announcement(val id: String, val title: String, val body: String, val createdAt: String, val endsAt: String)

/** Price and offer from Google Play for one product. */
data class StoreOffer(val productId: String, val formattedPrice: String, val billingPeriod: String)

/** A purchase reported by Google Play that the backend still has to verify. */
data class StorePurchase(val productId: String, val purchaseToken: String)

package com.smyddevstudio.kanako.data.billing

internal enum class PurchasePlan { MONTHLY, YEARLY, LIFETIME }

internal data class BillingCatalog(
    val subscriptionId: String,
    val monthlyBasePlanId: String,
    val yearlyBasePlanId: String,
    val lifetimeId: String,
    val lifetimePurchaseOptionId: String
) {
    init {
        require(listOf(subscriptionId, monthlyBasePlanId, yearlyBasePlanId,
            lifetimeId, lifetimePurchaseOptionId).all { it.isNotBlank() })
        require(subscriptionId != lifetimeId)
        require(monthlyBasePlanId != yearlyBasePlanId)
    }
}

internal data class StoreOffer(
    val plan: PurchasePlan,
    val formattedPrice: String
)

internal sealed interface PaidAccess {
    data object None : PaidAccess
    data class Subscription(val expiresAtMillis: Long) : PaidAccess
    data object Lifetime : PaidAccess
}

internal fun PaidAccess.isActive(nowMillis: Long): Boolean = when (this) {
    PaidAccess.None -> false
    PaidAccess.Lifetime -> true
    is PaidAccess.Subscription -> nowMillis >= 0 && nowMillis < expiresAtMillis
}

internal data class PurchaseEvidence(
    val token: String,
    val productIds: List<String>,
    val signedData: String,
    val signature: String
)

internal data class VerifiedPurchase(
    val productId: String,
    val access: PaidAccess
)

internal fun interface PurchaseVerifier {
    suspend fun verify(evidence: PurchaseEvidence): VerifiedPurchase?
}

internal fun verifiedAccess(
    catalog: BillingCatalog,
    productIds: List<String>,
    verified: VerifiedPurchase?,
    nowMillis: Long
): PaidAccess {
    if (verified == null || verified.productId !in productIds) return PaidAccess.None
    return when {
        verified.productId == catalog.lifetimeId && verified.access == PaidAccess.Lifetime ->
            PaidAccess.Lifetime
        verified.productId == catalog.subscriptionId && verified.access is PaidAccess.Subscription &&
            verified.access.isActive(nowMillis) -> verified.access
        else -> PaidAccess.None
    }
}

internal fun combinedPaidAccess(access: List<PaidAccess>, nowMillis: Long): PaidAccess {
    if (PaidAccess.Lifetime in access) return PaidAccess.Lifetime
    return access.filterIsInstance<PaidAccess.Subscription>()
        .filter { it.isActive(nowMillis) }
        .maxByOrNull { it.expiresAtMillis } ?: PaidAccess.None
}

internal const val TRIAL_DURATION_MILLIS = 7L * 24 * 60 * 60 * 1_000

internal fun remainingTrialMillis(startedAtMillis: Long?, nowMillis: Long): Long {
    if (startedAtMillis == null || startedAtMillis <= 0 || nowMillis < startedAtMillis) return 0
    val elapsed = nowMillis - startedAtMillis
    return if (elapsed >= TRIAL_DURATION_MILLIS) 0 else TRIAL_DURATION_MILLIS - elapsed
}

internal enum class BillingIssue {
    UNAVAILABLE, CONNECTION, PRODUCTS, PURCHASE, VERIFICATION, ACKNOWLEDGEMENT, NO_PURCHASES
}

internal data class BillingState(
    val refreshing: Boolean = false,
    val purchasing: Boolean = false,
    val offers: List<StoreOffer> = emptyList(),
    val access: PaidAccess = PaidAccess.None,
    val pendingPurchase: Boolean = false,
    val issue: BillingIssue? = null
)

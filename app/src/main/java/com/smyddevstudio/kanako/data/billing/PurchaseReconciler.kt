package com.smyddevstudio.kanako.data.billing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

internal data class StorePurchase(val evidence: PurchaseEvidence, val pending: Boolean)

/** Shared by Play callbacks, resume reconciliation and Restore purchases. */
internal class PurchaseReconciler(
    private val catalog: BillingCatalog,
    private val verifier: PurchaseVerifier,
    private val state: MutableStateFlow<BillingState>,
    private val nowMillis: () -> Long
) {
    suspend fun reconcile(
        purchases: List<StorePurchase>,
        restoring: Boolean,
        isCurrent: () -> Boolean
    ) {
        fun ensureCurrent() {
            if (!isCurrent()) throw CancellationException("Superseded purchase reconciliation")
        }
        ensureCurrent()
        val completed = purchases.filterNot { it.pending }
        val pending = purchases.any { it.pending }
        state.update { it.copy(verifying = completed.isNotEmpty(), pendingPurchase = pending, issue = null) }
        var allResolved = true
        val verified = completed.map { purchase ->
            var receivedResponse = false
            val result = withTimeoutOrNull(20_000) {
                for (retry in 0..3) {
                    ensureCurrent()
                    try {
                        return@withTimeoutOrNull verifier.verify(purchase.evidence).also { receivedResponse = true }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        ensureCurrent()
                        if (error !is TemporaryPurchaseVerificationException || retry == 3) {
                            return@withTimeoutOrNull null
                        }
                        delay(1_000L shl retry)
                    }
                }
                null
            }
            ensureCurrent()
            allResolved = allResolved && receivedResponse
            verifiedAccess(catalog, purchase.evidence.productIds, result, nowMillis())
        }
        ensureCurrent()
        val confirmed = combinedPaidAccess(verified, nowMillis())
        val unverified = verified.any { it == PaidAccess.None }
        state.update {
            it.copy(
                verifying = false,
                purchasesResolved = it.purchasesResolved || allResolved,
                access = if (unverified || pending)
                    combinedPaidAccess(listOf(it.access, confirmed), nowMillis()) else confirmed,
                pendingPurchase = pending,
                issue = when {
                    unverified && confirmed == PaidAccess.None -> BillingIssue.VERIFICATION
                    restoring && confirmed == PaidAccess.None && !pending -> BillingIssue.NO_PURCHASES
                    else -> null
                }
            )
        }
        // Only the trusted service delivers access and acknowledges; the client never consumes Lifetime.
    }
}

package com.smyddevstudio.kanako.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PlayBillingStore(
    context: Context,
    private val catalog: BillingCatalog,
    private val verifier: PurchaseVerifier,
    private val purchasesAllowed: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val obfuscatedAccountId: String? = null
) : AutoCloseable {
    private data class ProductOffer(
        val details: ProductDetails,
        val token: String?,
        val offer: StoreOffer
    )

    private class Failure(val issue: BillingIssue) : Exception()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(BillingState())
    val state = mutableState.asStateFlow()
    private var closed = false
    private var products = emptyMap<PurchasePlan, ProductOffer>()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { result, _ ->
            if (!closed) {
                mutableState.update {
                    it.copy(
                        purchasing = false,
                        issue = if (result.responseCode == BillingClient.BillingResponseCode.OK ||
                            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) null
                        else BillingIssue.PURCHASE
                    )
                }
                if (result.responseCode == BillingClient.BillingResponseCode.OK ||
                    result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refresh()
            }
        }
        .build()

    fun refresh(restoring: Boolean = false) {
        if (closed) return
        scope.launch {
            mutex.withLock {
                mutableState.update { it.copy(refreshing = true, issue = null) }
                try {
                    connect()
                    refreshPurchases(restoring)
                    refreshProducts()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Failure) {
                    mutableState.update { it.copy(issue = failure.issue) }
                } catch (_: Exception) {
                    mutableState.update { it.copy(issue = BillingIssue.UNAVAILABLE) }
                } finally {
                    mutableState.update { it.copy(refreshing = false) }
                }
            }
        }
    }

    fun purchase(activity: Activity, plan: PurchasePlan) {
        if (closed || !purchasesAllowed() || state.value.purchasing || state.value.refreshing) return
        scope.launch {
            mutex.withLock {
                mutableState.update { it.copy(refreshing = true, issue = null) }
                try {
                    connect()
                    refreshPurchases(restoring = false)
                    if (!purchasesAllowed() || state.value.access.isActive(nowMillis()) ||
                        state.value.pendingPurchase || activity.isFinishing || activity.isDestroyed) return@withLock
                    refreshProducts()
                    val product = products[plan] ?: throw Failure(BillingIssue.PRODUCTS)
                    val params = BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product.details)
                        .apply { product.token?.let { setOfferToken(it) } }
                        .build()
                    mutableState.update { it.copy(purchasing = true) }
                    val result = client.launchBillingFlow(
                        activity,
                        BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params))
                            .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }.build()
                    )
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        mutableState.update { it.copy(purchasing = false) }
                        throw Failure(BillingIssue.PURCHASE)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Failure) {
                    mutableState.update { it.copy(purchasing = false, issue = failure.issue) }
                } catch (_: Exception) {
                    mutableState.update { it.copy(purchasing = false, issue = BillingIssue.UNAVAILABLE) }
                } finally {
                    mutableState.update { it.copy(refreshing = false) }
                }
            }
        }
    }

    private suspend fun connect() {
        if (closed) throw CancellationException()
        if (client.isReady) return
        suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (!continuation.isActive) return
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                    else continuation.resumeWithException(Failure(BillingIssue.CONNECTION))
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resumeWithException(Failure(BillingIssue.CONNECTION))
                    if (!closed) mutableState.update { it.copy(issue = BillingIssue.CONNECTION) }
                }
            })
        }
    }

    private suspend fun refreshProducts() {
        products = emptyMap()
        mutableState.update { it.copy(offers = emptyList()) }
        val subscription = queryProducts(catalog.subscriptionId, BillingClient.ProductType.SUBS)
        val lifetime = queryProducts(catalog.lifetimeId, BillingClient.ProductType.INAPP)
        val available = mutableMapOf<PurchasePlan, ProductOffer>()
        subscription?.subscriptionOfferDetails.orEmpty().forEach { offer ->
            val plan = when (offer.basePlanId) {
                catalog.monthlyBasePlanId -> PurchasePlan.MONTHLY
                catalog.yearlyBasePlanId -> PurchasePlan.YEARLY
                else -> null
            }
            val phases = offer.pricingPhases.pricingPhaseList
            val phase = phases.singleOrNull()
            val expectedPeriod = if (plan == PurchasePlan.MONTHLY) "P1M" else "P1Y"
            if (plan != null && offer.offerId == null && phase != null &&
                phase.priceAmountMicros > 0 && phase.billingPeriod == expectedPeriod &&
                phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING) {
                available[plan] = ProductOffer(subscription!!, offer.offerToken,
                    StoreOffer(plan, phase.formattedPrice))
            }
        }
        val lifetimeOffer = lifetime?.oneTimePurchaseOfferDetailsList.orEmpty().firstOrNull {
            it.purchaseOptionId == catalog.lifetimePurchaseOptionId && it.offerId == null &&
                it.rentalDetails == null && it.preorderDetails == null && it.priceAmountMicros > 0
        }
        if (lifetime != null && lifetimeOffer != null) {
            available[PurchasePlan.LIFETIME] = ProductOffer(lifetime, lifetimeOffer.offerToken,
                StoreOffer(PurchasePlan.LIFETIME, lifetimeOffer.formattedPrice))
        }
        products = available
        mutableState.update {
            it.copy(
                offers = PurchasePlan.entries.mapNotNull { plan -> available[plan]?.offer },
                issue = it.issue ?: if (available.size != PurchasePlan.entries.size) BillingIssue.PRODUCTS else null
            )
        }
    }

    private suspend fun queryProducts(productId: String, type: String): ProductDetails? =
        suspendCancellableCoroutine { continuation ->
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId).setProductType(type).build()
            client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
            ) { result, details ->
                if (continuation.isActive) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK)
                        continuation.resume(details.productDetailsList.singleOrNull { it.productId == productId })
                    else continuation.resumeWithException(Failure(BillingIssue.PRODUCTS))
                }
            }
        }

    private suspend fun queryPurchases(type: String): List<Purchase> =
        suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, purchases ->
                if (continuation.isActive) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(purchases)
                    else continuation.resumeWithException(Failure(BillingIssue.CONNECTION))
                }
            }
        }

    private suspend fun refreshPurchases(restoring: Boolean) {
        val purchases = (queryPurchases(BillingClient.ProductType.SUBS) +
            queryPurchases(BillingClient.ProductType.INAPP))
            .distinctBy { it.purchaseToken }
            .filter { it.products.any { id -> id == catalog.subscriptionId || id == catalog.lifetimeId } }
        val completed = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val verified = completed.map { purchase ->
            val result = try {
                verifier.verify(PurchaseEvidence(purchase.purchaseToken, purchase.products,
                    purchase.originalJson, purchase.signature))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw Failure(BillingIssue.VERIFICATION)
            }
            purchase to verifiedAccess(catalog, purchase.products, result, nowMillis())
        }
        val access = combinedPaidAccess(verified.map { it.second }, nowMillis())
        val pending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        mutableState.update {
            it.copy(
                access = access,
                pendingPurchase = pending,
                issue = when {
                    completed.isNotEmpty() && verified.any { pair -> pair.second == PaidAccess.None } -> BillingIssue.VERIFICATION
                    restoring && access == PaidAccess.None && !pending -> BillingIssue.NO_PURCHASES
                    else -> null
                }
            )
        }
        for ((purchase, entitlement) in verified) {
            if (entitlement.isActive(nowMillis()) && !purchase.isAcknowledged) acknowledge(purchase.purchaseToken)
        }
    }

    private suspend fun acknowledge(token: String) {
        val result: BillingResult = suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) {
                if (continuation.isActive) continuation.resume(it)
            }
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK)
            mutableState.update { it.copy(issue = BillingIssue.ACKNOWLEDGEMENT) }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        client.endConnection()
        products = emptyMap()
    }
}

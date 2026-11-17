package com.smyddevstudio.kanako.data.billing

import android.app.Activity
import android.content.Context
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
    verifier: PurchaseVerifier,
    private val purchasesAllowed: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val obfuscatedAccountId: String? = null
) : AutoCloseable {
    private data class ProductOffer(
        val details: ProductDetails,
        val token: String,
        val offer: StoreOffer
    )

    private class Failure(val issue: BillingIssue) : Exception()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(BillingState())
    val state = mutableState.asStateFlow()
    private var closed = false
    private var products = emptyMap<PurchasePlan, ProductOffer>()
    private var revision = 0L
    private val reportedPurchases = mutableMapOf<String, Purchase>()
    private val reconciler = PurchaseReconciler(catalog, verifier, mutableState, nowMillis)

    private fun update(request: Long, transform: (BillingState) -> BillingState) {
        if (!closed && revision == request) mutableState.update(transform)
    }

    private val client = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { result, purchases ->
            if (!closed) {
                val completed = purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                val pending = purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }
                purchases.orEmpty().forEach { reportedPurchases[it.purchaseToken] = it }
                mutableState.update {
                    it.copy(
                        purchasing = false,
                        verifying = it.verifying || completed,
                        showFeedback = true,
                        pendingPurchase = pending,
                        issue = if (result.responseCode == BillingClient.BillingResponseCode.OK ||
                            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) null
                        else BillingIssue.PURCHASE
                    )
                }
                if (result.responseCode == BillingClient.BillingResponseCode.OK ||
                    result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refresh(userInitiated = true)
            }
        }
        .build()

    fun refresh(restoring: Boolean = false, userInitiated: Boolean = false) {
        if (closed) return
        val request = ++revision
        mutableState.update { it.copy(refreshing = true, issue = null, showFeedback = userInitiated || restoring || it.purchasing || it.showFeedback && it.verifying) }
        scope.launch {
            mutex.withLock {
                if (request != revision) return@withLock
                try {
                    connect()
                    try { refreshPurchases(restoring, request) }
                    finally { if (request == revision) refreshProducts(request) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Failure) {
                    update(request) { it.copy(issue = failure.issue) }
                } catch (_: Exception) {
                    update(request) { it.copy(issue = BillingIssue.UNAVAILABLE) }
                } finally {
                    update(request) { it.copy(refreshing = false, verifying = false) }
                }
            }
        }
    }

    fun purchase(activity: Activity, plan: PurchasePlan) {
        if (closed || !purchasesAllowed() || state.value.purchasing || state.value.refreshing ||
            state.value.verifying || state.value.issue == BillingIssue.VERIFICATION) return
        val request = ++revision
        mutableState.update { it.copy(refreshing = true, issue = null, showFeedback = true) }
        scope.launch {
            mutex.withLock {
                if (request != revision) return@withLock
                try {
                    connect()
                    refreshPurchases(restoring = false, request = request)
                    if (request != revision || state.value.issue == BillingIssue.VERIFICATION ||
                        !purchasesAllowed() || state.value.access.isActive(nowMillis()) ||
                        state.value.pendingPurchase || activity.isFinishing || activity.isDestroyed) return@withLock
                    refreshProducts(request)
                    if (request != revision) return@withLock
                    val product = products[plan] ?: throw Failure(BillingIssue.PRODUCTS)
                    val params = BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product.details)
                        .setOfferToken(product.token)
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
                    update(request) { it.copy(purchasing = false, issue = failure.issue) }
                } catch (_: Exception) {
                    update(request) { it.copy(purchasing = false, issue = BillingIssue.UNAVAILABLE) }
                } finally {
                    update(request) { it.copy(refreshing = false, verifying = false) }
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
                    if (!closed && !state.value.verifying) {
                        mutableState.update { it.copy(issue = BillingIssue.CONNECTION) }
                    }
                }
            })
        }
    }

    private suspend fun refreshProducts(request: Long) {
        products = emptyMap()
        mutableState.update { it.copy(offers = emptyList()) }
        suspend fun availableProduct(id: String, type: String): ProductDetails? = try {
            queryProducts(id, type)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val subscription = availableProduct(catalog.subscriptionId, BillingClient.ProductType.SUBS)
        val lifetime = availableProduct(catalog.lifetimeId, BillingClient.ProductType.INAPP)
        val available = mutableMapOf<PurchasePlan, ProductOffer>()
        subscription?.subscriptionOfferDetails.orEmpty().forEach { offer ->
            val plan = catalogPlan(catalog, subscription!!.productId, basePlanId = offer.basePlanId)
            val phases = offer.pricingPhases.pricingPhaseList
            val phase = phases.singleOrNull()
            val expectedPeriod = if (plan == PurchasePlan.MONTHLY) "P1M" else "P1Y"
            if (plan != null && offer.offerId == null && offer.offerToken.isNotBlank() && phase != null &&
                phase.priceAmountMicros > 0 && phase.billingPeriod == expectedPeriod &&
                phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING) {
                available[plan] = ProductOffer(subscription!!, offer.offerToken,
                    StoreOffer(plan, phase.formattedPrice))
            }
        }
        val lifetimeOffer = lifetime?.oneTimePurchaseOfferDetailsList.orEmpty().firstOrNull {
            catalogPlan(catalog, lifetime!!.productId, purchaseOptionId = it.purchaseOptionId) == PurchasePlan.LIFETIME &&
                !it.offerToken.isNullOrBlank() && it.offerId == null &&
                it.rentalDetails == null && it.preorderDetails == null && it.priceAmountMicros > 0
        }
        if (lifetime != null && lifetimeOffer != null) {
            available[PurchasePlan.LIFETIME] = ProductOffer(lifetime, checkNotNull(lifetimeOffer.offerToken),
                StoreOffer(PurchasePlan.LIFETIME, lifetimeOffer.formattedPrice))
        }
        if (request != revision || closed) return
        products = available
        update(request) {
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

    private suspend fun refreshPurchases(restoring: Boolean, request: Long) {
        val queried = try {
            queryPurchases(BillingClient.ProductType.SUBS) + queryPurchases(BillingClient.ProductType.INAPP)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Failure) {
            if (reportedPurchases.isEmpty()) throw failure
            emptyList()
        }
        val purchases = (reportedPurchases.values.toList() + queried)
            .distinctBy { it.purchaseToken }
            .filter { it.products.any { id -> id == catalog.subscriptionId || id == catalog.lifetimeId } }
        reconciler.reconcile(
            purchases.mapNotNull { purchase ->
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED, Purchase.PurchaseState.PENDING -> StorePurchase(
                        PurchaseEvidence(purchase.purchaseToken, purchase.products,
                            purchase.originalJson, purchase.signature),
                        pending = purchase.purchaseState == Purchase.PurchaseState.PENDING
                    )
                    else -> null
                }
            },
            restoring,
            isCurrent = { !closed && request == revision }
        )
        if (request == revision && state.value.issue != BillingIssue.VERIFICATION) reportedPurchases.clear()
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        client.endConnection()
        products = emptyMap()
        reportedPurchases.clear()
    }
}

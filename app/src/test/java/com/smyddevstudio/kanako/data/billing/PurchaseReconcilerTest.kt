package com.smyddevstudio.kanako.data.billing

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseReconcilerTest {
    private val catalog = BillingCatalog("premium", "monthly", "yearly", "lifetime", "buy")
    private val evidence = PurchaseEvidence("same-token", listOf("premium"), "signed-data", "signature")
    private val purchase = StorePurchase(evidence, pending = false)
    private val subscription = PaidAccess.Subscription(100_000)
    private val verified = VerifiedPurchase("premium", subscription)
    private fun temporary() = TemporaryPurchaseVerificationException(IOException("Unavailable"))

    @Test fun temporaryFailureThenSuccessKeepsVerifyingAndReusesTheSameEvidence() = runTest {
        val state = MutableStateFlow(BillingState())
        val attempts = mutableListOf<PurchaseEvidence>()
        val times = mutableListOf<Long>()
        val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
            attempts += it
            times += testScheduler.currentTime
            assertTrue(state.value.verifying)
            assertEquals(null, state.value.issue)
            if (attempts.size < 4) throw temporary()
            verified
        }, state) { testScheduler.currentTime }
        val job = launch { reconciler.reconcile(listOf(purchase), false) { true } }
        runCurrent()
        assertTrue(state.value.verifying)
        advanceTimeBy(6_999)
        runCurrent()
        assertEquals(3, attempts.size)
        assertEquals(null, state.value.issue)
        advanceUntilIdle()
        job.join()
        assertTrue(state.value.purchasesResolved)
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), times)
        assertTrue(attempts.all { it === evidence })
        assertEquals(subscription, state.value.access)
        assertFalse(state.value.verifying)
        assertEquals(null, state.value.issue)
    }

    @Test fun retryExhaustionOffersVerificationRecoveryAndPreservesExistingPaidAccess() = runTest {
        val existing = PaidAccess.Subscription(200_000)
        val state = MutableStateFlow(BillingState(access = existing))
        var attempts = 0
        val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
            attempts++
            assertEquals(existing, state.value.access)
            throw temporary()
        }, state) { testScheduler.currentTime }
        reconciler.reconcile(listOf(purchase), restoring = true) { true }
        assertFalse(state.value.purchasesResolved)
        assertEquals(4, attempts)
        assertEquals(7_000L, testScheduler.currentTime)
        assertEquals(BillingIssue.VERIFICATION, state.value.issue)
        assertEquals(existing, state.value.access)
        assertFalse(state.value.verifying)
        assertFalse(state.value.purchasing)
    }

    @Test fun pendingPaymentDoesNotVerifyOrGrantAccessAndCanLaterComplete() = runTest {
        val state = MutableStateFlow(BillingState())
        var attempts = 0
        val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
            attempts++
            verified
        }, state) { 1_000L }
        reconciler.reconcile(listOf(purchase.copy(pending = true)), true) { true }
        assertTrue(state.value.purchasesResolved)
        assertEquals(0, attempts)
        assertTrue(state.value.pendingPurchase)
        assertFalse(state.value.verifying)
        assertEquals(PaidAccess.None, state.value.access)
        assertEquals(null, state.value.issue)
        reconciler.reconcile(listOf(purchase), false) { true }
        assertEquals(1, attempts)
        assertFalse(state.value.pendingPurchase)
        assertEquals(subscription, state.value.access)
    }

    @Test fun permanentFailureAndUnverifiedResponseAreNotRetried() = runTest {
        for (failure in listOf(false, true)) {
            val state = MutableStateFlow(BillingState())
            var attempts = 0
            val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
                attempts++
                if (failure) throw IllegalArgumentException("Rejected")
                null
            }, state) { 1_000L }
            reconciler.reconcile(listOf(purchase), false) { true }
            assertEquals(1, attempts)
            assertEquals(0L, testScheduler.currentTime)
            assertEquals(BillingIssue.VERIFICATION, state.value.issue)
            assertEquals(PaidAccess.None, state.value.access)
        }
    }

    @Test fun aSlowServerHasABoundedDeadline() = runTest {
        val state = MutableStateFlow(BillingState())
        val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
            awaitCancellation()
        }, state) { testScheduler.currentTime }
        reconciler.reconcile(listOf(purchase), false) { true }
        assertFalse(state.value.purchasesResolved)
        assertEquals(20_000L, testScheduler.currentTime)
        assertEquals(BillingIssue.VERIFICATION, state.value.issue)
        assertFalse(state.value.verifying)
    }

    @Test fun staleSuccessOrFailureCannotOverwriteNewerVerifiedAccess() = runTest {
        for (fails in listOf(false, true)) {
            val state = MutableStateFlow(BillingState())
            val response = CompletableDeferred<Unit>()
            var current = true
            val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
                response.await()
                if (fails) throw temporary()
                verified
            }, state) { 1_000L }
            val old = launch { reconciler.reconcile(listOf(purchase), false) { current } }
            runCurrent()
            current = false
            state.value = BillingState(access = PaidAccess.Lifetime, purchasesResolved = true)
            response.complete(Unit)
            old.join()
            assertTrue(state.value.purchasesResolved)
            assertEquals(PaidAccess.Lifetime, state.value.access)
            assertEquals(null, state.value.issue)
            assertFalse(state.value.verifying)
        }
    }

    @Test fun cancellationStopsRetriesAndRestoreUsesTheSameReconciliation() = runTest {
        val state = MutableStateFlow(BillingState())
        var attempts = 0
        var available = false
        val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
            attempts++
            if (!available) throw temporary()
            verified
        }, state) { 1_000L }
        val old = launch { reconciler.reconcile(listOf(purchase), false) { true } }
        runCurrent()
        old.cancelAndJoin()
        advanceUntilIdle()
        assertEquals(1, attempts)
        available = true
        reconciler.reconcile(listOf(purchase), true) { true }
        assertEquals(2, attempts)
        assertEquals(subscription, state.value.access)
        assertEquals(null, state.value.issue)
    }

    @Test fun confirmedLifetimeIsNotHiddenByAnOldUnverifiedSubscription() = runTest {
        val state = MutableStateFlow(BillingState())
        val lifetime = StorePurchase(evidence.copy(token = "lifetime-token", productIds = listOf("lifetime")), false)
        val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
            if (it.token == lifetime.evidence.token) VerifiedPurchase("lifetime", PaidAccess.Lifetime) else null
        }, state) { 1_000L }
        reconciler.reconcile(listOf(purchase, lifetime), true) { true }
        assertEquals(PaidAccess.Lifetime, state.value.access)
        assertEquals(null, state.value.issue)
    }

    @Test fun restoreWithoutPurchasesReportsNoPurchases() = runTest {
        val state = MutableStateFlow(BillingState())
        val reconciler = PurchaseReconciler(catalog, PurchaseVerifier {
            error("No purchase should be verified")
        }, state) { 1_000L }
        reconciler.reconcile(emptyList(), true) { true }
        assertTrue(state.value.purchasesResolved)
        assertEquals(BillingIssue.NO_PURCHASES, state.value.issue)
        assertEquals(PaidAccess.None, state.value.access)
    }
}

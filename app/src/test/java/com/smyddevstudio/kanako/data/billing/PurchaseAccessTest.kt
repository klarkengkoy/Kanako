package com.smyddevstudio.kanako.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseAccessTest {
    private val catalog = BillingCatalog("premium", "monthly", "yearly", "lifetime", "buy")
    private val start = 1_000L

    @Test fun trialEndsAtExactlySevenDaysAndNeverCreatesPaidAccess() {
        assertEquals(TRIAL_DURATION_MILLIS, remainingTrialMillis(start, start))
        assertEquals(1L, remainingTrialMillis(start, start + TRIAL_DURATION_MILLIS - 1))
        assertEquals(0L, remainingTrialMillis(start, start + TRIAL_DURATION_MILLIS))
        assertEquals(0L, remainingTrialMillis(start, start + TRIAL_DURATION_MILLIS + 1))
        assertFalse(PaidAccess.None.isActive(start + TRIAL_DURATION_MILLIS))
    }

    @Test fun missingOrInvalidTrialOriginCannotGrantAccess() {
        assertEquals(0L, remainingTrialMillis(null, start))
        assertEquals(0L, remainingTrialMillis(-1, start))
        assertEquals(0L, remainingTrialMillis(start, start - 1))
        assertEquals(0L, remainingTrialMillis(start, Long.MAX_VALUE))
    }

    @Test fun aPurchaseWithoutVerificationCannotUnlockEitherProduct() {
        assertEquals(PaidAccess.None, verifiedAccess(catalog, listOf("premium"), null, start))
        assertEquals(PaidAccess.None, verifiedAccess(catalog, listOf("lifetime"), null, start))
    }

    @Test fun unrelatedProductOrMismatchedVerificationCannotUnlockLifetime() {
        val lifetime = VerifiedPurchase("lifetime", PaidAccess.Lifetime)
        assertEquals(PaidAccess.None, verifiedAccess(catalog, listOf("other"), lifetime, start))
        assertEquals(PaidAccess.None, verifiedAccess(catalog, listOf("premium"), lifetime, start))
        assertEquals(PaidAccess.None, verifiedAccess(catalog, listOf("premium"),
            VerifiedPurchase("premium", PaidAccess.Lifetime), start))
        assertEquals(PaidAccess.None, verifiedAccess(catalog, listOf("lifetime"),
            VerifiedPurchase("lifetime", PaidAccess.Subscription(start + 100)), start))
    }

    @Test fun cancelledButNotExpiredSubscriptionRetainsAccessUntilItsVerifiedExpiry() {
        val verified = VerifiedPurchase("premium", PaidAccess.Subscription(start + 100))
        val access = verifiedAccess(catalog, listOf("premium"), verified, start)
        assertTrue(access.isActive(start + 99))
        assertFalse(access.isActive(start + 100))
        assertEquals(PaidAccess.None, verifiedAccess(catalog, listOf("premium"), verified, start + 100))
    }

    @Test fun lifetimeWinsOverSubscriptionAndSurvivesSubscriptionExpiry() {
        val access = combinedPaidAccess(listOf(PaidAccess.Subscription(start + 100), PaidAccess.Lifetime), start)
        assertEquals(PaidAccess.Lifetime, access)
        assertTrue(access.isActive(start + TRIAL_DURATION_MILLIS))
    }

    @Test fun restoringChoosesTheLatestUnexpiredVerifiedSubscription() {
        assertEquals(PaidAccess.Subscription(start + 200), combinedPaidAccess(listOf(
            PaidAccess.None, PaidAccess.Subscription(start - 1),
            PaidAccess.Subscription(start + 100), PaidAccess.Subscription(start + 200)
        ), start))
        assertEquals(PaidAccess.None,
            combinedPaidAccess(listOf(PaidAccess.Subscription(start)), start))
    }
}

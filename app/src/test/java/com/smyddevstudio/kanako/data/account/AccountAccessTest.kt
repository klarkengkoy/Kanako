package com.smyddevstudio.kanako.data.account

import com.smyddevstudio.kanako.data.billing.PaidAccess
import com.smyddevstudio.kanako.data.billing.TRIAL_DURATION_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAccessTest {
    private val started = 1_000L
    private val received = 100L

    private fun grant(
        serverNow: Long = started,
        access: PaidAccess = PaidAccess.None,
        lease: Long = MAX_ACCESS_LEASE_MILLIS
    ) = AccountGrant(
        "reader-a", serverNow, started, started + TRIAL_DURATION_MILLIS,
        access, lease, received
    )

    @Test fun onlyTheAccountThatOwnsTheGrantCanUseItsTrial() {
        val grant = grant()
        assertTrue(grant.canRead("reader-a", received))
        assertFalse(grant.canRead("reader-b", received))
        assertFalse(grant.canRead(null, received))
    }

    @Test fun theTrialEndsAtTheServerDeadlineWithoutCreatingPaidAccess() {
        val grant = grant(serverNow = started + TRIAL_DURATION_MILLIS - 1)
        assertTrue(grant.canRead("reader-a", received))
        assertFalse(grant.canRead("reader-a", received + 1))
        assertEquals(0L, grant.trialRemaining(received + 1))
        assertFalse(grant.hasPaidAccess(received + 1))
    }

    @Test fun aRebootOrBackwardsElapsedClockCannotReuseTheGrant() {
        assertFalse(grant().canRead("reader-a", received - 1))
        assertFalse(grant().isFresh(0))
    }

    @Test fun anExpiredLeaseRequiresAnotherServerCheckEvenForLifetime() {
        val grant = grant(access = PaidAccess.Lifetime, lease = 100)
        assertTrue(grant.canRead("reader-a", received + 99))
        assertFalse(grant.canRead("reader-a", received + 100))
    }

    @Test fun subscriptionAccessEndsAtItsVerifiedExpiryAfterTheTrial() {
        val now = started + TRIAL_DURATION_MILLIS + 10
        val grant = grant(serverNow = now, access = PaidAccess.Subscription(now + 100))
        assertTrue(grant.canRead("reader-a", received + 99))
        assertFalse(grant.canRead("reader-a", received + 100))
    }

    @Test fun purchaseOptionsRequireAFreshGrantForTheSignedInAccount() {
        val expiredTrial = grant(serverNow = started + TRIAL_DURATION_MILLIS)
        assertTrue(AccountState(true, "reader-a", grant = expiredTrial, elapsedMillis = received).needsPurchase)
        assertFalse(AccountState(true, "reader-b", grant = expiredTrial, elapsedMillis = received).needsPurchase)
        assertFalse(AccountState(true, "reader-a", grant = expiredTrial,
            elapsedMillis = received + MAX_ACCESS_LEASE_MILLIS).needsPurchase)
        assertFalse(AccountState(true, "reader-a").needsPurchase)
        assertFalse(AccountState(true, "reader-a", grant = grant(), elapsedMillis = received).needsPurchase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aServerResponseCannotSilentlyExtendTheSevenDayTrial() {
        AccountGrant("reader-a", started, started, started + TRIAL_DURATION_MILLIS + 1,
            PaidAccess.None, 100, received)
    }

    @Test fun designatedTestAccountsCanOpenCheckoutDuringTheirUnchangedTrial() {
        val original = grant()
        val testGrant = original.copy(testCheckoutAllowed = true)
        val state = AccountState(true, "reader-a", grant = testGrant, elapsedMillis = received)
        assertTrue(state.canPurchase)
        assertFalse(state.needsPurchase)
        assertTrue(state.canRead)
        assertEquals(original.trialStartedAtMillis, testGrant.trialStartedAtMillis)
        assertEquals(original.trialEndsAtMillis, testGrant.trialEndsAtMillis)
        assertFalse(testGrant.hasPaidAccess(received))
        assertFalse(state.copy(userId = "reader-b").canPurchase)
        assertFalse(state.copy(userId = null).canPurchase)
        assertFalse(state.copy(elapsedMillis = received + MAX_ACCESS_LEASE_MILLIS).canPurchase)
        assertFalse(state.copy(grant = testGrant.copy(paidAccess = PaidAccess.Lifetime)).canPurchase)
        assertFalse(state.copy(grant = testGrant.copy(
            paidAccess = PaidAccess.Subscription(started + 1_000))).canPurchase)
    }

    @Test fun testCheckoutNeverGrantsReadingAccessAfterTrialExpiry() {
        val testGrant = grant(serverNow = started + TRIAL_DURATION_MILLIS).copy(testCheckoutAllowed = true)
        val state = AccountState(true, "reader-a", grant = testGrant, elapsedMillis = received)
        assertTrue(state.needsPurchase)
        assertFalse(state.canRead)
        assertFalse(testGrant.hasPaidAccess(received))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aServerResponseCannotCreateAnUnlimitedOfflineLease() {
        grant(lease = MAX_ACCESS_LEASE_MILLIS + 1)
    }

    @Test fun signInWithoutTheAccessServiceDoesNotStartATrialOrOfferPurchases() {
        val signedIn = AccountState(
            configured = true,
            userId = "reader-a",
            accessServiceConfigured = false
        )
        assertFalse(signedIn.canRead)
        assertFalse(signedIn.needsPurchase)
        assertEquals(null, signedIn.grant)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPlanLabelCannotTurnASubscriptionIntoLifetimeAccess() {
        AccountGrant("reader-a", started, started, started + TRIAL_DURATION_MILLIS,
            PaidAccess.Subscription(started + 1_000), 100, received,
            com.smyddevstudio.kanako.data.billing.PurchasePlan.LIFETIME)
    }

    @Test fun reviewerGrantHasNoTrialOrPaidEntitlementAndNeverOpensCheckout() {
        val reviewer = AccountGrant(
            "reader-a", started + TRIAL_DURATION_MILLIS * 100, 0, 0,
            PaidAccess.None, MAX_ACCESS_LEASE_MILLIS, received, reviewerAccess = true
        )
        val state = AccountState(true, "reader-a", grant = reviewer, elapsedMillis = received)
        assertTrue(state.hasSession)
        assertTrue(state.canRead)
        assertTrue(state.testSession)
        assertFalse(state.needsPurchase)
        assertEquals(0L, reviewer.trialRemaining(received))
        assertFalse(reviewer.hasPaidAccess(received))
        assertFalse(state.copy(userId = "reader-b").canRead)
        assertFalse(state.copy(userId = null).hasSession)
        assertFalse(state.copy(elapsedMillis = received + MAX_ACCESS_LEASE_MILLIS).canRead)
    }

    @Test fun pendingReviewerAuthenticationDoesNotOpenTheApp() {
        assertFalse(AccountState(true, "reader-a", reviewerSignInPending = true).hasSession)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aReviewerGrantCannotPretendToBeAPurchasedLifetime() {
        AccountGrant("reader-a", started, 0, 0, PaidAccess.Lifetime,
            MAX_ACCESS_LEASE_MILLIS, received, reviewerAccess = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aReviewerGrantCannotStartATrialOrOpenEarlyCheckout() {
        grant().copy(reviewerAccess = true, testCheckoutAllowed = true)
    }

    @Test fun signedInSessionDoesNotBypassAnExpiredTrial() {
        val state = AccountState(
            configured = true, userId = "reader-a",
            grant = grant(serverNow = started + TRIAL_DURATION_MILLIS), elapsedMillis = received
        )
        assertTrue(state.hasSession)
        assertFalse(state.canRead)
        assertTrue(state.needsPurchase)
        assertFalse(AccountState(configured = true).hasSession)
    }

    @Test fun aNewAccountCanChooseWithoutStartingItsTrial() {
        val available = AccountGrant("reader-a", started, 0, 0, PaidAccess.None,
            MAX_ACCESS_LEASE_MILLIS, received, checkoutAllowed = true)
        val state = AccountState(true, "reader-a", loginChoicePending = true)
            .withVerifiedGrant(available, received)
        assertTrue(available.trialAvailable)
        assertFalse(state.canRead)
        assertTrue(state.canPurchase)
        assertTrue(state.paywallRequested)
        assertEquals(0L, available.trialStartedAtMillis)
        assertEquals(0L, available.trialEndsAtMillis)
    }

    @Test fun eachExplicitSignInOffersTheExistingTrialWithoutResettingItsDeadline() {
        val original = grant().copy(checkoutAllowed = true)
        val first = AccountState(true, "reader-a", loginChoicePending = true)
            .withVerifiedGrant(original, received)
        assertTrue(first.paywallRequested)
        assertTrue(first.canRead)
        val dismissed = first.copy(paywallRequested = false)
        val refreshed = dismissed.withVerifiedGrant(original, received + 1)
        assertFalse(refreshed.paywallRequested)
        val signedInAgain = refreshed.copy(loginChoicePending = true).withVerifiedGrant(original, received + 2)
        assertTrue(signedInAgain.paywallRequested)
        assertEquals(original.trialStartedAtMillis, signedInAgain.grant?.trialStartedAtMillis)
        assertEquals(original.trialEndsAtMillis, signedInAgain.grant?.trialEndsAtMillis)
    }

    @Test fun restoredActiveTrialContinuesAndExpiredTrialOffersPurchases() {
        val restored = AccountState(true, "reader-a").withVerifiedGrant(grant(), received)
        assertTrue(restored.canRead)
        assertFalse(restored.paywallRequested)
        val expired = restored.withVerifiedGrant(
            grant(serverNow = started + TRIAL_DURATION_MILLIS), received)
        assertFalse(expired.canRead)
        assertTrue(expired.paywallRequested)
        assertTrue(expired.needsPurchase)
    }

    @Test fun paidAndReviewerAccountsNeverPresentTheSignInPaywall() {
        for (paid in listOf(PaidAccess.Lifetime, PaidAccess.Subscription(started + 1000))) {
            val state = AccountState(true, "reader-a", loginChoicePending = true, paywallRequested = true)
                .withVerifiedGrant(grant(access = paid), received)
            assertTrue(state.canRead)
            assertFalse(state.paywallRequested)
            assertFalse(state.canPurchase)
        }
        val reviewer = AccountGrant("reader-a", started, 0, 0, PaidAccess.None,
            MAX_ACCESS_LEASE_MILLIS, received, reviewerAccess = true)
        val state = AccountState(true, "reader-a", loginChoicePending = true, paywallRequested = true)
            .withVerifiedGrant(reviewer, received)
        assertTrue(state.canRead)
        assertFalse(state.paywallRequested)
        assertFalse(state.canPurchase)
    }

    @Test fun subscriptionDeadlineRequiresServerConfirmationBeforeShowingThePaywall() {
        val now = started + TRIAL_DURATION_MILLIS
        val active = grant(serverNow = now, access = PaidAccess.Subscription(now + 100))
        val state = AccountState(true, "reader-a").withVerifiedGrant(active, received)
            .copy(elapsedMillis = received + 100)
        assertFalse(state.canRead)
        assertTrue(state.subscriptionNeedsRefresh)
        assertFalse(state.needsPurchase)
        assertFalse(state.canPurchase)
        assertFalse(state.paywallRequested)
        val renewed = state.withVerifiedGrant(
            grant(serverNow = now + 100, access = PaidAccess.Subscription(now + 1000)), received + 100)
        assertTrue(renewed.hasPaidAccess)
        assertFalse(renewed.paywallRequested)
        val expired = state.withVerifiedGrant(grant(serverNow = now + 100), received + 100)
        assertTrue(expired.paywallRequested)
        assertFalse(expired.canRead)
    }

    @Test fun aConnectionFailureDoesNotTurnACachedSubscriberIntoAnUnpaidCustomer() {
        val state = AccountState(true, "reader-a", grant = grant(access = PaidAccess.Lifetime),
            elapsedMillis = received, loginChoicePending = true, issue = AccountIssue.CONNECTION)
        assertTrue(state.canRead)
        assertTrue(state.hasPaidAccess)
        assertFalse(state.paywallRequested)
        assertFalse(state.canPurchase)
        assertFalse(state.accessResolved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anUnstartedTrialCannotCarryOnlyOneDate() {
        AccountGrant("reader-a", started, 0, started + TRIAL_DURATION_MILLIS,
            PaidAccess.None, 100, received)
    }

    @Test fun missingSetupOrMissingSessionNeverInventsReadingAccess() {
        assertFalse(AccountState(configured = false).canRead)
        assertFalse(AccountState(configured = true, userId = "reader-a").canRead)
    }
}

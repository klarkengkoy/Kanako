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
        assertTrue(state.needsPurchase)
        assertTrue(state.canRead)
        assertEquals(original.trialStartedAtMillis, testGrant.trialStartedAtMillis)
        assertEquals(original.trialEndsAtMillis, testGrant.trialEndsAtMillis)
        assertFalse(testGrant.hasPaidAccess(received))
        assertFalse(state.copy(userId = "reader-b").needsPurchase)
        assertFalse(state.copy(userId = null).needsPurchase)
        assertFalse(state.copy(elapsedMillis = received + MAX_ACCESS_LEASE_MILLIS).needsPurchase)
        assertFalse(state.copy(grant = testGrant.copy(paidAccess = PaidAccess.Lifetime)).needsPurchase)
        assertFalse(state.copy(grant = testGrant.copy(
            paidAccess = PaidAccess.Subscription(started + 1_000))).needsPurchase)
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

    @Test fun testSessionUnlocksReadingWithoutCreatingATrialOrPurchase() {
        val state = AccountState(configured = false, testSession = true)
        assertTrue(state.hasSession)
        assertTrue(state.canRead)
        assertFalse(state.needsPurchase)
        assertEquals(null, state.userId)
        assertEquals(null, state.grant)
        assertFalse(state.copy(testSession = false).hasSession)
        assertFalse(state.copy(testSession = false).canRead)
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

    @Test fun missingSetupOrMissingSessionNeverInventsReadingAccess() {
        assertFalse(AccountState(configured = false).canRead)
        assertFalse(AccountState(configured = true, userId = "reader-a").canRead)
    }
}

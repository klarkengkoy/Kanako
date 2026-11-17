package com.smyddevstudio.kanako.data.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountResponseOrderTest {
    @Test fun olderSessionSuccessAndFailureCannotReplaceVerifiedAccess() {
        val order = AccountResponseOrder()
        val session = order.begin()
        val verification = order.begin()
        assertTrue(order.accept(verification, purchaseVerification = true))
        assertFalse(order.accept(session))
        assertFalse(order.isCurrent(session))
    }

    @Test fun anOverlappingSessionIsStaleEvenIfItWasRequestedLast() {
        val order = AccountResponseOrder()
        val verification = order.begin()
        val session = order.begin()
        assertTrue(order.accept(verification, purchaseVerification = true))
        assertFalse(order.isCurrent(session))
        // The session timestamp may be later even though its paid snapshot predates verification.
        assertFalse(order.accept(session))
    }

    @Test fun purchaseConfirmationWinsOverAnOverlappingSessionThatReturnedFirst() {
        val order = AccountResponseOrder()
        val verification = order.begin()
        val session = order.begin()
        assertTrue(order.accept(session))
        assertTrue(order.isVerificationCurrent(verification))
        assertTrue(order.accept(verification, purchaseVerification = true))
    }

    @Test fun olderVerificationCannotReplaceANewerVerifiedPlan() {
        val order = AccountResponseOrder()
        val monthly = order.begin()
        val lifetime = order.begin()
        assertTrue(order.accept(lifetime, purchaseVerification = true))
        assertFalse(order.accept(monthly, purchaseVerification = true))
        assertFalse(order.isCurrent(monthly))
        assertFalse(order.isVerificationCurrent(monthly))
    }

    @Test fun aRefreshStartedAfterConfirmationCanStillApplyExpiryOrRevocation() {
        val order = AccountResponseOrder()
        assertTrue(order.accept(order.begin(), purchaseVerification = true))
        val laterRevocation = order.begin()
        assertTrue(order.isCurrent(laterRevocation))
        assertTrue(order.accept(laterRevocation))
    }

    @Test fun aNewAccountAndFreshRequestsCanRecoverAfterFailure() {
        val order = AccountResponseOrder()
        val oldAccount = order.begin()
        assertTrue(order.accept(oldAccount, purchaseVerification = true))
        order.reset()
        assertFalse(order.accept(oldAccount, purchaseVerification = true))
        val request = order.begin()
        assertTrue(order.isCurrent(request))
        assertTrue(order.accept(request))
        assertTrue(order.isCurrent(order.begin()))
    }
}

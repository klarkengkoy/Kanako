package com.smyddevstudio.kanako.data.account

import com.smyddevstudio.kanako.data.billing.PaidAccess
import com.smyddevstudio.kanako.data.billing.PurchasePlan
import com.smyddevstudio.kanako.data.billing.TRIAL_DURATION_MILLIS

internal const val MAX_ACCESS_LEASE_MILLIS = 24L * 60 * 60 * 1_000

internal data class AccountGrant(
    val userId: String,
    val serverNowMillis: Long,
    val trialStartedAtMillis: Long,
    val trialEndsAtMillis: Long,
    val paidAccess: PaidAccess,
    val leaseDurationMillis: Long,
    val receivedAtElapsedMillis: Long,
    val plan: PurchasePlan? = null,
    val testCheckoutAllowed: Boolean = false,
    val reviewerAccess: Boolean = false,
    val checkoutAllowed: Boolean = false
) {
    init {
        require(userId.isNotBlank())
        require(serverNowMillis > 0)
        if (reviewerAccess) {
            require(trialStartedAtMillis == 0L && trialEndsAtMillis == 0L)
            require(paidAccess == PaidAccess.None && plan == null && !testCheckoutAllowed && !checkoutAllowed)
        } else if (trialStartedAtMillis != 0L || trialEndsAtMillis != 0L) {
            require(trialStartedAtMillis > 0 && trialStartedAtMillis <= serverNowMillis)
            require(trialEndsAtMillis > trialStartedAtMillis)
            require(trialEndsAtMillis - trialStartedAtMillis == TRIAL_DURATION_MILLIS)
        }
        require(leaseDurationMillis in 1..MAX_ACCESS_LEASE_MILLIS)
        require(receivedAtElapsedMillis >= 0)
        require(plan == null || when (paidAccess) {
            PaidAccess.None -> false
            PaidAccess.Lifetime -> plan == PurchasePlan.LIFETIME
            is PaidAccess.Subscription -> plan == PurchasePlan.MONTHLY || plan == PurchasePlan.YEARLY
        })
    }

    val trialAvailable: Boolean get() = !reviewerAccess && trialStartedAtMillis == 0L && trialEndsAtMillis == 0L

    fun serverNow(elapsedMillis: Long): Long {
        val duration = (elapsedMillis - receivedAtElapsedMillis).coerceAtLeast(0)
        return if (duration > Long.MAX_VALUE - serverNowMillis) Long.MAX_VALUE else serverNowMillis + duration
    }

    fun isFresh(elapsedMillis: Long): Boolean =
        elapsedMillis >= receivedAtElapsedMillis &&
            elapsedMillis - receivedAtElapsedMillis < leaseDurationMillis

    fun trialRemaining(elapsedMillis: Long): Long =
        (trialEndsAtMillis - serverNow(elapsedMillis)).coerceAtLeast(0)

    fun hasPaidAccess(elapsedMillis: Long): Boolean = when (val access = paidAccess) {
        PaidAccess.None -> false
        PaidAccess.Lifetime -> true
        is PaidAccess.Subscription -> serverNow(elapsedMillis) < access.expiresAtMillis
    }

    fun canRead(userId: String?, elapsedMillis: Long): Boolean =
        this.userId == userId && isFresh(elapsedMillis) &&
            (reviewerAccess || trialRemaining(elapsedMillis) > 0 || hasPaidAccess(elapsedMillis))
}

internal enum class AccountIssue { SETUP, SIGN_IN, CONNECTION, DELETION, TRIAL }

internal data class AccountState(
    val configured: Boolean,
    val userId: String? = null,
    val email: String? = null,
    val busy: Boolean = false,
    val grant: AccountGrant? = null,
    val elapsedMillis: Long = 0,
    val issue: AccountIssue? = null,
    val accessServiceConfigured: Boolean = false,
    val reviewerSignInPending: Boolean = false,
    val loginChoicePending: Boolean = false,
    val paywallRequested: Boolean = false,
    val accessResolved: Boolean = false,
    val displayName: String? = null,
    val profilePhotoUrl: String? = null
) {
    val testSession: Boolean get() = grant?.let { it.userId == userId && it.reviewerAccess } == true
    val hasSession: Boolean get() = userId != null && !reviewerSignInPending
    val canRead: Boolean get() = grant?.canRead(userId, elapsedMillis) == true
    val hasFreshGrant: Boolean get() = grant?.let { it.userId == userId && it.isFresh(elapsedMillis) } == true
    val hasPaidAccess: Boolean get() = hasFreshGrant && grant?.hasPaidAccess(elapsedMillis) == true
    val subscriptionNeedsRefresh: Boolean get() = grant?.let {
        val subscription = it.paidAccess as? PaidAccess.Subscription
        subscription != null && !it.hasPaidAccess(elapsedMillis) && it.serverNowMillis < subscription.expiresAtMillis
    } == true
    val needsPurchase: Boolean get() = hasFreshGrant && !testSession && !hasPaidAccess &&
        !subscriptionNeedsRefresh && grant?.trialRemaining(elapsedMillis) == 0L
    val canPurchase: Boolean get() = hasFreshGrant && !testSession && !hasPaidAccess &&
        !subscriptionNeedsRefresh && (needsPurchase || grant?.checkoutAllowed == true || grant?.testCheckoutAllowed == true)

    fun withVerifiedGrant(verified: AccountGrant, elapsed: Long): AccountState {
        val next = copy(grant = verified, elapsedMillis = elapsed, issue = null, accessResolved = true)
        val showChoice = next.hasFreshGrant && !next.testSession && !next.hasPaidAccess &&
            (paywallRequested || loginChoicePending || (next.needsPurchase && (!needsPurchase || !accessResolved)))
        return next.copy(paywallRequested = showChoice, loginChoicePending = false)
    }
}

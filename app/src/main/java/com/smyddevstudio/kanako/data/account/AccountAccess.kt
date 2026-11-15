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
    val testCheckoutAllowed: Boolean = false
) {
    init {
        require(userId.isNotBlank())
        require(serverNowMillis > 0 && trialStartedAtMillis > 0)
        require(trialEndsAtMillis > trialStartedAtMillis)
        require(trialEndsAtMillis - trialStartedAtMillis == TRIAL_DURATION_MILLIS)
        require(trialStartedAtMillis <= serverNowMillis)
        require(leaseDurationMillis in 1..MAX_ACCESS_LEASE_MILLIS)
        require(receivedAtElapsedMillis >= 0)
        require(plan == null || when (paidAccess) {
            PaidAccess.None -> false
            PaidAccess.Lifetime -> plan == PurchasePlan.LIFETIME
            is PaidAccess.Subscription -> plan == PurchasePlan.MONTHLY || plan == PurchasePlan.YEARLY
        })
    }

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
            (trialRemaining(elapsedMillis) > 0 || hasPaidAccess(elapsedMillis))
}

internal enum class AccountIssue { SETUP, SIGN_IN, CONNECTION, DELETION }

internal data class AccountState(
    val configured: Boolean,
    val userId: String? = null,
    val email: String? = null,
    val busy: Boolean = false,
    val grant: AccountGrant? = null,
    val elapsedMillis: Long = 0,
    val issue: AccountIssue? = null,
    val accessServiceConfigured: Boolean = false,
    val testSession: Boolean = false
) {
    val hasSession: Boolean get() = userId != null || testSession
    val canRead: Boolean get() = testSession || grant?.canRead(userId, elapsedMillis) == true
    val needsPurchase: Boolean get() = !testSession && userId != null && grant?.let {
        it.userId == userId && it.isFresh(elapsedMillis) &&
            (it.trialRemaining(elapsedMillis) == 0L || it.testCheckoutAllowed) &&
            !it.hasPaidAccess(elapsedMillis)
    } == true
}

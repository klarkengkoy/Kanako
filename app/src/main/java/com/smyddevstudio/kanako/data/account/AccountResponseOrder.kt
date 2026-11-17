package com.smyddevstudio.kanako.data.account

/** Main-dispatcher ordering for server snapshots and errors from overlapping requests. */
internal class AccountResponseOrder {
    private var nextRequest = 0L
    private var acceptedRequest = 0L
    private var acceptedVerification = 0L

    fun begin(): Long = ++nextRequest

    fun isCurrent(request: Long): Boolean = request > acceptedRequest

    fun isVerificationCurrent(request: Long): Boolean = request > acceptedVerification

    fun accept(request: Long, purchaseVerification: Boolean = false): Boolean {
        // A session reads paid status separately from its timestamp. Confirmation wins over
        // overlapping sessions; a session started afterward can still revoke or expire access.
        if (purchaseVerification) {
            if (!isVerificationCurrent(request)) return false
            acceptedVerification = request
        } else if (!isCurrent(request)) return false
        acceptedRequest = nextRequest
        return true
    }

    fun reset() {
        acceptedRequest = ++nextRequest
        acceptedVerification = nextRequest
    }
}

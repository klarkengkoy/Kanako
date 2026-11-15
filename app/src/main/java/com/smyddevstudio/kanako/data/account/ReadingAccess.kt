package com.smyddevstudio.kanako.data.account

internal object ReadingAccess {
    @Volatile private var accountCheck: (() -> Boolean)? = null

    fun initialize(check: () -> Boolean) {
        accountCheck = check
    }

    fun allowedNow(): Boolean =
        accountCheck?.invoke() == true
}

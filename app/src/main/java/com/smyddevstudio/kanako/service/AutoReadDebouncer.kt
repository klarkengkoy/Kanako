package com.smyddevstudio.kanako.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AutoReadDebouncer(
    private val scope: CoroutineScope,
    private val onRead: () -> Unit,
    private val settleMillis: Long = 700L
) {
    private var job: Job? = null
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) cancel()
        }

    fun onMovement(eligible: Boolean) {
        cancel()
        if (!enabled || !eligible) return
        job = scope.launch {
            delay(settleMillis)
            onRead()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

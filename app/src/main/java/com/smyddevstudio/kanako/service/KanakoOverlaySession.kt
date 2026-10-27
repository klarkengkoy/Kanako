package com.smyddevstudio.kanako.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object KanakoOverlaySession {
    private val mutableIsActive = MutableStateFlow(false)
    val isActive = mutableIsActive.asStateFlow()

    fun setActive(active: Boolean) {
        mutableIsActive.value = active
    }
}

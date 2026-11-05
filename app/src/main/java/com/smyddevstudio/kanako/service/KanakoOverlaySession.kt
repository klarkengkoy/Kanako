package com.smyddevstudio.kanako.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object KanakoOverlaySession {
    private val mutableIsActive = MutableStateFlow(false)
    val isActive = mutableIsActive.asStateFlow()
    private val mutablePhotoReaderVisible = MutableStateFlow(false)
    val photoReaderVisible = mutablePhotoReaderVisible.asStateFlow()

    fun setPhotoReaderVisible(visible: Boolean) {
        mutablePhotoReaderVisible.value = visible
    }

    fun setActive(active: Boolean) {
        mutableIsActive.value = active
    }
}

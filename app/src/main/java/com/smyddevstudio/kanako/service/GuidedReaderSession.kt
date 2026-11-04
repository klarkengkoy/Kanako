package com.smyddevstudio.kanako.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GuidedReaderStep { INACTIVE, SHOW, READ, CONTROLS, SCRIPT, CLEAR, DRAG, PREFERENCES, RETURN, STOP, COMPLETE }

internal object GuidedReaderSession {
    private val mutableStep = MutableStateFlow(GuidedReaderStep.INACTIVE)
    val step = mutableStep.asStateFlow()
    var sampleVisible = false
        private set
    val active: Boolean get() = step.value != GuidedReaderStep.INACTIVE
    fun begin() { mutableStep.value = GuidedReaderStep.SHOW }
    fun setSampleVisible(visible: Boolean) { sampleVisible = visible }
    fun advance(expected: GuidedReaderStep, next: GuidedReaderStep) {
        if (sampleVisible && step.value == expected) mutableStep.value = next
    }
    fun openPreferencesFromHome() {
        if (step.value == GuidedReaderStep.PREFERENCES) mutableStep.value = GuidedReaderStep.RETURN
    }
    fun returnFromPreferences() {
        if (step.value == GuidedReaderStep.RETURN) mutableStep.value = GuidedReaderStep.STOP
    }
    fun end() {
        sampleVisible = false
        mutableStep.value = GuidedReaderStep.INACTIVE
    }
    fun allowsSource(isOwnPackage: Boolean): Boolean =
        if (!active) !isOwnPackage else sampleVisible && isOwnPackage &&
            step.value == GuidedReaderStep.READ
}

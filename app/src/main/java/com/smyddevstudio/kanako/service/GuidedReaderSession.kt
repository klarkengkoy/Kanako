package com.smyddevstudio.kanako.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class GuidedReaderState(
    val active: Boolean = false,
    val sampleVisible: Boolean = false,
    val sampleBounds: ReadingBounds? = null,
    val highlightPractice: Boolean = true,
    val automaticPractice: Boolean = false,
    val sampleScrolling: Boolean = false,
    val sampleText: String = "",
    val sampleSpans: List<RecognizedTextSpan> = emptyList()
)

internal object GuidedReaderSession {
    private val mutableState = MutableStateFlow(GuidedReaderState())
    val state = mutableState.asStateFlow()
    private val mutableActions = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val actions = mutableActions.asSharedFlow()
    fun action(action: String) { if (active) mutableActions.tryEmit(action) }
    fun setSampleText(text: String, spans: List<RecognizedTextSpan>) {
        mutableState.value = state.value.copy(sampleText = text, sampleSpans = spans)
    }
    fun alignSample(line: RecognizedTextLine): RecognizedTextLine {
        val sample = state.value
        val spans = tutorialSampleSpans(sample.sampleText, sample.sampleSpans, line.text) ?: return line
        val bounds = ReadingBounds(spans.minOf { it.bounds.left }, spans.minOf { it.bounds.top },
            spans.maxOf { it.bounds.right }, spans.maxOf { it.bounds.bottom })
        return line.copy(bounds = android.graphics.Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            spans = spans)
    }
    val active: Boolean get() = state.value.active
    val sampleVisible: Boolean get() = state.value.sampleVisible

    fun begin() { mutableState.value = state.value.copy(active = true) }
    fun setSampleVisible(visible: Boolean) {
        mutableState.value = state.value.copy(sampleVisible = visible)
    }
    fun setSampleBounds(bounds: ReadingBounds) {
        mutableState.value = state.value.copy(sampleBounds = bounds.takeIf {
            it.right > it.left && it.bottom > it.top
        })
    }
    fun setHighlightPractice(enabled: Boolean) {
        mutableState.value = state.value.copy(highlightPractice = enabled)
    }
    fun setAutomaticPractice(enabled: Boolean) {
        mutableState.value = state.value.copy(automaticPractice = enabled, sampleScrolling = false)
    }
    fun setSampleScrolling(scrolling: Boolean) {
        mutableState.value = state.value.copy(sampleScrolling = scrolling)
    }
    // This only permits the visible lesson sample. The persisted opt-in and capture guards still apply.
    val allowsAutomaticRead: Boolean get() = state.value.let {
        it.active && it.automaticPractice && it.sampleVisible && it.sampleBounds != null && !it.sampleScrolling
    }
    fun end() { mutableState.value = GuidedReaderState() }
    fun allowsSource(isOwnPackage: Boolean): Boolean =
        if (active) sampleVisible && state.value.sampleBounds != null && isOwnPackage else !isOwnPackage
}

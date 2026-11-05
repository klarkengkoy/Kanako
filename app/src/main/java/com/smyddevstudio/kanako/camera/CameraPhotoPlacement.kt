package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.RubyRequest
import com.smyddevstudio.kanako.service.placeRubyReadings

internal data class PhotoRubyRequest(val anchor: ReadingBounds, val vertical: Boolean = false)

internal data class PhotoRubyMeasurement<T>(
    val width: Int,
    val height: Int,
    val gap: Int,
    val content: T
)

internal data class PhotoRubyPlacement<T>(
    val bounds: ReadingBounds,
    val fontPixels: Float,
    val measurement: PhotoRubyMeasurement<T>
)

internal fun <T> placeCameraPhotoReadings(
    requests: List<PhotoRubyRequest>,
    clip: ReadingBounds,
    sourceText: List<ReadingBounds>,
    minimumFontPixels: Float,
    measure: (Int, Float) -> PhotoRubyMeasurement<T>?,
    checkCancellation: () -> Unit = {}
): List<PhotoRubyPlacement<T>?> {
    if (clip.isEmpty || !minimumFontPixels.isFinite() || minimumFontPixels <= 0f) {
        return List(requests.size) { null }
    }
    fun measured(index: Int, font: Float): PhotoRubyPlacement<T>? {
        checkCancellation()
        val request = requests[index]
        if (request.anchor.isEmpty || !clip.contains(request.anchor)) return null
        val size = measure(index, font) ?: return null
        val bounds = placeRubyReadings(
            listOf(RubyRequest(request.anchor, clip, size.width, size.height, request.vertical)), size.gap
        ).single() ?: return null
        if (sourceText.any { it.intersection(bounds) != null }) return null
        return PhotoRubyPlacement(bounds, font, size)
    }
    val minimum = requests.indices.map { measured(it, minimumFontPixels) }
    val result = MutableList<PhotoRubyPlacement<T>?>(requests.size) { null }
    fun fits(index: Int, candidate: PhotoRubyPlacement<T>): Boolean =
        minimum.indices.none { other ->
            other != index && (minimum[other]?.bounds?.intersection(candidate.bounds) != null ||
                result[other]?.bounds?.intersection(candidate.bounds) != null)
        }
    requests.forEachIndexed { index, request ->
        checkCancellation()
        var best = minimum[index]?.takeIf { fits(index, it) } ?: return@forEachIndexed
        val sourceSize = if (request.vertical) request.anchor.right - request.anchor.left
            else request.anchor.bottom - request.anchor.top
        val natural = maxOf(minimumFontPixels, sourceSize * 0.5f)
        if (natural > minimumFontPixels) {
            val grown = measured(index, natural)
            if (grown != null && fits(index, grown)) best = grown
            else {
                var low = minimumFontPixels
                var high = natural
                repeat(8) {
                    val font = (low + high) / 2f
                    val candidate = measured(index, font)
                    if (candidate != null && fits(index, candidate)) {
                        best = candidate
                        low = font
                    } else high = font
                }
            }
        }
        result[index] = best
    }
    return result
}

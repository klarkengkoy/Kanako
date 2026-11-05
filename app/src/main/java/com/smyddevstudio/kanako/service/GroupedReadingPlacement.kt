package com.smyddevstudio.kanako.service

internal data class GroupedRubyRequest(val anchor: ReadingBounds, val vertical: Boolean = false, val clip: ReadingBounds? = null)

internal data class GroupedRubyMeasurement<T>(
    val width: Int,
    val height: Int,
    val gap: Int,
    val content: T
)

internal data class GroupedRubyPlacement<T>(
    val bounds: ReadingBounds,
    val fontPixels: Float,
    val measurement: GroupedRubyMeasurement<T>
)

internal fun <T> placeGroupedReadings(
    requests: List<GroupedRubyRequest>,
    clip: ReadingBounds,
    sourceText: List<ReadingBounds>,
    minimumFontPixels: Float,
    measure: (Int, Float) -> GroupedRubyMeasurement<T>?,
    checkCancellation: () -> Unit = {}
): List<GroupedRubyPlacement<T>?> {
    if (clip.isEmpty || !minimumFontPixels.isFinite() || minimumFontPixels <= 0f) {
        return List(requests.size) { null }
    }
    fun measured(index: Int, font: Float): GroupedRubyPlacement<T>? {
        checkCancellation()
        val request = requests[index]
        val viewport = request.clip?.intersection(clip) ?: if (request.clip == null) clip else return null
        if (request.anchor.isEmpty || !viewport.contains(request.anchor)) return null
        val size = measure(index, font) ?: return null
        val bounds = placeRubyReadings(
            listOf(RubyRequest(request.anchor, viewport, size.width, size.height, request.vertical)), size.gap
        ).single() ?: return null
        if (sourceText.any { it.intersection(bounds) != null }) return null
        return GroupedRubyPlacement(bounds, font, size)
    }
    val minimum = requests.indices.map { measured(it, minimumFontPixels) }
    val result = MutableList<GroupedRubyPlacement<T>?>(requests.size) { null }
    fun fits(index: Int, candidate: GroupedRubyPlacement<T>): Boolean =
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

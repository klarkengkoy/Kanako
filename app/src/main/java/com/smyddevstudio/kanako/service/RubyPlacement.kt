package com.smyddevstudio.kanako.service

internal data class RubyRequest(
    val anchor: ReadingBounds,
    val clip: ReadingBounds,
    val width: Int,
    val height: Int,
    val vertical: Boolean = false
)

internal fun placeRubyReadings(requests: List<RubyRequest>, gap: Int): List<ReadingBounds?> =
    requests.map { request ->
        val clip = request.clip
        if (request.width <= 0 || request.height <= 0 ||
            request.width > clip.right - clip.left || request.height > clip.bottom - clip.top
        ) return@map null
        val anchor = request.anchor
        val preferredX = if (request.vertical) anchor.right + gap
            else (anchor.left + anchor.right - request.width) / 2
        val preferredY = if (request.vertical) (anchor.top + anchor.bottom - request.height) / 2
            else anchor.top - gap - request.height
        val x = if (request.vertical && preferredX + request.width > clip.right)
            anchor.left - gap - request.width else preferredX
        val y = if (!request.vertical && preferredY < clip.top) anchor.bottom + gap else preferredY
        val left = x.coerceIn(clip.left, clip.right - request.width)
        val top = y.coerceIn(clip.top, clip.bottom - request.height)
        ReadingBounds(left, top, left + request.width, top + request.height)
    }

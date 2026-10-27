package com.smyddevstudio.kanako.camera

import kotlin.math.abs
import kotlin.math.max

internal const val CAMERA_LIVE_RESULT_MAX_AGE_MS = 650L
internal const val CAMERA_TEXT_UPDATE_INTERVAL_MS = 700L

// Content and geometry have separate clocks. Exact text matches can move on every fresh OCR frame;
// new content needs two consecutive observations and cannot replace content more often than 700 ms.
internal class CameraLiveReadings {
    private var lastFrame = Long.MIN_VALUE
    private var lastContentChange: Long? = null
    private var content = emptyList<CameraReadingLine>()
    private data class Observation(val line: CameraReadingLine, val count: Int)
    private var candidates = emptyList<Observation>()

    fun reset() {
        lastFrame = Long.MIN_VALUE
        lastContentChange = null
        clearContent()
    }

    private fun clearContent() {
        content = emptyList()
        candidates = emptyList()
    }

    // null means rejected, while an empty list is a fresh observation with no safe reading to show.
    fun update(started: Long, now: Long, detected: List<CameraReadingLine>): List<CameraReadingLine>? {
        if (now < started || now - started > CAMERA_LIVE_RESULT_MAX_AGE_MS || started <= lastFrame) return null
        if (lastFrame != Long.MIN_VALUE && started - lastFrame > CAMERA_LIVE_RESULT_MAX_AGE_MS) clearContent()
        lastFrame = started
        val previousCandidates = candidates.toMutableList()
        candidates = detected.map { line ->
            val previous = previousCandidates.filter { matches(it.line, line) }
                .minByOrNull { distance(it.line.bounds, line.bounds) }
            previousCandidates.remove(previous)
            Observation(line, ((previous?.count ?: 0) + 1).coerceAtMost(2))
        }
        val confirmed = candidates.filter { it.count >= 2 }.map { it.line }
        // Ignore OCR ordering changes, but allow confirmed text to be reacquired after a large move.
        val unmatched = content.toMutableList()
        val sameContent = confirmed.size == content.size && confirmed.all { takeMatch(unmatched, it) != null }
        val lastChange = lastContentChange
        if (confirmed.isNotEmpty() && (lastChange == null || now - lastChange >= CAMERA_TEXT_UPDATE_INTERVAL_MS) &&
            !sameContent
        ) {
            content = confirmed
            lastContentChange = now
        }

        val available = detected.toMutableList()
        val visible = mutableListOf<CameraReadingLine>()
        content = content.map { previous ->
            // One-to-one nearest matching prevents duplicate lines from sharing a box. A missing or
            // changed line is hidden immediately, rather than leaving old readings over new kanji.
            val match = takeMatch(available, previous)
            if (match == null) previous else {
                previous.copy(bounds = match.bounds, spans = match.spans).also { visible.add(it) }
            }
        }
        return visible
    }

    private fun takeMatch(available: MutableList<CameraReadingLine>, line: CameraReadingLine): CameraReadingLine? {
        val match = available.filter { matches(line, it) }.minByOrNull { distance(line.bounds, it.bounds) }
        available.remove(match)
        return match
    }

    private fun matches(a: CameraReadingLine, b: CameraReadingLine) =
        a.text == b.text && closeEnough(a.bounds, b.bounds)

    private fun distance(a: CameraRect, b: CameraRect): Float =
        abs(a.left + a.right - b.left - b.right) + abs(a.top + a.bottom - b.top - b.bottom)

    private fun closeEnough(a: CameraRect, b: CameraRect): Boolean {
        val vertical = a.height > a.width
        val crossAxis = if (vertical) max(a.width, b.width) else max(a.height, b.height)
        val alongAxis = if (vertical) max(a.height, b.height) else max(a.width, b.width)
        val dx = abs(a.left + a.right - b.left - b.right) / 2
        val dy = abs(a.top + a.bottom - b.top - b.bottom) / 2
        return if (vertical) dx <= crossAxis * 3 && dy <= alongAxis
            else dx <= alongAxis && dy <= crossAxis * 3
    }
}

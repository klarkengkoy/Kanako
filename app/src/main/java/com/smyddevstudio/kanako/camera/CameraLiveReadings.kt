package com.smyddevstudio.kanako.camera

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val CAMERA_LIVE_RESULT_MAX_AGE_MS = 650L
// Measured recognition cycles approach 600 ms. Leave time for tracking/layout before the next result.
internal const val CAMERA_LIVE_DISPLAY_HOLD_MS = 800L
internal const val CAMERA_LIVE_TRACK_MAX_SOURCE_AGE_MS = CAMERA_LIVE_RESULT_MAX_AGE_MS + CAMERA_LIVE_DISPLAY_HOLD_MS
internal const val CAMERA_TEXT_UPDATE_INTERVAL_MS = 700L
internal const val CAMERA_LINE_MISS_GRACE_MS = 400L

internal class CameraLiveReadings {
    private data class Observation(val line: CameraReadingLine, val count: Int)
    private data class Retained(
        val line: CameraReadingLine,
        val raw: CameraReadingLine,
        val seenAt: Long,
        val visibleUntil: Long,
        val misses: Int = 0
    )

    private var nextKey = 0L
    private var lastFrame = Long.MIN_VALUE
    private var lastContentChange: Long? = null
    private var content = emptyList<Retained>()
    private var candidates = emptyList<Observation>()

    val nextExpiryAt: Long?
        get() = content.filter { it.visibleUntil != Long.MIN_VALUE }.minOfOrNull { it.visibleUntil }

    fun reset() {
        lastFrame = Long.MIN_VALUE
        lastContentChange = null
        clearContent()
    }

    private fun clearContent() {
        content = emptyList()
        candidates = emptyList()
    }

    fun expire(now: Long): List<CameraReadingLine> {
        content = content.map { if (it.visibleUntil <= now) it.copy(visibleUntil = Long.MIN_VALUE) else it }
        return visibleLines()
    }

    // Geometry follows fresh results; accepting different text still requires two observations.
    fun update(started: Long, now: Long, detected: List<CameraReadingLine>): List<CameraReadingLine>? {
        if (now < started || now - started > CAMERA_LIVE_RESULT_MAX_AGE_MS || started <= lastFrame) {
            return null
        }
        if (lastFrame != Long.MIN_VALUE && started - lastFrame > CAMERA_LIVE_RESULT_MAX_AGE_MS) {
            // Unconfirmed observations must be consecutive, but a still-visible confirmed track
            // may bridge a slow cycle without losing its identity.
            candidates = emptyList()
            content = content.filter { it.visibleUntil > now }
        }
        lastFrame = started
        val observed = cameraReadingGroups(detected)
        val previousCandidates = candidates.toMutableList()
        candidates = observed.map { line ->
            val previous = previousCandidates.filter { matches(it.line, line) }
                .minByOrNull { distance(it.line.bounds, line.bounds) }
            previousCandidates.remove(previous)
            Observation(line, ((previous?.count ?: 0) + 1).coerceAtMost(2))
        }
        val available = observed.toMutableList()
        val matches = content.map { takeMatch(available, it.raw) }
        // A blank/changed scene or motion cannot justify keeping an absent line at its old position.
        val stableScene = available.isEmpty() && matches.any { it != null } &&
            matches.withIndex().all { (index, match) -> match == null || smallMovement(content[index].raw, match) }
        fun keepMissing(old: Retained): Boolean =
            stableScene && old.misses == 0 && old.visibleUntil > now && now - old.seenAt < CAMERA_LINE_MISS_GRACE_MS
        fun held(old: Retained) = old.copy(
            // An omitted word never renews its display lease.
            visibleUntil = min(old.visibleUntil, old.seenAt + CAMERA_LINE_MISS_GRACE_MS), misses = old.misses + 1
        )
        fun fresh(line: CameraReadingLine, old: Retained?) = Retained(
            line.copy(trackingKey = if (old != null && old.visibleUntil > now) old.line.trackingKey else "live:${++nextKey}"),
            line, now,
            min(now + CAMERA_LIVE_DISPLAY_HOLD_MS, started + CAMERA_LIVE_TRACK_MAX_SOURCE_AGE_MS)
        )

        content = content.mapIndexed { index, old ->
            matches[index]?.let { fresh(it, old) }
                ?: if (keepMissing(old)) held(old) else old.copy(visibleUntil = Long.MIN_VALUE)
        }
        val additions = candidates.filter { it.count >= 2 }.mapNotNull { takeMatch(available, it.line) }
        val lastChange = lastContentChange
        val publish = additions.isNotEmpty() && (lastChange == null || now - lastChange >= CAMERA_TEXT_UPDATE_INTERVAL_MS)
        if (publish) {
            content = content.filter { it.visibleUntil > now } + additions.map { fresh(it, null) }
            lastContentChange = now
        }
        return visibleLines()
    }

    private fun visibleLines() = content.filter { it.visibleUntil != Long.MIN_VALUE }.map { it.line }

    private fun takeMatch(available: MutableList<CameraReadingLine>, line: CameraReadingLine): CameraReadingLine? {
        val match = available.filter { matches(line, it) }.minByOrNull { distance(line.bounds, it.bounds) }
        available.remove(match)
        return match
    }

    private fun matches(a: CameraReadingLine, b: CameraReadingLine) =
        a.text == b.text && a.segments == b.segments && a.isVertical == b.isVertical && closeEnough(a.bounds, b.bounds, a.isVertical)

    private fun distance(a: CameraRect, b: CameraRect): Float =
        abs(a.left + a.right - b.left - b.right) + abs(a.top + a.bottom - b.top - b.bottom)

    private fun smallMovement(a: CameraReadingLine, b: CameraReadingLine): Boolean {
        val limit = max(1f, min(min(a.bounds.width, a.bounds.height), min(b.bounds.width, b.bounds.height)) * 0.2f)
        return abs(a.bounds.left - b.bounds.left) <= limit && abs(a.bounds.top - b.bounds.top) <= limit &&
            abs(a.bounds.right - b.bounds.right) <= limit && abs(a.bounds.bottom - b.bounds.bottom) <= limit
    }

    private fun closeEnough(a: CameraRect, b: CameraRect, vertical: Boolean): Boolean {
        val crossAxis = if (vertical) max(a.width, b.width) else max(a.height, b.height)
        val alongAxis = if (vertical) max(a.height, b.height) else max(a.width, b.width)
        val dx = abs(a.left + a.right - b.left - b.right) / 2
        val dy = abs(a.top + a.bottom - b.top - b.bottom) / 2
        return if (vertical) dx <= crossAxis * 3 && dy <= alongAxis
            else dx <= alongAxis && dy <= crossAxis * 3
    }
}

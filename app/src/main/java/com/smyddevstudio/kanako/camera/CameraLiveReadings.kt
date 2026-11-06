package com.smyddevstudio.kanako.camera

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val CAMERA_LIVE_RESULT_MAX_AGE_MS = 650L
internal const val CAMERA_TEXT_UPDATE_INTERVAL_MS = 700L
internal const val CAMERA_LINE_MISS_GRACE_MS = 200L

internal class CameraLiveReadings {
    private data class Observation(val line: CameraReadingLine, val count: Int)
    private data class Retained(
        val line: CameraReadingLine,
        val raw: CameraReadingLine,
        val sourceStarted: Long,
        val seenAt: Long,
        val visibleUntil: Long
    )

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
        val available = detected.toMutableList()
        val matches = content.map { takeMatch(available, it.raw) }
        // A blank/changed scene or motion cannot justify keeping an absent line at its old position.
        val stableScene = available.isEmpty() && matches.any { it != null } &&
            matches.withIndex().all { (index, match) -> match == null || smallMovement(content[index].raw, match) }
        fun keepMissing(old: Retained): Boolean =
            stableScene && old.visibleUntil > now && now - old.seenAt < CAMERA_LINE_MISS_GRACE_MS &&
                now - old.sourceStarted < CAMERA_LIVE_RESULT_MAX_AGE_MS
        fun held(old: Retained) = old.copy(
            visibleUntil = min(old.sourceStarted + CAMERA_LIVE_RESULT_MAX_AGE_MS, old.seenAt + CAMERA_LINE_MISS_GRACE_MS)
        )
        fun fresh(line: CameraReadingLine, old: Retained?) = Retained(
            if (old != null && old.visibleUntil > now && smallMovement(old.raw, line)) smooth(old.line, line) else line,
            line, started, now, started + CAMERA_LIVE_RESULT_MAX_AGE_MS
        )

        val confirmed = candidates.filter { it.count >= 2 }.map { it.line }
        val unmatched = content.map { it.raw }.toMutableList()
        val sameContent = confirmed.size == content.size && confirmed.all { takeMatch(unmatched, it) != null }
        val lastChange = lastContentChange
        if (confirmed.isNotEmpty() && (lastChange == null || now - lastChange >= CAMERA_TEXT_UPDATE_INTERVAL_MS) &&
            !sameContent
        ) {
            val previous = content.toMutableList()
            val next = confirmed.map { line ->
                val old = previous.filter { matches(it.raw, line) }.minByOrNull { distance(it.raw.bounds, line.bounds) }
                previous.remove(old)
                fresh(line, old)
            }
            content = next + previous.filter(::keepMissing).map(::held)
            lastContentChange = now
        } else {
            content = content.mapIndexed { index, old ->
                matches[index]?.let { fresh(it, old) }
                    ?: if (keepMissing(old)) held(old) else old.copy(visibleUntil = Long.MIN_VALUE)
            }
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
        a.text == b.text && a.segments == b.segments && closeEnough(a.bounds, b.bounds)

    private fun distance(a: CameraRect, b: CameraRect): Float =
        abs(a.left + a.right - b.left - b.right) + abs(a.top + a.bottom - b.top - b.bottom)

    private fun smallMovement(a: CameraReadingLine, b: CameraReadingLine): Boolean {
        val limit = max(1f, min(min(a.bounds.width, a.bounds.height), min(b.bounds.width, b.bounds.height)) * 0.2f)
        return abs(a.bounds.left - b.bounds.left) <= limit && abs(a.bounds.top - b.bounds.top) <= limit &&
            abs(a.bounds.right - b.bounds.right) <= limit && abs(a.bounds.bottom - b.bounds.bottom) <= limit
    }

    private fun smooth(previous: CameraReadingLine, current: CameraReadingLine): CameraReadingLine {
        if (previous.spans.map { it.start to it.end } != current.spans.map { it.start to it.end }) return current
        fun rect(a: CameraRect, b: CameraRect) = CameraRect(
            a.left + (b.left - a.left) * 0.4f, a.top + (b.top - a.top) * 0.4f,
            a.right + (b.right - a.right) * 0.4f, a.bottom + (b.bottom - a.bottom) * 0.4f
        )
        return current.copy(bounds = rect(previous.bounds, current.bounds),
            spans = current.spans.mapIndexed { index, span ->
                val old = previous.spans[index]
                val corners = if (old.corners.size == span.corners.size) span.corners.mapIndexed { pointIndex, point ->
                    val before = old.corners[pointIndex]
                    CameraPoint(before.x + (point.x - before.x) * 0.4f, before.y + (point.y - before.y) * 0.4f)
                } else span.corners
                span.copy(bounds = rect(old.bounds, span.bounds), corners = corners)
            })
    }

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

package com.smyddevstudio.kanako.service

internal class ScreenUpdateDispatcher {
    private val listeners = mutableSetOf<(ScreenUpdate) -> Unit>()

    fun subscribe(listener: (ScreenUpdate) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun publish(update: ScreenUpdate) {
        listeners.toList().forEach { it(update) }
    }
}

internal class ScrollContentCorrelation(private val graceMillis: Long = 150) {
    private var lastMovement: ScrollMovement? = null
    private var lastScrollTime = Long.MIN_VALUE

    fun onScroll(movement: ScrollMovement?, time: Long) {
        lastMovement = movement
        lastScrollTime = time
    }

    fun matches(windowId: Int, bounds: ReadingBounds?, time: Long): Boolean {
        val movement = lastMovement ?: return false
        return bounds != null && windowId == movement.windowId &&
            time >= lastScrollTime && time - lastScrollTime <= graceMillis &&
            movement.viewport.contains(bounds)
    }

    fun clear() { lastMovement = null }
}

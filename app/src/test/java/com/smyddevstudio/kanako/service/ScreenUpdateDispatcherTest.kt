package com.smyddevstudio.kanako.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenUpdateDispatcherTest {
    @Test
    fun everyScrollReachesTheReaderInOrderAndUnsubscribeStopsDelivery() {
        val dispatcher = ScreenUpdateDispatcher()
        val received = mutableListOf<Long>()
        val unsubscribe = dispatcher.subscribe { received.add(it.revision) }
        repeat(1000) { dispatcher.publish(ScreenUpdate(revision = it.toLong())) }
        assertEquals((0L..999L).toList(), received)
        unsubscribe()
        dispatcher.publish(ScreenUpdate(revision = 1000))
        assertEquals(1000, received.size)
    }

    @Test
    fun onlyChangesInsideTheRecentScrollViewportAreCorrelated() {
        val correlation = ScrollContentCorrelation()
        correlation.onScroll(ScrollMovement(1, ReadingBounds(0, 100, 400, 800), 0, 50), 1000)
        assertTrue(correlation.matches(1, ReadingBounds(10, 200, 100, 300), 1100))
        assertFalse(correlation.matches(2, ReadingBounds(10, 200, 100, 300), 1100))
        assertFalse(correlation.matches(1, ReadingBounds(0, 0, 400, 800), 1100))
        assertFalse(correlation.matches(1, ReadingBounds(10, 200, 100, 300), 1200))
        correlation.clear()
        assertFalse(correlation.matches(1, ReadingBounds(10, 200, 100, 300), 1100))
    }

    @Test
    fun unknownScrollMovementNeverHidesAContentChange() {
        val correlation = ScrollContentCorrelation()
        correlation.onScroll(null, 1000)
        assertFalse(correlation.matches(1, ReadingBounds(0, 100, 400, 800), 1000))
    }
}

package com.smyddevstudio.kanako.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoReadDebouncerTest {
    @Test
    fun defaultModeNeverCapturesAutomatically() = runTest {
        var captures = 0
        val reader = AutoReadDebouncer(this, { captures++ })
        reader.onMovement(true)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(0, captures)
    }

    @Test
    fun movementRestartsTheSettlingDelayAndTriggersOnlyOnce() = runTest {
        var captures = 0
        val reader = AutoReadDebouncer(this, { captures++ }).apply { enabled = true }
        reader.onMovement(true)
        advanceTimeBy(500)
        reader.onMovement(true)
        advanceTimeBy(699)
        runCurrent()
        assertEquals(0, captures)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, captures)
        advanceTimeBy(5000)
        assertEquals(1, captures)
    }

    @Test
    fun sensitiveInputOrStopCancelsPendingCapture() = runTest {
        var captures = 0
        val reader = AutoReadDebouncer(this, { captures++ }).apply { enabled = true }
        reader.onMovement(true)
        advanceTimeBy(300)
        reader.onMovement(false)
        advanceTimeBy(2000)
        assertEquals(0, captures)
        reader.onMovement(true)
        reader.cancel()
        advanceTimeBy(2000)
        assertEquals(0, captures)
    }

    @Test
    fun disablingAutomaticModeCancelsPendingCapture() = runTest {
        var captures = 0
        val reader = AutoReadDebouncer(this, { captures++ }).apply { enabled = true }
        reader.onMovement(true)
        advanceTimeBy(300)
        reader.enabled = false
        advanceTimeBy(2000)
        assertEquals(0, captures)
    }
}

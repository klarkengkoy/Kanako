package com.smyddevstudio.kanako.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotRequestGateTest {
    @Test
    fun scrollOrAppChangeRejectsAnInFlightResult() {
        val gate = SnapshotRequestGate()
        val request = gate.begin()
        assertTrue(gate.isCurrent(request))
        gate.invalidate()
        assertFalse(gate.isCurrent(request))
    }

    @Test
    fun aNewTapCannotPublishAnOlderReading() {
        val gate = SnapshotRequestGate()
        val old = gate.begin()
        val current = gate.begin()
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(current))
    }

    @Test
    fun sensitiveInputOrStopInvalidatesAllPriorRequests() {
        val gate = SnapshotRequestGate()
        val old = gate.begin()
        gate.invalidate()
        val next = gate.begin()
        gate.invalidate()
        assertFalse(gate.isCurrent(old))
        assertFalse(gate.isCurrent(next))
    }
}

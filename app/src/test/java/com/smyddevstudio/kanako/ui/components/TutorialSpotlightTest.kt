package com.smyddevstudio.kanako.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialSpotlightTest {
    @Test fun overlappingTargetsLeaveOnlyDisjointBlockedAreas() {
        val viewport = Rect(0f, 0f, 100f, 100f)
        val targets = listOf(Rect(20f, 20f, 60f, 60f), Rect(40f, 40f, 80f, 80f))
        val blocked = targets.fold(listOf(viewport)) { areas, target ->
            areas.flatMap { subtractTutorialTarget(it, target) }
        }
        assertEquals(7200f, blocked.sumOf { (it.width * it.height).toDouble() }.toFloat(), 0.01f)
        blocked.forEachIndexed { index, area ->
            assertTrue(targets.all { area.intersect(it).isEmpty })
            assertTrue(blocked.drop(index + 1).all { area.intersect(it).isEmpty })
        }
    }

    @Test fun outsideTargetDoesNotUnblockViewport() {
        val viewport = Rect(0f, 0f, 100f, 100f)
        assertEquals(listOf(viewport), subtractTutorialTarget(viewport, Rect(110f, 0f, 150f, 50f)))
    }

    @Test fun targetCoveringViewportLeavesNoBlockers() {
        assertTrue(subtractTutorialTarget(Rect(0f, 0f, 100f, 100f), Rect(-10f, -10f, 110f, 110f)).isEmpty())
    }
}

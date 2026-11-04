package com.smyddevstudio.kanako.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingMascotMotionTest {
    @Test fun entryCompletesBeforeAnAutomaticReadingAnimation() {
        val entering = FloatingMascotMotion().update(true, true, true)
        assertEquals(MascotPhase.CREEPING, entering.phase)
        assertEquals(MascotPhase.LOADING_START, entering.finished(MascotPhase.CREEPING).phase)
    }

    @Test fun slowReadingUsesStartLoopEndAndRest() {
        var motion = FloatingMascotMotion(MascotPhase.RESTING).update(true, true, true)
        assertEquals(MascotPhase.LOADING_START, motion.phase)
        motion = motion.finished(MascotPhase.LOADING_START)
        assertEquals(MascotPhase.LOADING_LOOP, motion.phase)
        motion = motion.update(false, true, true)
        assertEquals(MascotPhase.LOADING_LOOP, motion.phase)
        motion = motion.finished(MascotPhase.LOADING_LOOP)
        assertEquals(MascotPhase.LOADING_END, motion.phase)
        assertEquals(MascotPhase.RESTING, motion.finished(MascotPhase.LOADING_END).phase)
    }

    @Test fun shortReadingFinishesOneWholeLoopBeforeClosing() {
        val motion = FloatingMascotMotion(MascotPhase.RESTING).update(true, true, true)
            .update(false, true, true)
        assertEquals(MascotPhase.LOADING_START, motion.phase)
        val looping = motion.finished(MascotPhase.LOADING_START)
        assertEquals(MascotPhase.LOADING_LOOP, looping.phase)
        val ending = looping.finished(MascotPhase.LOADING_LOOP)
        assertEquals(MascotPhase.LOADING_END, ending.phase)
        assertEquals(MascotPhase.RESTING, ending.finished(MascotPhase.LOADING_END).phase)
    }

    @Test fun anotherReadingDuringTheOutroWaitsForTheOutro() {
        val motion = FloatingMascotMotion(MascotPhase.LOADING_END).update(true, true, true)
        assertEquals(MascotPhase.LOADING_END, motion.phase)
        assertEquals(MascotPhase.LOADING_START, motion.finished(MascotPhase.LOADING_END).phase)
    }

    @Test fun resumedReadingCanContinueWhenALoopCompletionWasAlreadyQueued() {
        val motion = FloatingMascotMotion(MascotPhase.LOADING_LOOP, true)
            .update(false, true, true).update(true, true, true)
        val continued = motion.finished(MascotPhase.LOADING_LOOP)
        assertEquals(MascotPhase.LOADING_LOOP, continued.phase)
        assertEquals(motion.loopPass + 1, continued.loopPass)
    }

    @Test fun draggingOverridesAnimationsAndReleaseReturnsToRest() {
        val motion = FloatingMascotMotion(MascotPhase.LOADING_LOOP, true).startDrag()
        assertEquals(MascotPhase.DRAGGING, motion.phase)
        assertEquals(MascotPhase.DRAGGING, motion.update(false, true, true).phase)
        assertEquals(MascotPhase.RESTING, motion.endDrag().phase)
        assertEquals(motion, motion.finished(MascotPhase.LOADING_START))
    }

    @Test fun hiddenOrReducedMotionStatesCancelTheSequence() {
        val motion = FloatingMascotMotion(MascotPhase.LOADING_START, true)
        assertEquals(MascotPhase.RESTING, motion.update(true, false, true).phase)
        assertEquals(MascotPhase.RESTING, motion.update(true, true, false).phase)
    }
}

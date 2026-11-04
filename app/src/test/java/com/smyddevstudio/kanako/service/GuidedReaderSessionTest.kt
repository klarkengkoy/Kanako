package com.smyddevstudio.kanako.service

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GuidedReaderSessionTest {
    @After fun reset() = GuidedReaderSession.end()

    @Test fun preferencesReturnResumesOnlyThePreferencesLesson() {
        GuidedReaderSession.begin()
        GuidedReaderSession.returnFromPreferences()
        GuidedReaderSession.openPreferencesFromHome()
        assertEquals(GuidedReaderStep.SHOW, GuidedReaderSession.step.value)
        GuidedReaderSession.setSampleVisible(true)
        GuidedReaderSession.advance(GuidedReaderStep.SHOW, GuidedReaderStep.READ)
        GuidedReaderSession.advance(GuidedReaderStep.READ, GuidedReaderStep.CONTROLS)
        GuidedReaderSession.advance(GuidedReaderStep.CONTROLS, GuidedReaderStep.SCRIPT)
        GuidedReaderSession.advance(GuidedReaderStep.SCRIPT, GuidedReaderStep.CLEAR)
        GuidedReaderSession.advance(GuidedReaderStep.CLEAR, GuidedReaderStep.DRAG)
        GuidedReaderSession.advance(GuidedReaderStep.DRAG, GuidedReaderStep.PREFERENCES)
        GuidedReaderSession.setSampleVisible(false)
        GuidedReaderSession.openPreferencesFromHome()
        assertEquals(GuidedReaderStep.RETURN, GuidedReaderSession.step.value)
        assertFalse(GuidedReaderSession.allowsSource(true))
        GuidedReaderSession.returnFromPreferences()
        assertEquals(GuidedReaderStep.STOP, GuidedReaderSession.step.value)
    }

    @Test fun outsideTheLessonOnlyOtherAppsAreEligible() {
        GuidedReaderSession.end()
        assertFalse(GuidedReaderSession.allowsSource(true))
        assertTrue(GuidedReaderSession.allowsSource(false))
    }

    @Test fun lessonRequiresVisibleSampleAndReadStepAndRejectsOtherApps() {
        GuidedReaderSession.begin()
        assertFalse(GuidedReaderSession.allowsSource(true))
        GuidedReaderSession.setSampleVisible(true)
        assertFalse(GuidedReaderSession.allowsSource(true))
        GuidedReaderSession.advance(GuidedReaderStep.SHOW, GuidedReaderStep.READ)
        assertTrue(GuidedReaderSession.allowsSource(true))
        assertFalse(GuidedReaderSession.allowsSource(false))
        GuidedReaderSession.setSampleVisible(false)
        assertFalse(GuidedReaderSession.allowsSource(true))
        GuidedReaderSession.advance(GuidedReaderStep.READ, GuidedReaderStep.CONTROLS)
        assertEquals(GuidedReaderStep.READ, GuidedReaderSession.step.value)
        GuidedReaderSession.setSampleVisible(true)
        GuidedReaderSession.advance(GuidedReaderStep.READ, GuidedReaderStep.CONTROLS)
        assertFalse(GuidedReaderSession.allowsSource(true))
    }

    @Test fun outOfOrderOrBackgroundEventsCannotFinishTheLesson() {
        GuidedReaderSession.begin()
        GuidedReaderSession.advance(GuidedReaderStep.SHOW, GuidedReaderStep.COMPLETE)
        assertEquals(GuidedReaderStep.SHOW, GuidedReaderSession.step.value)
        GuidedReaderSession.setSampleVisible(true)
        GuidedReaderSession.advance(GuidedReaderStep.STOP, GuidedReaderStep.COMPLETE)
        assertEquals(GuidedReaderStep.SHOW, GuidedReaderSession.step.value)
        GuidedReaderSession.end()
        assertFalse(GuidedReaderSession.active)
        assertFalse(GuidedReaderSession.sampleVisible)
    }
}

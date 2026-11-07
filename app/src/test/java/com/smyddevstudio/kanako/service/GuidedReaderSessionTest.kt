package com.smyddevstudio.kanako.service

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GuidedReaderSessionTest {
    @After fun reset() = GuidedReaderSession.end()

    @Test fun visibleSampleRequiresBoundsAndNeverAllowsOtherApps() {
        GuidedReaderSession.begin()
        GuidedReaderSession.setSampleVisible(true)
        assertFalse(GuidedReaderSession.allowsSource(true))
        GuidedReaderSession.setSampleBounds(ReadingBounds(20, 40, 20, 40))
        assertFalse(GuidedReaderSession.allowsSource(true))
        GuidedReaderSession.setSampleBounds(ReadingBounds(20, 40, 300, 200))
        assertTrue(GuidedReaderSession.allowsSource(true))
        assertFalse(GuidedReaderSession.allowsSource(false))
        GuidedReaderSession.setSampleVisible(false)
        assertFalse(GuidedReaderSession.allowsSource(true))
        assertFalse(GuidedReaderSession.allowsSource(false))
    }

    @Test fun outsideTheLessonOnlyOtherAppsAreEligible() {
        GuidedReaderSession.end()
        assertFalse(GuidedReaderSession.allowsSource(true))
        assertTrue(GuidedReaderSession.allowsSource(false))
    }

    @Test fun restoredVisibleLayoutCanArriveBeforeTheSessionStarts() {
        GuidedReaderSession.setSampleVisible(true)
        GuidedReaderSession.setSampleBounds(ReadingBounds(20, 40, 300, 200))
        assertFalse(GuidedReaderSession.allowsSource(true))
        GuidedReaderSession.begin()
        assertTrue(GuidedReaderSession.allowsSource(true))
        assertFalse(GuidedReaderSession.allowsSource(false))
    }

    @Test fun endingTheLessonDiscardsItsSampleState() {
        GuidedReaderSession.begin()
        GuidedReaderSession.setSampleVisible(true)
        GuidedReaderSession.setSampleBounds(ReadingBounds(20, 40, 300, 200))
        GuidedReaderSession.end()
        assertEquals(GuidedReaderState(), GuidedReaderSession.state.value)
        GuidedReaderSession.begin()
        assertFalse(GuidedReaderSession.allowsSource(true))
    }
    @Test fun automaticPracticeRequiresTheVisibleSampleAndStopsDuringScroll() {
        GuidedReaderSession.begin()
        GuidedReaderSession.setSampleVisible(true)
        GuidedReaderSession.setSampleBounds(ReadingBounds(20, 40, 300, 200))
        assertFalse(GuidedReaderSession.allowsAutomaticRead)
        GuidedReaderSession.setAutomaticPractice(true)
        assertTrue(GuidedReaderSession.allowsAutomaticRead)
        assertFalse(GuidedReaderSession.allowsSource(false))
        GuidedReaderSession.setSampleScrolling(true)
        assertFalse(GuidedReaderSession.allowsAutomaticRead)
        GuidedReaderSession.setSampleScrolling(false)
        assertTrue(GuidedReaderSession.allowsAutomaticRead)
        GuidedReaderSession.setSampleVisible(false)
        assertFalse(GuidedReaderSession.allowsAutomaticRead)
        GuidedReaderSession.setSampleVisible(true)
        GuidedReaderSession.setAutomaticPractice(false)
        assertFalse(GuidedReaderSession.allowsAutomaticRead)
        GuidedReaderSession.end()
        assertFalse(GuidedReaderSession.allowsAutomaticRead)
    }

}

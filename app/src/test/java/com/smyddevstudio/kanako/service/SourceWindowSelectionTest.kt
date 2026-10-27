package com.smyddevstudio.kanako.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceWindowSelectionTest {
    @Test fun overlayActivationNeverOutranksTheFocusedApplication() {
        assertEquals(0, applicationWindowPriority(false, true, true))
        assertTrue(applicationWindowPriority(true, true, false) > applicationWindowPriority(true, false, true))
        assertEquals(0, applicationWindowPriority(true, false, false))
    }

    @Test fun readerUpdatesDoNotInvalidateTheUnchangedSource() {
        assertTrue(isReaderOverlayEvent("kanako", "kanako", 90, "browser", 3))
        assertTrue(isReaderOverlayEvent("kanako", "kanako", 90, null, null))
        assertTrue(isReaderOverlayEvent("kanako", "kanako", 90, "kanako", 4))
    }

    @Test fun openingTheActualAppAndChangesInOtherAppsStillReachInvalidation() {
        assertFalse(isReaderOverlayEvent("kanako", "kanako", 4, "kanako", 4))
        assertFalse(isReaderOverlayEvent("kanako", "browser", 3, "browser", 3))
        assertFalse(isReaderOverlayEvent("kanako", null, 3, "browser", 3))
    }
}

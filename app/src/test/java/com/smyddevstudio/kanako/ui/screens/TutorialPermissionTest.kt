package com.smyddevstudio.kanako.ui.screens

import com.smyddevstudio.kanako.model.TutorialPath
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialPermissionTest {
    @Test fun choosingAReaderCannotBeBypassedWithNext() {
        assertFalse(tutorialCanAdvance(0, TutorialPath.FLOATING, true, true))
        assertFalse(tutorialCanAdvance(0, TutorialPath.CAMERA, true, true))
    }

    @Test fun readerLessonsRequireOnlyTheSelectedReadersAccess() {
        for (step in listOf(-1, 1, 2, 6)) {
            assertFalse(tutorialCanAdvance(step, TutorialPath.FLOATING, false, true))
            assertTrue(tutorialCanAdvance(step, TutorialPath.FLOATING, true, false))
            assertFalse(tutorialCanAdvance(step, TutorialPath.CAMERA, true, false))
            assertTrue(tutorialCanAdvance(step, TutorialPath.CAMERA, false, true))
        }
    }

    @Test fun appearanceAndTileLessonsDoNotRequireReadingPermissions() {
        for (step in listOf(3, 4, 5)) {
            assertTrue(tutorialCanAdvance(step, TutorialPath.FLOATING, false, false))
            assertTrue(tutorialCanAdvance(step, TutorialPath.CAMERA, false, false))
        }
    }
}

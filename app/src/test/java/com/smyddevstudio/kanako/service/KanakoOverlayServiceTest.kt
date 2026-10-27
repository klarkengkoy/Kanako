package com.smyddevstudio.kanako.service

import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanakoOverlayServiceTest {

    @Test
    fun testServiceConstants() {
        assertEquals("kanako_overlay_channel", KanakoOverlayService.CHANNEL_ID)
        assertEquals(1001, KanakoOverlayService.NOTIFICATION_ID)
        assertEquals("com.smyddevstudio.kanako.service.ACTION_START", KanakoOverlayService.ACTION_START_SERVICE)
        assertEquals("com.smyddevstudio.kanako.service.ACTION_STOP", KanakoOverlayService.ACTION_STOP_SERVICE)
    }

    @Test
    fun testOverlayPreferenceState() {
        val inactivePrefs = KanakoPreferences(isOverlayActive = false)
        assertFalse(inactivePrefs.isOverlayActive)

        val activePrefs = KanakoPreferences(isOverlayActive = true, scriptType = ScriptType.KANA)
        assertTrue(activePrefs.isOverlayActive)
        assertEquals(ScriptType.KANA, activePrefs.scriptType)
    }
}

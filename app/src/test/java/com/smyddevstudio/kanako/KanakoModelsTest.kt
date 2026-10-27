package com.smyddevstudio.kanako

import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.FabDocking
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.FuriganaColor
import com.smyddevstudio.kanako.model.FuriganaSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanakoModelsTest {

    @Test
    fun testDefaultKanakoPreferences() {
        val prefs = KanakoPreferences()
        assertEquals(FuriganaSize.MEDIUM, prefs.furiganaSize)
        assertEquals(FuriganaColor.MUSHROOM_CRIMSON, prefs.furiganaColor)
        assertEquals(ScriptType.KANA, prefs.scriptType)
        assertEquals(AnnotationPlateStyle.WASHI_CAPSULE, prefs.annotationPlateStyle)
        assertEquals(FabSize.MEDIUM, prefs.fabSize)
        assertEquals(FabDocking.FLOATING, prefs.fabDocking)
        assertFalse(prefs.isAutoTranslateEnabled)
        assertFalse(prefs.isDarkMode)
        assertTrue(prefs.touchPassthrough)
        assertTrue(prefs.autoHideOnKeyboard)
    }

    @Test
    fun testFuriganaSizeValues() {
        assertEquals(10, FuriganaSize.SMALL.sizeSp)
        assertEquals(12, FuriganaSize.MEDIUM.sizeSp)
        assertEquals(14, FuriganaSize.LARGE.sizeSp)
    }

    @Test
    fun testFuriganaColorValues() {
        assertEquals(0xFFB82923L, FuriganaColor.MUSHROOM_CRIMSON.colorHex)
        assertEquals(0xFF2B2625L, FuriganaColor.INK_BLACK.colorHex)
        assertEquals(0xFF233E60L, FuriganaColor.INDIGO.colorHex)
        assertEquals(0xFF24583FL, FuriganaColor.PINE_GREEN.colorHex)
    }

    @Test
    fun testFuriganaSegmentCreation() {
        val segment = FuriganaSegment(text = "私", reading = "わたし", isHighlighted = true)
        assertEquals("私", segment.text)
        assertEquals("わたし", segment.reading)
        assertEquals(true, segment.isHighlighted)

        val plainSegment = FuriganaSegment(text = "です")
        assertEquals("です", plainSegment.text)
        assertNull(plainSegment.reading)
        assertFalse(plainSegment.isHighlighted)
    }
}


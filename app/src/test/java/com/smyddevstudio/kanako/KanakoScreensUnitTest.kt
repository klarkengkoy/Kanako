package com.smyddevstudio.kanako

import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.FuriganaColor
import com.smyddevstudio.kanako.model.FuriganaSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Test

class KanakoScreensUnitTest {

    @Test
    fun testPreferencesCopyAndMutation() {
        val initial = KanakoPreferences()
        val updated = initial.copy(
            furiganaSize = FuriganaSize.LARGE,
            furiganaColor = FuriganaColor.PINE_GREEN,
            scriptType = ScriptType.ROMAJI,
            annotationPlateStyle = AnnotationPlateStyle.HIGH_CONTRAST,
            touchPassthrough = false
        )

        assertEquals(FuriganaSize.LARGE, updated.furiganaSize)
        assertEquals(FuriganaColor.PINE_GREEN, updated.furiganaColor)
        assertEquals(ScriptType.ROMAJI, updated.scriptType)
        assertEquals(AnnotationPlateStyle.HIGH_CONTRAST, updated.annotationPlateStyle)
        assertEquals(false, updated.touchPassthrough)
    }

    @Test
    fun testScriptTypeEnumLabels() {
        assertEquals("Kana", ScriptType.KANA.label)
        assertEquals("Romaji", ScriptType.ROMAJI.label)
    }

    @Test
    fun testAnnotationPlateStyleLabels() {
        assertEquals("Clear", AnnotationPlateStyle.CLEAR.label)
        assertEquals("Washi Capsule", AnnotationPlateStyle.WASHI_CAPSULE.label)
        assertEquals("High Contrast", AnnotationPlateStyle.HIGH_CONTRAST.label)
        assertEquals("Subtle Card", AnnotationPlateStyle.SUBTLE.label)
    }

    @Test
    fun testFuriganaSegmentProperties() {
        val segment = com.smyddevstudio.kanako.ui.components.FuriganaSegment(
            text = "猫",
            reading = "ねこ",
            isHighlighted = true
        )
        assertEquals("猫", segment.text)
        assertEquals("ねこ", segment.reading)
        assertEquals(true, segment.isHighlighted)
    }
}

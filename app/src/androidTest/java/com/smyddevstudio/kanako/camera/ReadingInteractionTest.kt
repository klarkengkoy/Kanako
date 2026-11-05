package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.service.ReadingTarget
import com.smyddevstudio.kanako.service.selectReading
import com.smyddevstudio.kanako.ui.components.CameraPhotoSurface
import com.smyddevstudio.kanako.ui.components.CameraReadingOverlay
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import com.smyddevstudio.kanako.ui.screens.cameraReaderGestures
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val line = CameraReadingLine("読み", CameraRect(100f, 200f, 180f, 240f),
        listOf(FuriganaSegment("読み", "よみ")))

    @Test fun liveKanjiTapSelectsWithoutRefocusingAndEmptyTapStillFocuses() {
        var invalidations = 0
        var focuses = 0
        var targets = emptyList<ReadingTarget>()
        compose.setContent {
            var selected by remember { mutableStateOf<String?>(null) }
            KanakoTheme {
                Box(Modifier.fillMaxSize().testTag("live")
                    .cameraReaderGestures({}, { focuses++ }, { invalidations++ }, onSelect = {
                        selected = selectReading(targets, it.x, it.y, selected)
                        selected != null
                    }).semantics { stateDescription = selected.orEmpty() }) {
                    CameraReadingOverlay(listOf(line), KanakoPreferences(), Modifier.fillMaxSize(),
                        selected = selected, onTargets = { targets = it })
                }
            }
        }
        compose.onNodeWithTag("live").performTouchInput { click(Offset(120f, 220f)) }
        compose.onNodeWithTag("live").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "0:0:読み"))
        compose.runOnIdle {
            assertEquals(0, invalidations)
            assertEquals(0, focuses)
        }
        compose.onNodeWithTag("live").performTouchInput { click(Offset(20f, 20f)) }
        compose.runOnIdle {
            assertEquals(1, invalidations)
            assertEquals(1, focuses)
        }
    }

    @Test fun matchingOkuriganaIsRemovedFromReadingAndAnchor() {
        val ruby = layoutCameraAnnotations(listOf(line), 400, 400, 24f, ScriptType.KANA).labels.single()
        assertEquals("よ", ruby.reading)
        assertEquals(100, ruby.target.anchor.left)
        assertEquals(140, ruby.target.anchor.right)
        assertEquals(120f, (ruby.bounds.left + ruby.bounds.right) / 2f, 0.5f)
    }

    @Test fun photoSelectionUsesZoomedCoordinatesAndComparisonPreservesTheReading() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
        val showReadings = mutableStateOf(true)
        try {
            compose.setContent {
                KanakoTheme {
                    CameraPhotoSurface(photo, listOf(line), KanakoPreferences(),
                        Modifier.fillMaxSize().testTag("photo"), showReadings = showReadings.value)
                }
            }
            val image = compose.onNodeWithTag("photo")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val expected = context.getString(R.string.photo_reading_selected, "読", "よ")
            compose.waitUntil(5_000) {
                image.fetchSemanticsNode().config[SemanticsActions.CustomActions].size > 3
            }
            fun tapSource(zoom: Float = 1f) {
                val bounds = image.fetchSemanticsNode().boundsInRoot
                val fit = fitCameraImage(400, 400, bounds.width.toInt(), bounds.height.toInt())!!
                val scale = fit.scale * zoom
                image.performTouchInput {
                    advanceEventTime(500)
                    click(Offset((bounds.width - 400 * scale) / 2 + 120 * scale,
                        (bounds.height - 400 * scale) / 2 + 220 * scale))
                }
            }
            fun selected() = image.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains(expected)
            tapSource()
            compose.waitUntil(5_000) { selected() }
            tapSource()
            compose.waitUntil(5_000) { !selected() }
            image.performTouchInput {
                pinch(start0 = center - Offset(40f, 0f), end0 = center - Offset(80f, 0f),
                    start1 = center + Offset(40f, 0f), end1 = center + Offset(80f, 0f), durationMillis = 400)
            }
            val zoom = image.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                .filter { it.isDigit() }.toFloat() / 100f
            tapSource(zoom)
            compose.waitUntil(5_000) { selected() }
            val state = image.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            compose.runOnIdle { showReadings.value = false }
            compose.waitUntil(5_000) { image.fetchSemanticsNode().config[SemanticsProperties.Text].all { it.text.isEmpty() } }
            compose.runOnIdle { showReadings.value = true }
            compose.waitUntil(5_000) {
                image.fetchSemanticsNode().config[SemanticsProperties.StateDescription] == state
            }
            image.performTouchInput { advanceEventTime(500); click(Offset(10f, 10f)) }
            compose.waitUntil(5_000) { !selected() }
        } finally { photo.close() }
    }

    @Test fun crowdedSourcesAreSelectableEvenWhenNoAutomaticLabelsFit() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888))
        val lines = listOf(
            CameraReadingLine("日本語", CameraRect(100f, 200f, 120f, 216f), listOf(FuriganaSegment("日本語", "にほんご"))),
            CameraReadingLine("銀行", CameraRect(124f, 200f, 144f, 216f), listOf(FuriganaSegment("銀行", "ぎんこう")))
        )
        try {
            compose.setContent {
                KanakoTheme {
                    CameraPhotoSurface(photo, lines, KanakoPreferences(scriptType = ScriptType.ROMAJI),
                        Modifier.fillMaxSize().testTag("photo"))
                }
            }
            val image = compose.onNodeWithTag("photo")
            compose.waitUntil(5_000) { image.fetchSemanticsNode().config[SemanticsActions.CustomActions].size == 5 }
            val actions = image.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { actions[3].action() }
            compose.waitUntil(5_000) {
                image.fetchSemanticsNode().config[SemanticsProperties.Text].single().text == "nihongo"
            }
            val next = image.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { next[4].action() }
            compose.waitUntil(5_000) {
                image.fetchSemanticsNode().config[SemanticsProperties.Text].single().text == "ginkou"
            }
            val clear = image.fetchSemanticsNode().config[SemanticsActions.CustomActions].last()
            compose.runOnIdle { clear.action() }
            compose.waitUntil(5_000) { image.fetchSemanticsNode().config[SemanticsActions.CustomActions].size == 5 }
        } finally { photo.close() }
    }
}

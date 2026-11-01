package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    @Test fun photoKanjiSelectionUsesZoomedImageCoordinatesAndReachesExportStyle() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
        var style: CameraExportStyle? = null
        try {
            compose.setContent {
                KanakoTheme {
                    CameraPhotoSurface(photo, listOf(line), KanakoPreferences(),
                        Modifier.fillMaxSize().testTag("photo"), onExportStyle = { style = it })
                }
            }
            val image = compose.onNodeWithTag("photo")
            val initialBounds = image.fetchSemanticsNode().boundsInRoot
            val initialFit = fitCameraImage(400, 400, initialBounds.width.toInt(), initialBounds.height.toInt())!!
            image.performTouchInput {
                click(Offset(initialFit.left + 120 * initialFit.scale, initialFit.top + 220 * initialFit.scale))
            }
            compose.waitUntil(timeoutMillis = 3_000) { style?.selected != null }
            image.performTouchInput { click(Offset(10f, 10f)) }
            compose.waitUntil(timeoutMillis = 3_000) { style?.selected == null }
            image.performTouchInput {
                pinch(start0 = center - Offset(40f, 0f), end0 = center - Offset(80f, 0f),
                    start1 = center + Offset(40f, 0f), end1 = center + Offset(80f, 0f), durationMillis = 400)
            }
            val bounds = image.fetchSemanticsNode().boundsInRoot
            val fit = fitCameraImage(400, 400, bounds.width.toInt(), bounds.height.toInt())!!
            val ruby = layoutCameraAnnotations(listOf(line), 400, 400,
                requireNotNull(style).fontPixels, ScriptType.KANA).labels.single()
            val zoomPercent = image.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                .filter { it.isDigit() }.toFloat() / 100f
            val scale = fit.scale * zoomPercent
            val target = Offset((bounds.width - 400 * scale) / 2 + 120 * scale,
                (bounds.height - 400 * scale) / 2 + 220 * scale)
            image.performTouchInput { click(target) }
            compose.waitUntil(timeoutMillis = 3_000) { style?.selected != null }
            compose.runOnIdle { assertEquals(ruby.target.key, style?.selected) }
        } finally { photo.close() }
    }
}

package com.smyddevstudio.kanako.camera

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPhotoAnnotationTest {
    private val readingTypeface by lazy {
        requireNotNull(androidx.core.content.res.ResourcesCompat.getFont(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
            com.smyddevstudio.kanako.R.font.nunito_sans_medium
        ))
    }

    private val document = listOf(
        CameraReadingLine("市役所", CameraRect(100f, 200f, 180f, 230f),
            listOf(FuriganaSegment("市役所", "しやくしょ"))),
        CameraReadingLine("銀行", CameraRect(200f, 200f, 260f, 230f),
            listOf(FuriganaSegment("銀行", "ぎんこう")))
    )

    @Test fun realTextMeasurementDefersCrowdedRomajiAndRevealsItAtCloserZoom() {
        val overview = layoutCameraPhotoAnnotations(document, 500, 400, 36f, ScriptType.ROMAJI, readingTypeface = readingTypeface)
        assertTrue(overview.labels.isEmpty())
        val closer = layoutCameraPhotoAnnotations(document, 500, 400, 12f, ScriptType.ROMAJI, readingTypeface = readingTypeface)
        assertEquals(2, closer.labels.size)
        assertNull(closer.labels[0].bounds.intersection(closer.labels[1].bounds))
        closer.labels.forEach { label ->
            assertTrue(label.layouts.all { it.paint.textSize >= 12f })
            document.forEach { line ->
                val source = ReadingBounds(line.bounds.left.toInt(), line.bounds.top.toInt(),
                    line.bounds.right.toInt(), line.bounds.bottom.toInt())
                assertNull(label.bounds.intersection(source))
            }
        }
    }

    @Test fun largeJapaneseSourceCharactersGrowReadingsAboveTheChosenFloor() {
        val line = CameraReadingLine("日本語", CameraRect(200f, 300f, 400f, 400f),
            listOf(FuriganaSegment("日本語", "にほんご")))
        val ruby = layoutCameraPhotoAnnotations(listOf(line), 700, 600, 12f, ScriptType.KANA, readingTypeface = readingTypeface).labels.single()
        assertEquals(50f, ruby.layouts.single().paint.textSize, 0.01f)
        assertEquals(600, ruby.bounds.left + ruby.bounds.right)
    }

    @Test fun allGroupsRemainAvailableAndSelectingACrowdedGroupShowsOnlyItsReading() {
        val groups = cameraPhotoReadings(document, 500, 400, ScriptType.ROMAJI)
        assertEquals(listOf("市役所", "銀行"), groups.map { it.source })
        assertTrue(layoutCameraPhotoReadings(groups, groups.map { it.anchor }, 500, 400, 36f, readingTypeface = readingTypeface).labels.isEmpty())
        val selected = layoutCameraPhotoReadings(groups, groups.map { it.anchor }, 500, 400, 36f,
            selected = groups.first().key, readingTypeface = readingTypeface)
        assertEquals(1, selected.labels.size)
        assertEquals(groups.first().reading, selected.labels.single().reading)
        assertTrue(selected.labels.single().layouts.all { it.paint.textSize >= 36f })
        assertNull(selected.labels.single().bounds.intersection(groups.first().anchor))
    }

    @Test fun selectedCalloutStaysInsideTheUnobscuredPartOfThePhoto() {
        val groups = cameraPhotoReadings(document, 500, 400, ScriptType.KANA)
        val viewport = ReadingBounds(80, 140, 300, 340)
        val selected = layoutCameraPhotoReadings(groups, groups.map { it.anchor }, 500, 400, 20f,
            selected = groups.first().key, selectionClip = viewport, readingTypeface = readingTypeface)
        assertTrue(viewport.contains(selected.labels.single().bounds))
    }

    @Test fun highlightsIncludeOkuriganaWhileReadingsStayOnKanjiAndExcludeKanaOnlyTokens() {
        val line = CameraReadingLine("読みかな", CameraRect(100f, 200f, 260f, 240f),
            listOf(FuriganaSegment("読み", "よみ"), FuriganaSegment("かな", "かな")))
        val group = cameraPhotoReadings(listOf(line), 400, 400, ScriptType.KANA).single()
        assertEquals("読み", group.source)
        assertEquals("よ", group.reading)
        assertEquals(100, group.anchor.left)
        assertEquals(140, group.anchor.right)
        assertEquals(180, group.highlight.right)
        assertEquals(group.key, selectCameraPhotoReading(listOf(group), 170f, 220f, null))
        assertNull(selectCameraPhotoReading(listOf(group), 220f, 220f, null))
    }

    @Test fun auxiliaryKanaIsHighlightedInLiveAndPhotoWhileTheLabelStaysOnKanji() {
        val line = CameraReadingLine("読みますを本", CameraRect(100f, 200f, 340f, 240f),
            listOf(FuriganaSegment("読み", "よみ", highlightSuffixLength = 2),
                FuriganaSegment("ます"), FuriganaSegment("を"), FuriganaSegment("本", "ほん")))
        val group = cameraPhotoReadings(listOf(line), 500, 500, ScriptType.KANA).first()
        val liveLine = cameraReadingGroups(listOf(line)).first()
        val live = cameraPhotoReadings(listOf(liveLine), 500, 500, ScriptType.KANA).single()
        assertEquals("読みます", group.source)
        assertEquals("よ", group.reading)
        assertEquals(ReadingBounds(100, 200, 140, 240), group.anchor)
        assertEquals(ReadingBounds(100, 200, 260, 240), group.highlight)
        assertEquals(group.highlight, live.highlight)
        assertEquals(group.anchor, live.anchor)
        assertEquals(group.key, selectCameraPhotoReading(listOf(group), 240f, 220f, null))
    }

    @Test fun tiltedHighlightsStayInsideTheCharacterShape() {
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val line = CameraReadingLine("日", CameraRect(10f, 20f, 60f, 60f),
                listOf(FuriganaSegment("日", "ひ")), listOf(CameraTextSpan(0, 1, CameraRect(10f, 20f, 60f, 60f),
                    listOf(CameraPoint(20f, 20f), CameraPoint(60f, 40f), CameraPoint(50f, 60f), CameraPoint(10f, 40f)))))
            val readings = cameraPhotoReadings(listOf(line), 100, 100, ScriptType.KANA)
            drawCameraPhotoHighlights(android.graphics.Canvas(bitmap), readings, null, 1f)
            assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(12, 22)))
            assertTrue(android.graphics.Color.alpha(bitmap.getPixel(35, 40)) > 0)
        } finally { bitmap.recycle() }
    }

    @Test fun compoundReadingHasOneContinuousHighlightAcrossItsCharacters() {
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val line = CameraReadingLine("日本", CameraRect(10f, 20f, 80f, 40f),
                listOf(FuriganaSegment("日本", "にほん")), listOf(
                    CameraTextSpan(0, 1, CameraRect(10f, 20f, 30f, 40f)),
                    CameraTextSpan(1, 2, CameraRect(60f, 20f, 80f, 40f))))
            val readings = cameraPhotoReadings(listOf(line), 100, 100, ScriptType.KANA)
            assertEquals(1, readings.size)
            drawCameraPhotoHighlights(android.graphics.Canvas(bitmap), readings, readings.single().key, 1f)
            assertEquals(1, readings.single().outlines.size)
            assertTrue(android.graphics.Color.alpha(bitmap.getPixel(45, 30)) > 0)
            assertTrue(android.graphics.Color.alpha(bitmap.getPixel(20, 30)) > 0)
        } finally { bitmap.recycle() }
    }

    @Test fun liveAndPhotoUseTheSameCompoundOutlineAndTrackedSelectionKey() {
        val line = CameraReadingLine("日本", CameraRect(10f, 20f, 80f, 40f),
            listOf(FuriganaSegment("日本", "にほん")), listOf(
                CameraTextSpan(0, 1, CameraRect(10f, 20f, 30f, 40f)),
                CameraTextSpan(1, 2, CameraRect(60f, 20f, 80f, 40f))), trackingKey = "live:42")
        val group = cameraPhotoReadings(listOf(line), 200, 200, ScriptType.KANA).single()
        val live = layoutCameraAnnotations(listOf(line), 200, 200, 12f, ScriptType.KANA, readingTypeface = readingTypeface).labels.single()
        assertEquals("live:42", group.key)
        assertEquals(group.key, live.target.key)
        assertEquals(group.anchor, live.target.anchor)
        assertEquals(1, group.outlines.size)
    }

    @Test fun cancellationStopsExtractionBeforeMoreSourceGroupsAreProcessed() {
        var checks = 0
        val error = runCatching {
            cameraPhotoReadings(document, 500, 400, ScriptType.KANA, checkCancellation = {
                checks++
                if (checks == 2) throw kotlinx.coroutines.CancellationException("Back")
            })
        }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertEquals(2, checks)
    }
}

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
    private val document = listOf(
        CameraReadingLine("市役所", CameraRect(100f, 200f, 180f, 230f),
            listOf(FuriganaSegment("市役所", "しやくしょ"))),
        CameraReadingLine("銀行", CameraRect(200f, 200f, 260f, 230f),
            listOf(FuriganaSegment("銀行", "ぎんこう")))
    )

    @Test fun realTextMeasurementDefersCrowdedRomajiAndRevealsItAtCloserZoom() {
        val overview = layoutCameraPhotoAnnotations(document, 500, 400, 36f, ScriptType.ROMAJI)
        assertTrue(overview.labels.isEmpty())
        val closer = layoutCameraPhotoAnnotations(document, 500, 400, 12f, ScriptType.ROMAJI)
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
        val ruby = layoutCameraPhotoAnnotations(listOf(line), 700, 600, 12f, ScriptType.KANA).labels.single()
        assertEquals(50f, ruby.layouts.single().paint.textSize, 0.01f)
        assertEquals(600, ruby.bounds.left + ruby.bounds.right)
    }

    @Test fun allGroupsRemainAvailableAndSelectingACrowdedGroupShowsOnlyItsReading() {
        val groups = cameraPhotoReadings(document, 500, 400, ScriptType.ROMAJI)
        assertEquals(listOf("市役所", "銀行"), groups.map { it.source })
        assertTrue(layoutCameraPhotoReadings(groups, groups.map { it.anchor }, 500, 400, 36f).labels.isEmpty())
        val selected = layoutCameraPhotoReadings(groups, groups.map { it.anchor }, 500, 400, 36f,
            selected = groups.first().key)
        assertEquals(1, selected.labels.size)
        assertEquals(groups.first().reading, selected.labels.single().reading)
        assertTrue(selected.labels.single().layouts.all { it.paint.textSize >= 36f })
        assertNull(selected.labels.single().bounds.intersection(groups.first().anchor))
    }

    @Test fun selectedCalloutStaysInsideTheUnobscuredPartOfThePhoto() {
        val groups = cameraPhotoReadings(document, 500, 400, ScriptType.KANA)
        val viewport = ReadingBounds(80, 140, 300, 340)
        val selected = layoutCameraPhotoReadings(groups, groups.map { it.anchor }, 500, 400, 20f,
            selected = groups.first().key, selectionClip = viewport)
        assertTrue(viewport.contains(selected.labels.single().bounds))
    }

    @Test fun highlightsFollowReadingGroupsAndExcludeMatchingOkuriganaAndKanaOnlyTokens() {
        val line = CameraReadingLine("読みかな", CameraRect(100f, 200f, 260f, 240f),
            listOf(FuriganaSegment("読み", "よみ"), FuriganaSegment("かな", "かな")))
        val group = cameraPhotoReadings(listOf(line), 400, 400, ScriptType.KANA).single()
        assertEquals("読", group.source)
        assertEquals("よ", group.reading)
        assertEquals(100, group.anchor.left)
        assertEquals(140, group.anchor.right)
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

package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.ReadingTarget
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraLiveMotionTest {
    private fun frame(x: Int, key: String = "word", reading: String = "にほん"): CameraLiveFrame {
        val anchor = ReadingBounds(x, 100, x + 60, 130)
        val label = ReadingBounds(x, 75, x + 60, 95)
        val group = CameraPhotoReading(anchor, false, reading, key, "日本").copy(outlines = listOf(
            CameraPolygon(listOf(CameraPoint(x.toFloat(), 100f), CameraPoint(x + 60f, 100f),
                CameraPoint(x + 60f, 130f), CameraPoint(x.toFloat(), 130f)))))
        val ruby = CameraRuby(label, ReadingBounds(0, 0, 800, 800), emptyList(), 1, 1,
            false, reading, ReadingTarget(key, anchor, label))
        return CameraLiveFrame(CameraAnnotationLayout(listOf(ruby)), listOf(group))
    }

    @Test fun aMatchedHighlightLabelAndHitTargetSlideTogether() {
        val motion = CameraLiveMotion()
        motion.retarget(frame(100), 1000, true)
        motion.retarget(frame(140), 1160, true)
        val halfway = motion.frameAt(1205)
        assertEquals(120, halfway.groups.single().anchor.left)
        assertEquals(120f, halfway.groups.single().outlines.single().points.first().x, 0.001f)
        assertEquals(120, halfway.layout.labels.single().bounds.left)
        assertEquals(halfway.groups.single().anchor, halfway.targets.single().anchor)
        assertEquals(halfway.layout.labels.single().bounds, halfway.targets.single().label)
        assertEquals(140, motion.frameAt(1250).groups.single().anchor.left)
        assertFalse(motion.isMoving(1250))
    }

    @Test fun kanaEndingHitBoundsMoveAlongsideTheirSeparateKanjiAnchor() {
        fun wordFrame(x: Int): CameraLiveFrame {
            val original = frame(x)
            val group = original.groups.single()
            val highlight = group.anchor.copy(right = group.anchor.right + 40)
            return original.copy(groups = listOf(group.copy(group = group.group.copy(highlight = highlight))))
        }
        val motion = CameraLiveMotion()
        motion.retarget(wordFrame(100), 1000, true)
        motion.retarget(wordFrame(140), 1160, true)
        val halfway = motion.frameAt(1205)
        val word = halfway.groups.single()
        assertEquals(ReadingBounds(120, 100, 180, 130), word.anchor)
        assertEquals(ReadingBounds(120, 100, 220, 130), word.highlight)
        assertEquals(word.highlight, halfway.targets.single().anchor)
        assertEquals(word.key, selectCameraPhotoReading(listOf(word), 210f, 115f, null))
    }

    @Test fun retargetingAnActiveSlideStartsAtItsCurrentPosition() {
        val motion = CameraLiveMotion()
        motion.retarget(frame(100), 1000, true)
        motion.retarget(frame(140), 1160, true)
        motion.retarget(frame(160), 1205, true)
        assertEquals(120, motion.frameAt(1205).groups.single().anchor.left)
        assertEquals(140, motion.frameAt(1250).groups.single().anchor.left)
        assertEquals(160, motion.frameAt(1295).groups.single().anchor.left)
    }

    @Test fun sceneReplacementAndLargeMovementNeverAnimateAnOldReadingAcrossTheView() {
        val motion = CameraLiveMotion()
        motion.retarget(frame(100), 1000, true)
        motion.retarget(frame(500), 1160, true)
        assertEquals(500, motion.frameAt(1160).groups.single().anchor.left)
        assertFalse(motion.isMoving(1160))
        motion.retarget(frame(520, key = "new", reading = "にっぽん"), 1320, true)
        assertEquals("new", motion.frameAt(1320).groups.single().key)
        assertEquals("にっぽん", motion.frameAt(1320).layout.labels.single().reading)
        assertFalse(motion.isMoving(1320))
    }

    @Test fun aReplacementIsVisibleBeforeTheAnimationReceivesItsFirstFrame() {
        val previous = frame(100, key = "old")
        val accepted = frame(140, key = "new")
        val displayed = previous.forTarget(accepted)
        assertEquals(accepted.groups, displayed.groups)
        assertEquals(accepted.layout.labels, displayed.layout.labels)
        assertEquals(accepted.targets, displayed.targets)
    }

    @Test fun newlyAcceptedGroupsDoNotWaitForTheAnimationToStart() {
        val empty = CameraLiveFrame(CameraAnnotationLayout(emptyList()), emptyList())
        val accepted = frame(140)
        assertEquals(accepted, empty.forTarget(accepted))
        assertEquals(empty, accepted.forTarget(empty))
    }

    @Test fun targetHandoffKeepsAnExistingGroupAtItsAnimatedPosition() {
        val motion = CameraLiveMotion()
        motion.retarget(frame(100), 1000, true)
        val target = frame(140)
        motion.retarget(target, 1160, true)
        val midway = motion.frameAt(1205)
        val displayed = midway.forTarget(target)
        assertEquals(midway, displayed)
        assertEquals(120, displayed.targets.single().anchor.left)
        assertEquals(120, displayed.targets.single().label.left)
    }

    @Test fun anUnchangedKeyCannotKeepAnOldPronunciationOnScreen() {
        val accepted = frame(140, reading = "にっぽん")
        val displayed = frame(100).forTarget(accepted)
        assertEquals(accepted, displayed)
    }

    @Test fun removalClearsAllVisualsAndReducedMotionSnapsImmediately() {
        val motion = CameraLiveMotion()
        motion.retarget(frame(100), 1000, true)
        motion.retarget(frame(140), 1160, false)
        assertEquals(140, motion.frameAt(1160).groups.single().anchor.left)
        assertFalse(motion.isMoving(1160))
        motion.retarget(CameraLiveFrame(CameraAnnotationLayout(emptyList()), emptyList()), 1320, true)
        assertTrue(motion.frameAt(1320).groups.isEmpty())
        assertTrue(motion.frameAt(1320).layout.labels.isEmpty())
        assertTrue(motion.frameAt(1320).targets.isEmpty())
    }
}

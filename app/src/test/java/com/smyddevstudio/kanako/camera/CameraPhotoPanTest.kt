package com.smyddevstudio.kanako.camera

import org.junit.Assert.*
import org.junit.Test

class CameraPhotoPanTest {
    @Test fun photoEdgesCanMoveClearOfControlsAtNormalZoom() {
        val belowHeader = CameraPhotoGesture().update(1f, 0f, 500f, 200f, 200f, 400, 400, 400, 400,
            topPadding = 96f, bottomPadding = 24f)
        assertEquals(1f, belowHeader.zoom, 0f)
        assertEquals(96f, belowHeader.y, 0f)
        val aboveNavigation = belowHeader.update(1f, 0f, -500f, 200f, 200f, 400, 400, 400, 400,
            topPadding = 96f, bottomPadding = 24f)
        assertEquals(-24f, aboveNavigation.y, 0f)
    }

    @Test fun galleryGesturesRetainTheirExistingDefaultBounds() {
        assertEquals(CameraPhotoGesture(),
            CameraPhotoGesture().update(1f, 50f, 100f, 200f, 200f, 400, 400, 400, 400))
    }

    @Test fun zoomedMovementStillStaysWithinTheImageAndControlMargins() {
        val moved = CameraPhotoGesture(zoom = 2f).update(1f, 5000f, 5000f, 200f, 200f,
            400, 400, 400, 400, topPadding = 96f, bottomPadding = 24f)
        assertEquals(200f, moved.x, 0f)
        assertEquals(296f, moved.y, 0f)
    }
}

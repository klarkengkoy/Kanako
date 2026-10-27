package com.smyddevstudio.kanako.camera

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraGeometryTest {
    @Test fun photoFitPreservesAspectRatioAndCentersLetterboxing() {
        val portrait = fitCameraImage(400, 300, 300, 600)!!
        assertEquals(0.75f, portrait.scale, 0.001f)
        assertEquals(0f, portrait.left, 0.001f)
        assertEquals(187.5f, portrait.top, 0.001f)
        val landscape = fitCameraImage(300, 400, 600, 300)!!
        assertEquals(187.5f, landscape.left, 0.001f)
        assertEquals(0f, landscape.top, 0.001f)
        assertNull(fitCameraImage(0, 300, 300, 600))
        assertNull(fitCameraImage(400, 300, 0, 600))
    }

    @Test fun zoomKeepsThePinchAnchorAndClampsPanning() {
        val zoom = CameraPhotoGesture().update(2f, 0f, 0f, 75f, 75f, 300, 300, 300, 300)
        assertEquals(2f, zoom.zoom, 0.001f)
        assertEquals(75f, zoom.x, 0.001f)
        assertEquals(75f, zoom.y, 0.001f)
        val pan = zoom.update(1f, 10000f, -10000f, 150f, 150f, 300, 300, 300, 300)
        assertEquals(150f, pan.x, 0.001f)
        assertEquals(-150f, pan.y, 0.001f)
        val reset = pan.update(0.1f, 20f, 20f, 150f, 150f, 300, 300, 300, 300)
        assertEquals(CameraPhotoGesture(), reset)
        assertEquals(zoom, zoom.update(Float.NaN, 0f, 0f, 150f, 150f, 300, 300, 300, 300))
    }

    @Test fun letterboxedAxisCannotBeDraggedUntilImageFillsIt() {
        val gesture = CameraPhotoGesture().update(1.5f, 500f, 500f, 150f, 300f, 400, 300, 300, 600)
        assertEquals(0f, gesture.y, 0.001f)
        assertEquals(75f, gesture.x, 0.001f)
    }

    @Test fun pauseAndGeometryChangesRejectEarlierLiveResults() {
        val gate = CameraRequestGate()
        assertNull(gate.liveTicket())
        val first = gate.resumeLive()
        assertTrue(gate.acceptsLive(first))
        gate.invalidateLive()
        assertFalse(gate.acceptsLive(first))
        val second = gate.liveTicket()!!
        gate.pauseLive()
        assertFalse(gate.acceptsLive(second))
        assertNull(gate.liveTicket())
    }

    @Test fun captureIsSingleFlightAndCannotCompleteAfterLeavingCamera() {
        val gate = CameraRequestGate()
        gate.resumeLive()
        val capture = gate.beginCapture()!!
        assertNull(gate.beginCapture())
        assertNull(gate.liveTicket())
        gate.pauseLive()
        assertFalse(gate.acceptCapture(capture))
    }

    @Test fun photoRecognitionSurvivesPauseAndPreferencesButNotReturnToLive() {
        val gate = CameraRequestGate()
        gate.resumeLive()
        val capture = gate.beginCapture()!!
        assertTrue(gate.acceptCapture(capture))
        gate.pauseLive()
        gate.invalidateLive()
        gate.resumeLive()
        assertTrue(gate.acceptsPhoto(capture))
        assertNull(gate.liveTicket())
        gate.returnToLive()
        assertFalse(gate.acceptsPhoto(capture))
        gate.resumeLive()
        assertNotNull(gate.liveTicket())
    }
}

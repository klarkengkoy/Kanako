package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.ReadingGroup
import com.smyddevstudio.kanako.service.placeReadingCallout
import com.smyddevstudio.kanako.service.selectReadingGroup

internal typealias CameraPhotoReading = ReadingGroup

internal fun selectCameraPhotoReading(
    readings: List<CameraPhotoReading>, x: Float, y: Float, selected: String?
): String? = selectReadingGroup(readings, x, y, selected)

internal fun placeCameraPhotoCallout(
    anchor: ReadingBounds, clip: ReadingBounds, width: Int, height: Int, gap: Int, vertical: Boolean
): ReadingBounds? = placeReadingCallout(anchor, clip, width, height, gap, vertical)

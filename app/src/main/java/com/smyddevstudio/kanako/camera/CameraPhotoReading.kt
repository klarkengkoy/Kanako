package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.ReadingGroup
import com.smyddevstudio.kanako.service.placeReadingCallout
import com.smyddevstudio.kanako.service.selectReadingGroup

internal data class CameraPhotoReading(
    val group: ReadingGroup,
    val outlines: List<CameraPolygon> = emptyList()
) {
    constructor(anchor: ReadingBounds, vertical: Boolean, reading: String, key: String, source: String) :
        this(ReadingGroup(anchor, vertical, reading, key, source))

    val anchor get() = group.anchor
    val vertical get() = group.vertical
    val reading get() = group.reading
    val key get() = group.key
    val source get() = group.source
}

internal fun selectCameraPhotoReading(
    readings: List<CameraPhotoReading>, x: Float, y: Float, selected: String?
): String? = selectReadingGroup(readings.map { it.group }, x, y, selected)

internal fun placeCameraPhotoCallout(
    anchor: ReadingBounds, clip: ReadingBounds, width: Int, height: Int, gap: Int, vertical: Boolean
): ReadingBounds? = placeReadingCallout(anchor, clip, width, height, gap, vertical)

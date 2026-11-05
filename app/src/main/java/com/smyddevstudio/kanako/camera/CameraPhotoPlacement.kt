package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.GroupedRubyRequest
import com.smyddevstudio.kanako.service.GroupedRubyMeasurement
import com.smyddevstudio.kanako.service.GroupedRubyPlacement
import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.placeGroupedReadings

internal typealias PhotoRubyRequest = GroupedRubyRequest
internal typealias PhotoRubyMeasurement<T> = GroupedRubyMeasurement<T>
internal typealias PhotoRubyPlacement<T> = GroupedRubyPlacement<T>

internal fun <T> placeCameraPhotoReadings(
    requests: List<PhotoRubyRequest>,
    clip: ReadingBounds,
    sourceText: List<ReadingBounds>,
    minimumFontPixels: Float,
    measure: (Int, Float) -> PhotoRubyMeasurement<T>?,
    checkCancellation: () -> Unit = {}
): List<PhotoRubyPlacement<T>?> = placeGroupedReadings(
    requests, clip, sourceText, minimumFontPixels, measure, checkCancellation
)

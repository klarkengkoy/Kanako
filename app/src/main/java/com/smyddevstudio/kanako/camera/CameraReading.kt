package com.smyddevstudio.kanako.camera

import com.google.mlkit.vision.text.Text
import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.ReadingContext
import com.smyddevstudio.kanako.ui.components.FuriganaSegment

internal data class CameraReadingLine(
    val text: String,
    val bounds: CameraRect,
    val segments: List<FuriganaSegment>,
    // Coordinates use the same space as bounds: PreviewView pixels live, upright bitmap pixels in photos.
    val spans: List<CameraTextSpan> = emptyList(),
    val vertical: Boolean? = null,
    val trackingKey: String? = null
) {
    val isVertical get() = vertical ?: (bounds.height > bounds.width)
}

internal data class CameraTextSpan(
    val start: Int, val end: Int, val bounds: CameraRect,
    val corners: List<CameraPoint> = emptyList()
)

internal fun cameraReadingLines(
    text: Text, readingContext: ReadingContext = ReadingContext()
): List<CameraReadingLine> {
    val blocks = text.textBlocks.map { block ->
        block.lines.mapNotNull { line ->
            val bounds = line.boundingBox
            if (line.text.isBlank() || bounds == null || bounds.isEmpty) null else CameraReadingLine(
                line.text,
                CameraRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat()),
                emptyList(),
                buildList {
                    var cursor = 0
                    line.elements.forEach { element ->
                        val start = line.text.indexOf(element.text, cursor)
                        if (start < 0) return@forEach
                        cursor = start + element.text.length
                        var symbolCursor = start
                        val symbols = element.symbols.mapNotNull { symbol ->
                            val symbolStart = line.text.indexOf(symbol.text, symbolCursor)
                            val box = symbol.boundingBox
                            if (symbolStart < 0 || symbolStart + symbol.text.length > cursor || box == null || box.isEmpty) null
                            else {
                                symbolCursor = symbolStart + symbol.text.length
                                CameraTextSpan(symbolStart, symbolCursor, CameraRect(
                                    box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()
                                ), symbol.cornerPoints?.map { CameraPoint(it.x.toFloat(), it.y.toFloat()) }.orEmpty())
                            }
                        }
                        // Prefer character geometry; element bounds remain useful when symbols are unavailable.
                        if (symbols.sumOf { it.end - it.start } == element.text.length) addAll(symbols)
                        else element.boundingBox?.takeUnless { it.isEmpty }?.let { box ->
                            add(CameraTextSpan(start, cursor, CameraRect(
                                box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()
                            ), element.cornerPoints?.map { CameraPoint(it.x.toFloat(), it.y.toFloat()) }.orEmpty()))
                        }
                    }
                }
            )
        }
    }
    val readings = readingContext.tokenize(blocks, { it.text }, {
        ReadingBounds(it.bounds.left.toInt(), it.bounds.top.toInt(), it.bounds.right.toInt(), it.bounds.bottom.toInt())
    })
    return blocks.flatten().mapIndexed { index, line -> line.copy(segments = readings[index]) }
}

internal val List<CameraReadingLine>.hasReadings: Boolean
    get() = any { line -> line.segments.any { !it.reading.isNullOrEmpty() } }

internal enum class CameraReadingStatus {
    STARTING, LIVE, NO_TEXT, MODEL_NEEDED, CAMERA_ERROR, CAPTURING, PROCESSING, PHOTO, READ_ERROR
}

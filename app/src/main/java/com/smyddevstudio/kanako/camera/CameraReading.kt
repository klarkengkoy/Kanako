package com.smyddevstudio.kanako.camera

import com.google.mlkit.vision.text.Text
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.ui.components.FuriganaSegment

internal data class CameraReadingLine(
    val text: String,
    val bounds: CameraRect,
    val segments: List<FuriganaSegment>,
    // Coordinates use the same space as bounds: PreviewView pixels live, upright bitmap pixels in photos.
    val spans: List<CameraTextSpan> = emptyList()
)

internal data class CameraTextSpan(val start: Int, val end: Int, val bounds: CameraRect)

internal fun cameraReadingLines(
    text: Text, cachedReadings: Map<String, List<FuriganaSegment>> = emptyMap()
): List<CameraReadingLine> {
    val tokenizer = JapaneseTextTokenizer()
    return text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
        val bounds = line.boundingBox
        if (line.text.isBlank() || bounds == null || bounds.isEmpty) null else CameraReadingLine(
            line.text,
            CameraRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat()),
            cachedReadings[line.text] ?: tokenizer.tokenize(line.text),
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
                            ))
                        }
                    }
                    // Prefer character geometry; element bounds remain useful when symbols are unavailable.
                    if (symbols.sumOf { it.end - it.start } == element.text.length) addAll(symbols)
                    else element.boundingBox?.takeUnless { it.isEmpty }?.let { box ->
                        add(CameraTextSpan(start, cursor, CameraRect(
                            box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()
                        )))
                    }
                }
            }
        )
    }
}

internal val List<CameraReadingLine>.hasReadings: Boolean
    get() = any { line -> line.segments.any { !it.reading.isNullOrEmpty() } }

internal enum class CameraReadingStatus {
    STARTING, LIVE, NO_TEXT, MODEL_NEEDED, CAMERA_ERROR, CAPTURING, PROCESSING, PHOTO, READ_ERROR
}

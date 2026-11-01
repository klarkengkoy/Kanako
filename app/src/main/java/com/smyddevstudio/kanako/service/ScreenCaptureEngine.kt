package com.smyddevstudio.kanako.service

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import java.util.concurrent.Executors
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

internal data class RecognizedTextLine(
    val text: String,
    val bounds: Rect,
    val windowId: Int,
    val clip: ReadingBounds,
    val spans: List<RecognizedTextSpan> = emptyList(),
    val segments: List<FuriganaSegment>? = null
)

internal class ScreenCaptureEngine : AutoCloseable {
    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )
    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    private var closed = false

    fun readOnce(
        shouldProcess: () -> Boolean,
        onCaptured: () -> Unit,
        onFinished: () -> Unit = {},
        onResult: (Result<List<RecognizedTextLine>>) -> Unit
    ): Boolean {
        if (busy || closed) return false
        busy = true
        KanakoAccessibilityService.requestScreenshot { result ->
            onCaptured()
            val frame = result.getOrElse {
                busy = false
                if (!closed) onResult(Result.failure(it))
                finishClose()
                onFinished()
                return@requestScreenshot
            }
            if (closed || !shouldProcess()) {
                frame.bitmap.recycle()
                busy = false
                finishClose()
                onFinished()
                return@requestScreenshot
            }
            try {
                recognizer.process(InputImage.fromBitmap(frame.bitmap, 0))
                    .continueWith(worker) { task ->
                        screenReadingLines(task.getResult(Exception::class.java),
                            frame.left, frame.top, frame.windowId, frame.viewport)
                    }
                    .addOnSuccessListener { lines ->
                        if (!closed) onResult(Result.success(lines))
                    }
                    .addOnFailureListener { error ->
                        if (!closed) onResult(Result.failure(
                            SnapshotFailure(
                                if (error is MlKitException && error.errorCode == MlKitException.UNAVAILABLE)
                                    SnapshotFailure.Reason.MODEL_UNAVAILABLE
                                else SnapshotFailure.Reason.OCR_FAILED
                            )
                        ))
                    }
                    .addOnCompleteListener {
                        frame.bitmap.recycle()
                        busy = false
                        finishClose()
                        onFinished()
                    }
            } catch (_: RuntimeException) {
                frame.bitmap.recycle()
                busy = false
                if (!closed) onResult(Result.failure(SnapshotFailure(SnapshotFailure.Reason.OCR_FAILED)))
                finishClose()
                onFinished()
            }
        }
        return true
    }

    private fun finishClose() {
        if (closed && !busy) {
            recognizer.close()
            worker.shutdown()
        }
    }

    override fun close() {
        closed = true
        finishClose()
    }
}

internal fun screenReadingLines(
    text: Text, left: Int, top: Int, windowId: Int, viewport: ReadingBounds
): List<RecognizedTextLine> {
    val tokenizer = JapaneseTextTokenizer()
    return text.textBlocks.flatMap { block ->
        val lines = block.lines.mapNotNull { line ->
            val box = line.boundingBox
            if (line.text.isBlank() || box == null || box.isEmpty) null else {
                fun bounds(rect: Rect) = ReadingBounds(rect.left + left, rect.top + top,
                    rect.right + left, rect.bottom + top)
                RecognizedTextLine(line.text, Rect(box).apply { offset(left, top) }, windowId, viewport,
                    buildList {
                        var cursor = 0
                        line.elements.forEach { element ->
                            val start = line.text.indexOf(element.text, cursor)
                            if (start < 0) return@forEach
                            cursor = start + element.text.length
                            var symbolCursor = start
                            val symbols = element.symbols.mapNotNull { symbol ->
                                val from = line.text.indexOf(symbol.text, symbolCursor)
                                val symbolBox = symbol.boundingBox
                                if (from < 0 || from + symbol.text.length > cursor ||
                                    symbolBox == null || symbolBox.isEmpty
                                ) null else {
                                    symbolCursor = from + symbol.text.length
                                    RecognizedTextSpan(from, symbolCursor, bounds(symbolBox))
                                }
                            }
                            if (symbols.sumOf { it.end - it.start } == element.text.length) addAll(symbols)
                            else element.boundingBox?.takeUnless { it.isEmpty }?.let {
                                add(RecognizedTextSpan(start, cursor, bounds(it)))
                            }
                        }
                    })
            }
        }
        val groups = mutableListOf<MutableList<RecognizedTextLine>>()
        lines.forEach { line ->
            val previous = groups.lastOrNull()?.lastOrNull()
            if (previous != null && continuesReadingLine(previous.text,
                    ReadingBounds(previous.bounds.left, previous.bounds.top, previous.bounds.right, previous.bounds.bottom),
                    ReadingBounds(line.bounds.left, line.bounds.top, line.bounds.right, line.bounds.bottom))
            ) groups.last().add(line) else groups.add(mutableListOf(line))
        }
        groups.flatMap { group ->
            val segments = tokenizer.tokenizeLines(group.map { it.text })
            group.mapIndexed { index, line -> line.copy(segments = segments[index]) }
        }.filter { line -> viewport.intersection(
            ReadingBounds(line.bounds.left, line.bounds.top, line.bounds.right, line.bounds.bottom)) != null
        }
    }
}

internal class SnapshotFailure(val reason: Reason) : Exception() {
    enum class Reason {
        SERVICE_UNAVAILABLE, UNSUPPORTED, SENSITIVE_SCREEN, NO_SOURCE,
        PROTECTED_CONTENT, TOO_SOON, CAPTURE_FAILED, MODEL_UNAVAILABLE, OCR_FAILED
    }
}

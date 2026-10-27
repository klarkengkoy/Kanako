package com.smyddevstudio.kanako.service

import android.graphics.Rect
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

internal data class RecognizedTextLine(
    val text: String,
    val bounds: Rect,
    val windowId: Int,
    val clip: ReadingBounds
)

internal class ScreenCaptureEngine : AutoCloseable {
    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )
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
                    .addOnSuccessListener { text ->
                        if (!closed) {
                            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                                val bounds = line.boundingBox
                                if (line.text.isBlank() || bounds == null || bounds.isEmpty) null
                                else RecognizedTextLine(
                                    line.text,
                                    Rect(bounds).apply { offset(frame.left, frame.top) },
                                    frame.windowId,
                                    frame.viewport
                                ).takeIf { recognized ->
                                    frame.viewport.intersection(ReadingBounds(
                                        recognized.bounds.left, recognized.bounds.top,
                                        recognized.bounds.right, recognized.bounds.bottom
                                    )) != null
                                }
                            }
                            onResult(Result.success(lines))
                        }
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
        if (closed && !busy) recognizer.close()
    }

    override fun close() {
        closed = true
        finishClose()
    }
}

internal class SnapshotFailure(val reason: Reason) : Exception() {
    enum class Reason {
        SERVICE_UNAVAILABLE, UNSUPPORTED, SENSITIVE_SCREEN, NO_SOURCE,
        PROTECTED_CONTENT, TOO_SOON, CAPTURE_FAILED, MODEL_UNAVAILABLE, OCR_FAILED
    }
}

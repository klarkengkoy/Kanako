package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import com.smyddevstudio.kanako.model.ScriptType
import java.io.OutputStream

internal data class CameraExportStyle(
    val fontPixels: Float,
    val script: ScriptType,
    val colors: CameraAnnotationColors,
    val selected: String? = null
)

internal class CameraPhotoExport(
    val lease: CameraPhoto.Lease,
    val lines: List<CameraReadingLine>,
    val style: CameraExportStyle
) : AutoCloseable {
    override fun close() = lease.close()
}

internal fun writeCameraPhotoExport(request: CameraPhotoExport, output: OutputStream) {
    val original = request.lease.bitmap
    val rendered = original.copy(Bitmap.Config.ARGB_8888, true) ?: error("Image copy failed")
    try {
        val layout = layoutCameraAnnotations(
            request.lines, rendered.width, rendered.height, request.style.fontPixels, request.style.script
        )
        drawCameraAnnotations(Canvas(rendered), layout, request.style.colors, request.style.selected)
        check(rendered.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Image export failed" }
    } finally {
        rendered.recycle()
    }
}

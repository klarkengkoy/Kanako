package com.smyddevstudio.kanako.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun cameraExportFileName(): String =
    "Kanako-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT))}.png"

internal class CameraDocumentDestination(
    private val resolver: ContentResolver,
    private val uri: Uri
) : CameraExportDestination<Uri> {
    override fun create() = uri
    override fun open(item: Uri) = resolver.openOutputStream(item, "w")
    override fun publish(item: Uri) = Unit
    override fun delete(item: Uri) {
        resolver.delete(item, null, null)
    }
}

@RequiresApi(29)
internal class CameraPicturesDestination(private val resolver: ContentResolver) : CameraExportDestination<Uri> {
    override fun create(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, cameraExportFileName())
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Kanako")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Cannot create camera export")
    }

    override fun open(item: Uri) = resolver.openOutputStream(item, "w")

    override fun publish(item: Uri) {
        val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        check(resolver.update(item, values, null, null) == 1) { "Cannot publish camera export" }
    }

    override fun delete(item: Uri) {
        resolver.delete(item, null, null)
    }
}

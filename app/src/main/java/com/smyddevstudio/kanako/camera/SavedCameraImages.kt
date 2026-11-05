package com.smyddevstudio.kanako.camera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SavedCameraImage(val uri: Uri, val name: String, val savedAt: Long)

internal class SavedCameraImages(context: Context) {
    private val app = context.applicationContext
    private val history = app.getSharedPreferences("saved_camera_images", Context.MODE_PRIVATE)

    suspend fun load(): List<SavedCameraImage> = withContext(Dispatchers.IO) {
        val documents = documentHistory()
        val pictures = if (Build.VERSION.SDK_INT >= 29) loadPictures() else emptyList()
        (pictures + documents).distinctBy { it.uri }.sortedByDescending { it.savedAt }
    }

    private fun documentHistory(): List<SavedCameraImage> = runCatching {
        val entries = JSONArray(history.getString("documents", "[]"))
        buildList {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val uri = Uri.parse(item.optString("uri"))
                if (uri.scheme == "content") add(SavedCameraImage(uri, item.optString("name", "Kanako.png"), item.optLong("savedAt")))
            }
        }
    }.getOrDefault(emptyList())

    @androidx.annotation.RequiresApi(29)
    private suspend fun loadPictures(): List<SavedCameraImage> = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.Images.Media.IS_PENDING} = 0"
            val images = app.contentResolver.query(
                collection, projection, selection, arrayOf("Pictures/Kanako/", app.packageName),
                "${MediaStore.Images.Media.DATE_ADDED} DESC", signal
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        signal.throwIfCanceled()
                        add(SavedCameraImage(
                            ContentUris.withAppendedId(collection, cursor.getLong(0)),
                            cursor.getString(1) ?: "Kanako.png", cursor.getLong(2) * 1_000L
                        ))
                    }
                }
            } ?: emptyList()
            if (continuation.isActive) continuation.resume(images)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}

internal fun galleryDecodeSampleSize(width: Int, height: Int, maximumEdge: Int): Int {
    require(maximumEdge > 0)
    var sample = 1
    while (maxOf(width, height).toLong() > maximumEdge.toLong() * sample) {
        if (sample > Int.MAX_VALUE / 2) break
        sample *= 2
    }
    return sample
}

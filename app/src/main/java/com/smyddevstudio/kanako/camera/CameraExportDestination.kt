package com.smyddevstudio.kanako.camera

import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext

internal interface CameraExportDestination<T : Any> {
    fun create(): T
    fun open(item: T): OutputStream?
    fun publish(item: T)
    fun delete(item: T)
}

internal suspend fun <T : Any> writeCameraExport(
    destination: CameraExportDestination<T>,
    write: (OutputStream) -> Unit
): T {
    var item: T? = null
    var complete = false
    try {
        val result = withContext(Dispatchers.IO) {
            val created = destination.create()
            item = created
            currentCoroutineContext().ensureActive()
            destination.open(created)?.use(write) ?: error("Cannot open export destination")
            currentCoroutineContext().ensureActive()
            destination.publish(created)
            currentCoroutineContext().ensureActive()
            created
        }
        currentCoroutineContext().ensureActive()
        complete = true
        return result
    } finally {
        if (!complete) item?.let { created ->
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { destination.delete(created) }
            }
        }
    }
}

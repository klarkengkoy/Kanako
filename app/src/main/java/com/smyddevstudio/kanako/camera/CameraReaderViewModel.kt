package com.smyddevstudio.kanako.camera

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class CameraReaderUiState(
    val status: CameraReadingStatus = CameraReadingStatus.STARTING,
    val lines: List<CameraReadingLine> = emptyList(),
    val photo: CameraPhoto? = null,
    val ready: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean? = null
)

internal class CameraReaderViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(CameraReaderUiState())
    val state = mutableState.asStateFlow()
    private val gate = CameraRequestGate()
    private val liveReadings = CameraLiveReadings()
    private var expiry: Job? = null
    private var recognition: Job? = null
    private var export: Job? = null
    private var pendingExport: CameraPhotoExport? = null
    private var photoTicket = 0L
    private var photoRequest = 0L
    private var liveRequest = 0L
    private var exportRequest = 0L

    fun liveTicket() = gate.liveTicket()

    fun resumeLive() {
        if (mutableState.value.photo != null) return
        gate.resumeLive()
        liveReadings.reset()
        mutableState.value = mutableState.value.copy(status = CameraReadingStatus.STARTING, ready = false, lines = emptyList())
    }

    fun pauseLive() {
        gate.pauseLive()
        liveReadings.reset()
        expiry?.cancel()
        if (mutableState.value.photo == null) {
            mutableState.value = mutableState.value.copy(lines = emptyList(), ready = false, status = CameraReadingStatus.STARTING)
        }
    }

    fun invalidateGeometry() {
        gate.invalidateLive()
        liveReadings.reset()
        expiry?.cancel()
        if (mutableState.value.photo == null) mutableState.value = mutableState.value.copy(lines = emptyList())
    }

    fun previewReady(ready: Boolean) {
        if (gate.liveTicket() != null) {
            mutableState.value = mutableState.value.copy(
                ready = ready,
                status = if (ready) CameraReadingStatus.LIVE else CameraReadingStatus.STARTING
            )
        }
    }

    fun cameraError() {
        gate.pauseLive()
        liveReadings.reset()
        expiry?.cancel()
        mutableState.value = mutableState.value.copy(ready = false, lines = emptyList(), status = CameraReadingStatus.CAMERA_ERROR)
    }

    fun receiveLive(ticket: Long, started: Long, result: Result<List<CameraReadingLine>>) {
        if (!gate.acceptsLive(ticket)) return
        val now = SystemClock.elapsedRealtime()
        val lines = liveReadings.update(started, now, result.getOrDefault(emptyList())) ?: return
        val age = now - started
        mutableState.value = mutableState.value.copy(
            lines = lines,
            status = result.exceptionOrNull()?.let(::recognitionFailure)
                ?: if (lines.hasReadings) CameraReadingStatus.LIVE else CameraReadingStatus.NO_TEXT
        )
        val request = ++liveRequest
        expiry?.cancel()
        expiry = viewModelScope.launch {
            delay((CAMERA_LIVE_RESULT_MAX_AGE_MS - age).coerceAtLeast(1))
            if (request == liveRequest && gate.acceptsLive(ticket)) {
                mutableState.value = mutableState.value.copy(lines = emptyList())
            }
        }
    }

    fun capture(session: LiveCameraSession, onCaptured: () -> Unit) {
        if (!mutableState.value.ready) return
        val ticket = gate.beginCapture() ?: return
        liveReadings.reset()
        expiry?.cancel()
        mutableState.value = mutableState.value.copy(status = CameraReadingStatus.CAPTURING, ready = false, lines = emptyList())
        session.capture captured@{ result ->
            if (!gate.acceptCapture(ticket)) {
                result.getOrNull()?.close()
                return@captured
            }
            val photo = result.getOrElse {
                gate.returnToLive()
                gate.resumeLive()
                mutableState.value = CameraReaderUiState(status = CameraReadingStatus.READ_ERROR, ready = true)
                return@captured
            }
            mutableState.value.photo?.close()
            photoTicket = ticket
            mutableState.value = CameraReaderUiState(status = CameraReadingStatus.PROCESSING, photo = photo)
            onCaptured()
            retryPhoto()
        }
    }

    fun retryPhoto() {
        val photo = mutableState.value.photo ?: return
        val ticket = photoTicket
        val request = ++photoRequest
        recognition?.cancel()
        mutableState.value = mutableState.value.copy(status = CameraReadingStatus.PROCESSING, lines = emptyList(), saved = null)
        recognition = viewModelScope.launch {
            try {
                val lines = withContext(Dispatchers.Default) { cameraReadingLines(recognizeCameraPhoto(photo)) }
                if (gate.acceptsPhoto(ticket) && request == photoRequest) {
                    mutableState.value = mutableState.value.copy(
                        lines = lines,
                        status = if (lines.hasReadings) CameraReadingStatus.PHOTO else CameraReadingStatus.NO_TEXT
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (gate.acceptsPhoto(ticket) && request == photoRequest) {
                    mutableState.value = mutableState.value.copy(status = recognitionFailure(error), lines = emptyList())
                }
            }
        }
    }

    fun prepareExport(style: CameraExportStyle): Boolean {
        val state = mutableState.value
        if (state.status != CameraReadingStatus.PHOTO || state.saving) return false
        val lease = state.photo?.acquire() ?: return false
        pendingExport?.close()
        pendingExport = CameraPhotoExport(lease, state.lines, style)
        return true
    }

    fun completeExport(resolver: ContentResolver, uri: Uri?, unavailable: Boolean = false) {
        val request = pendingExport
        pendingExport = null
        if (uri == null) {
            request?.close()
            if (unavailable) mutableState.value = mutableState.value.copy(saved = false)
            return
        }
        if (request == null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { resolver.delete(uri, null, null) } }
            mutableState.value = mutableState.value.copy(saved = false)
            return
        }
        mutableState.value = mutableState.value.copy(saving = true, saved = null)
        val operation = ++exportRequest
        export = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var complete = false
            try {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "w")?.use { output ->
                        writeCameraPhotoExport(request, output)
                    } ?: error("Cannot open export destination")
                    coroutineContext.ensureActive()
                }
                complete = true
                if (operation == exportRequest) mutableState.value = mutableState.value.copy(saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (operation == exportRequest) mutableState.value = mutableState.value.copy(saved = false)
            } finally {
                if (!complete) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    runCatching { resolver.delete(uri, null, null) }
                }
                request.close()
                if (operation == exportRequest) mutableState.value = mutableState.value.copy(saving = false)
            }
        }
    }

    fun returnToLive() {
        gate.returnToLive()
        liveReadings.reset()
        expiry?.cancel()
        recognition?.cancel()
        exportRequest++
        export?.cancel()
        photoRequest++
        pendingExport?.close()
        pendingExport = null
        val photo = mutableState.value.photo
        mutableState.value = CameraReaderUiState()
        photo?.close()
    }

    override fun onCleared() {
        returnToLive()
    }
}

private fun recognitionFailure(error: Throwable): CameraReadingStatus =
    if (error is MlKitException && error.errorCode == MlKitException.UNAVAILABLE) CameraReadingStatus.MODEL_NEEDED
    else CameraReadingStatus.READ_ERROR

internal suspend fun recognizeCameraPhoto(photo: CameraPhoto): Text = suspendCancellableCoroutine { continuation ->
    val lease = photo.acquire()
    if (lease == null) {
        continuation.resumeWithException(IllegalStateException("Camera photo was released"))
    } else {
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        try {
            recognizer.process(InputImage.fromBitmap(lease.bitmap, 0))
                .addOnCompleteListener(Dispatchers.Default.asExecutor()) { task ->
                    try {
                        if (continuation.isActive) {
                            if (task.isSuccessful) continuation.resume(task.result)
                            else continuation.resumeWithException(task.exception ?: IllegalStateException("Recognition failed"))
                        }
                    } finally {
                        recognizer.close()
                        lease.close()
                    }
                }
        } catch (error: Exception) {
            recognizer.close()
            lease.close()
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}

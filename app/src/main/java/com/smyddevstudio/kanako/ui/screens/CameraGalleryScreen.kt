package com.smyddevstudio.kanako.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withClip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraPhotoGesture
import com.smyddevstudio.kanako.camera.SavedCameraImage
import com.smyddevstudio.kanako.camera.SavedCameraImages
import com.smyddevstudio.kanako.camera.fitCameraImage
import com.smyddevstudio.kanako.camera.galleryDecodeSampleSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraGalleryScreen(
    onBack: () -> Unit,
    onOpen: (SavedCameraImage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { SavedCameraImages(context) }
    var version by remember { mutableIntStateOf(0) }
    var images by remember { mutableStateOf<List<SavedCameraImage>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) version++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(repository, version) {
        failed = false
        try {
            images = repository.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
                } },
                actions = { IconButton(onClick = { version++ }) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.gallery_refresh))
                } }
            )
        }
    ) { padding ->
        when {
            failed -> GalleryMessage(R.string.gallery_load_failed, Modifier.padding(padding), onRetry = { version++ })
            images == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            images!!.isEmpty() -> GalleryMessage(R.string.gallery_empty, Modifier.padding(padding))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(144.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(images!!, key = { it.uri.toString() }) { image ->
                    Card(onClick = { onOpen(image) }) {
                        val decoded = rememberGalleryBitmap(image.uri, 384, version)
                        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                            when {
                                decoded.bitmap != null -> Image(
                                    bitmap = decoded.bitmap.asImageBitmap(), contentDescription = image.name,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                )
                                decoded.failed -> Icon(Icons.Rounded.BrokenImage, stringResource(R.string.gallery_image_missing))
                                else -> CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                        Text(image.name, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedCameraImageScreen(
    uri: Uri,
    name: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var version by remember(uri) { mutableIntStateOf(0) }
    val decoded = rememberGalleryBitmap(uri, 2048, version)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
                } },
                actions = { IconButton(onClick = { shareSavedCameraImage(context, uri) }, enabled = decoded.bitmap != null) {
                    Icon(Icons.Rounded.Share, stringResource(R.string.camera_share))
                } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    decoded.bitmap != null -> SavedImageSurface(decoded.bitmap, name, Modifier.fillMaxSize())
                    decoded.failed -> GalleryMessage(R.string.gallery_image_missing, onRetry = { version++ })
                    else -> CircularProgressIndicator()
                }
            }
            if (decoded.bitmap != null) Text(
                stringResource(R.string.gallery_image_hint), Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GalleryMessage(message: Int, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(message), textAlign = TextAlign.Center)
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.camera_retry)) }
    }
}

@Composable
internal fun SavedCameraImageThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val decoded = rememberGalleryBitmap(uri, 192, 0)
    if (decoded.bitmap != null) Image(decoded.bitmap.asImageBitmap(), null,
        modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Icon(Icons.Rounded.BrokenImage, null, modifier, tint = Color.White)
}

internal fun shareSavedCameraImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Kanako", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val errorMessage = R.string.camera_share_failed
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }
}

private data class GalleryBitmap(val bitmap: Bitmap? = null, val failed: Boolean = false)
private val galleryDecodes = Semaphore(3)

@Composable
private fun rememberGalleryBitmap(uri: Uri, edge: Int, version: Int): GalleryBitmap {
    val resolver = LocalContext.current.contentResolver
    val decoded by produceState(GalleryBitmap(), uri, edge, version) {
        value = GalleryBitmap()
        try {
            val bitmap = galleryDecodes.withPermit {
                withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    check(bounds.outWidth > 0 && bounds.outHeight > 0)
                    currentCoroutineContext().ensureActive()
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = galleryDecodeSampleSize(bounds.outWidth, bounds.outHeight, edge)
                    }
                    val loaded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        ?: error("Cannot decode saved image")
                    try {
                        currentCoroutineContext().ensureActive()
                        loaded
                    } catch (cancelled: CancellationException) {
                        loaded.recycle()
                        throw cancelled
                    }
                }
            }
            value = GalleryBitmap(bitmap)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = GalleryBitmap(failed = true)
        }
    }
    return decoded
}

@Composable
private fun SavedImageSurface(bitmap: Bitmap, name: String, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var gesture by remember(bitmap, size) { mutableStateOf(CameraPhotoGesture()) }
    val description = stringResource(R.string.gallery_image_description, name)
    val zoomState = stringResource(R.string.camera_zoom_state, (gesture.zoom * 100).roundToInt())
    val zoomIn = stringResource(R.string.camera_zoom_in)
    val zoomOut = stringResource(R.string.camera_zoom_out)
    val reset = stringResource(R.string.camera_zoom_reset)
    fun zoom(factor: Float): Boolean {
        gesture = gesture.update(factor, 0f, 0f, size.width / 2f, size.height / 2f,
            bitmap.width, bitmap.height, size.width, size.height)
        return true
    }
    Canvas(modifier.onSizeChanged { size = it }
        .pointerInput(bitmap, size) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                gesture = gesture.update(zoom, pan.x, pan.y, centroid.x, centroid.y,
                    bitmap.width, bitmap.height, size.width, size.height)
            }
        }
        .pointerInput(bitmap, size) { detectTapGestures(onDoubleTap = { gesture = CameraPhotoGesture() }) }
        .semantics {
            contentDescription = description
            stateDescription = zoomState
            customActions = listOf(
                CustomAccessibilityAction(zoomIn) { zoom(1.5f) },
                CustomAccessibilityAction(zoomOut) { zoom(1 / 1.5f) },
                CustomAccessibilityAction(reset) { gesture = CameraPhotoGesture(); true }
            )
        }
    ) {
        val fit = fitCameraImage(bitmap.width, bitmap.height, size.width, size.height) ?: return@Canvas
        val scale = fit.scale * gesture.zoom
        drawIntoCanvas { drawing ->
            drawing.nativeCanvas.withClip(0, 0, size.width, size.height) {
                translate((size.width - bitmap.width * scale) / 2f + gesture.x,
                    (size.height - bitmap.height * scale) / 2f + gesture.y)
                scale(scale, scale)
                drawBitmap(bitmap, 0f, 0f, null)
            }
        }
    }
}

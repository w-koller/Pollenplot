package io.github.wkoller.pollenplot.ui.identify

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.wkoller.pollenplot.ml.Bitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The live camera looks at a frame this often, which is plenty for suggestions and spares the battery. */
private const val GLANCE_INTERVAL_MS = 600L

/** Photos are taken at about this size; the models need far less, and the app keeps a 2,048-pixel copy. */
private val CAPTURE_SIZE = Size(2_048, 1_536)

/** A still-photo use case sized for identification. */
fun identifyImageCapture(): ImageCapture = ImageCapture.Builder()
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    .setResolutionSelector(
        ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(CAPTURE_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build(),
    )
    .build()

/**
 * The back camera's preview, with frames handed to [onFrame] for live suggestions. A frame is skipped while the last
 * one is still being looked at, so the preview never stutters.
 */
@Composable
fun CameraPreview(imageCapture: ImageCapture, onFrame: suspend (Bitmap) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val latestOnFrame by rememberUpdatedState(onFrame)

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider { request -> surfaceRequest = request } }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        val busy = AtomicBoolean(false)
        var last = 0L
        val scope = this
        analysis.setAnalyzer(Dispatchers.Default.asExecutor()) { proxy ->
            val now = SystemClock.elapsedRealtime()
            if (now - last < GLANCE_INTERVAL_MS || !busy.compareAndSet(false, true)) {
                proxy.close()
                return@setAnalyzer
            }
            last = now
            val frame = proxy.use { Bitmaps.rotate(it.toBitmap(), it.imageInfo.rotationDegrees) }
            scope.launch {
                try {
                    latestOnFrame(frame)
                } finally {
                    busy.set(false)
                }
            }
        }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
            awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }
    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}

/** Takes a photo and returns it upright. */
suspend fun ImageCapture.takeUprightBitmap(): Bitmap = suspendCancellableCoroutine { continuation ->
    takePicture(
        Dispatchers.Default.asExecutor(),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.use { Bitmaps.rotate(it.toBitmap(), it.imageInfo.rotationDegrees) }
                continuation.resume(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                continuation.resumeWithException(exception)
            }
        },
    )
}

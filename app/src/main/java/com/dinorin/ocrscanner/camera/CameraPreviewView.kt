package com.dinorin.ocrscanner.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dinorin.ocrscanner.ocr.OcrResult
import com.dinorin.ocrscanner.ocr.TextRecognitionAnalyzer
import java.util.concurrent.Executors

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    onResult: (OcrResult) -> Unit,
    onTorchReady: ((Boolean) -> Unit) -> Unit,
    onCaptureReady: (() -> Unit) -> Unit,
    onBitmapCaptured: (Bitmap) -> Unit,
    onZoomChanged: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { TextRecognitionAnalyzer(onResult) }
    val cameraProviderRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
    }
    val cameraRef = remember { arrayOfNulls<Camera>(1) }

    LaunchedEffect(isScanning) {
        analyzer.setActive(isScanning)
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        future.addListener({
            val cameraProvider = future.get()
            cameraProviderRef[0] = cameraProvider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analyzerExecutor, analyzer) }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                    imageCapture
                )
                cameraRef[0] = camera

                onTorchReady { enabled ->
                    camera.cameraControl.enableTorch(enabled)
                }
                onCaptureReady {
                    imageCapture.takePicture(
                        mainExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = image.toBitmap()
                                val degrees = image.imageInfo.rotationDegrees
                                image.close()
                                val rotated = if (degrees != 0) {
                                    val m = Matrix()
                                    m.postRotate(degrees.toFloat())
                                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                } else bmp

                                // Crop bitmap theo FILL_CENTER của PreviewView
                                // để captured image = đúng cái thấy trên màn hình
                                val vW = previewView.width.toFloat()
                                val vH = previewView.height.toFloat()
                                val bW = rotated.width.toFloat()
                                val bH = rotated.height.toFloat()
                                val final = if (vW > 0 && vH > 0) {
                                    val scale = maxOf(vW / bW, vH / bH)
                                    val cropW = (vW / scale).toInt().coerceIn(1, rotated.width)
                                    val cropH = (vH / scale).toInt().coerceIn(1, rotated.height)
                                    val cropX = ((bW - cropW) / 2f).toInt()
                                    val cropY = ((bH - cropH) / 2f).toInt()
                                    Bitmap.createBitmap(rotated, cropX, cropY, cropW, cropH)
                                } else rotated

                                onBitmapCaptured(final)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                exception.printStackTrace()
                            }
                        }
                    )
                }

                // Pinch-to-zoom + tap-to-focus on PreviewView
                val zoomState = camera.cameraInfo.zoomState
                var currentZoom = 1f

                val scaleDetector = ScaleGestureDetector(context,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val zoomRatio = zoomState.value
                            val minZoom = zoomRatio?.minZoomRatio ?: 1f
                            val maxZoom = zoomRatio?.maxZoomRatio ?: 8f
                            currentZoom = (currentZoom * detector.scaleFactor)
                                .coerceIn(minZoom, maxZoom)
                            camera.cameraControl.setZoomRatio(currentZoom)
                            onZoomChanged(currentZoom)
                            return true
                        }
                    })

                previewView.setOnTouchListener { view, event ->
                    scaleDetector.onTouchEvent(event)
                    if (!scaleDetector.isInProgress &&
                        event.action == MotionEvent.ACTION_UP &&
                        event.pointerCount == 1) {
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera.cameraControl.startFocusAndMetering(action)
                        view.performClick()
                    }
                    true
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, mainExecutor)

        onDispose {
            cameraProviderRef[0]?.unbindAll()
            analyzerExecutor.shutdown()
            analyzer.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

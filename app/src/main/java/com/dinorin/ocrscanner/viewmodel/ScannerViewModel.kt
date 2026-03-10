package com.dinorin.ocrscanner.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dinorin.ocrscanner.ocr.OcrBox
import com.dinorin.ocrscanner.ocr.OcrResult
import com.dinorin.ocrscanner.utils.RegexExtractor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScannerViewModel : ViewModel() {

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _extractedMatches = MutableStateFlow<List<RegexExtractor.ExtractedMatch>>(emptyList())
    val extractedMatches: StateFlow<List<RegexExtractor.ExtractedMatch>> = _extractedMatches.asStateFlow()

    private val _ocrResult = MutableStateFlow<OcrResult?>(null)
    val ocrResult: StateFlow<OcrResult?> = _ocrResult.asStateFlow()

    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    var torchController: ((Boolean) -> Unit)? = null
    var captureController: (() -> Unit)? = null

    fun toggleGrid() { _showGrid.value = !_showGrid.value }
    fun onZoomChanged(zoom: Float) { _zoomRatio.value = zoom }

    fun onOcrResult(result: OcrResult) {
        if (!_isScanning.value || _capturedBitmap.value != null) return
        _ocrResult.value = result
        _recognizedText.value = result.text
        _extractedMatches.value = RegexExtractor.extract(result.text)
    }

    fun triggerCapture() {
        captureController?.invoke()
    }

    fun onBitmapCaptured(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _isCapturing.value = true
        _ocrResult.value = null

        viewModelScope.launch {
            val visionResult = withContext(Dispatchers.IO) {
                runCatching {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val visionText = suspendCancellableCoroutine { cont ->
                        recognizer.process(inputImage)
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resumeWithException(it) }
                        cont.invokeOnCancellation { recognizer.close() }
                    }
                    recognizer.close()
                    visionText
                }
            }
            visionResult.onSuccess { visionText ->
                val blocks = visionText.textBlocks.map { block ->
                    OcrBox(block.cornerPoints?.pointsToFloatArray(), block.boundingBox, block.text)
                }
                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        OcrBox(line.cornerPoints?.pointsToFloatArray(), line.boundingBox, line.text)
                    }
                }
                val result = OcrResult(
                    text          = visionText.text,
                    blocks        = blocks,
                    lines         = lines,
                    imageWidth    = bitmap.width,
                    imageHeight   = bitmap.height,
                    imageRotation = 0,
                    ocrFps        = 0f,
                    cameraFps     = 0f,
                    processingMs  = 0
                )
                _ocrResult.value = result
                _recognizedText.value = visionText.text
                _extractedMatches.value = RegexExtractor.extract(visionText.text)
            }
            _isCapturing.value = false
        }
    }

    fun clearCapture() {
        _capturedBitmap.value = null
        _ocrResult.value = null
        _recognizedText.value = ""
        _extractedMatches.value = emptyList()
    }

    fun toggleTorch() {
        val next = !_isTorchOn.value
        _isTorchOn.value = next
        torchController?.invoke(next)
    }

    fun clearResults() {
        _recognizedText.value = ""
        _extractedMatches.value = emptyList()
        _ocrResult.value = null
    }
}

private fun Array<android.graphics.Point>.pointsToFloatArray(): FloatArray {
    val fa = FloatArray(size * 2)
    forEachIndexed { i, p -> fa[i * 2] = p.x.toFloat(); fa[i * 2 + 1] = p.y.toFloat() }
    return fa
}

package com.dinorin.ocrscanner.ocr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class TextRecognitionAnalyzer(
    private val onResult: (OcrResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isActive    = AtomicBoolean(true)
    private val isProcessing = AtomicBoolean(false)

    // FPS tracking — sliding window 2s
    private val FPS_WINDOW_MS = 2000L
    private val ocrTimestamps    = ArrayDeque<Long>()   // mỗi frame OCR hoàn thành
    private val cameraTimestamps = ArrayDeque<Long>()   // mỗi frame camera đến

    fun setActive(active: Boolean) = isActive.set(active)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // Đếm camera FPS (mọi frame nhận được)
        trackTimestamp(cameraTimestamps)

        if (!isActive.get() || isProcessing.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        isProcessing.set(true)
        val startMs  = System.currentTimeMillis()
        val imgW     = mediaImage.width
        val imgH     = mediaImage.height
        val rotation = imageProxy.imageInfo.rotationDegrees

        recognizer.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { visionText ->
                val processingMs = System.currentTimeMillis() - startMs

                // Đếm OCR FPS (frame xử lý xong)
                trackTimestamp(ocrTimestamps)

                val blocks = visionText.textBlocks.map { block ->
                    OcrBox(block.cornerPoints?.toFloatArray(), block.boundingBox, block.text)
                }
                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        OcrBox(line.cornerPoints?.toFloatArray(), line.boundingBox, line.text)
                    }
                }

                onResult(
                    OcrResult(
                        text          = visionText.text,
                        blocks        = blocks,
                        lines         = lines,
                        imageWidth    = imgW,
                        imageHeight   = imgH,
                        imageRotation = rotation,
                        ocrFps        = calcFps(ocrTimestamps),
                        cameraFps     = calcFps(cameraTimestamps),
                        processingMs  = processingMs
                    )
                )
            }
            .addOnFailureListener { /* ignore */ }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    private fun trackTimestamp(queue: ArrayDeque<Long>) {
        val now = System.currentTimeMillis()
        synchronized(queue) {
            queue.addLast(now)
            while (queue.isNotEmpty() && now - queue.first() > FPS_WINDOW_MS) {
                queue.removeFirst()
            }
        }
    }

    private fun calcFps(queue: ArrayDeque<Long>): Float {
        synchronized(queue) {
            if (queue.size < 2) return 0f
            val span = queue.last() - queue.first()
            if (span == 0L) return 0f
            return (queue.size - 1) * 1000f / span
        }
    }

    fun close() = recognizer.close()
}

private fun Array<android.graphics.Point>.toFloatArray(): FloatArray {
    val fa = FloatArray(size * 2)
    forEachIndexed { i, p -> fa[i * 2] = p.x.toFloat(); fa[i * 2 + 1] = p.y.toFloat() }
    return fa
}

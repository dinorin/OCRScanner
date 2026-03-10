package com.dinorin.ocrscanner.ocr

import android.graphics.Rect

/**
 * Một bounding box của text block hoặc line.
 * [pts] = [x0,y0, x1,y1, x2,y2, x3,y3] theo chiều kim đồng hồ, có thể nghiêng.
 * [boundingBox] dùng làm fallback khi [pts] == null.
 */
data class OcrBox(
    val pts: FloatArray?,        // 8 giá trị: 4 điểm góc (upright coord space)
    val boundingBox: Rect?,
    val text: String = ""
)

data class OcrResult(
    val text: String,
    val blocks: List<OcrBox>,
    val lines: List<OcrBox>,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageRotation: Int,
    val ocrFps: Float,          // số frame OCR xử lý được mỗi giây
    val cameraFps: Float,       // số frame camera đưa vào mỗi giây
    val processingMs: Long      // thời gian ML Kit xử lý 1 frame (ms)
)

package com.dinorin.ocrscanner.ui.overlay

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dinorin.ocrscanner.ocr.OcrBox
import com.dinorin.ocrscanner.ocr.OcrResult
import kotlin.math.max

@Composable
fun BoundingBoxOverlay(
    result: OcrResult?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (result == null) return@Canvas
        if (size.width == 0f || size.height == 0f) return@Canvas

        // ML Kit trả về cornerPoints trong upright (post-rotation) coord space.
        // Chỉ cần scale + offset để map sang view coords.
        val logicalW = if (result.imageRotation == 90 || result.imageRotation == 270)
            result.imageHeight.toFloat() else result.imageWidth.toFloat()
        val logicalH = if (result.imageRotation == 90 || result.imageRotation == 270)
            result.imageWidth.toFloat() else result.imageHeight.toFloat()

        if (logicalW == 0f || logicalH == 0f) return@Canvas

        // FILL_CENTER — giống PreviewView default
        val scale   = max(size.width / logicalW, size.height / logicalH)
        val offsetX = (size.width  - logicalW * scale) / 2f
        val offsetY = (size.height - logicalH * scale) / 2f

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(offsetX, offsetY)
        }

        val blockStroke = 2.5f.dp.toPx()
        val lineStroke  = 1.5f.dp.toPx()
        val accentLen   = 14.dp.toPx()

        // Block level — cyan
        for (box in result.blocks) {
            drawOcrBox(
                box         = box,
                matrix      = matrix,
                borderColor = Color(0xFF00E5FF),
                fillColor   = Color(0x1500E5FF),
                strokeWidth = blockStroke,
                accentLen   = accentLen
            )
        }

        // Line level — green, no fill
        for (box in result.lines) {
            drawOcrBox(
                box         = box,
                matrix      = matrix,
                borderColor = Color(0xCC00FF88),
                fillColor   = Color.Transparent,
                strokeWidth = lineStroke,
                accentLen   = 0f
            )
        }
    }
}

// ---------------------------------------------------------------------------

private fun DrawScope.drawOcrBox(
    box: OcrBox,
    matrix: Matrix,
    borderColor: Color,
    fillColor: Color,
    strokeWidth: Float,
    accentLen: Float
) {
    val pts = box.pts?.copyOf()   // [x0,y0, x1,y1, x2,y2, x3,y3]

    if (pts != null && pts.size == 8) {
        // Map 4 corner points to view coordinates
        matrix.mapPoints(pts)

        val path = Path().apply {
            moveTo(pts[0], pts[1])
            lineTo(pts[2], pts[3])
            lineTo(pts[4], pts[5])
            lineTo(pts[6], pts[7])
            close()
        }

        // Fill
        if (fillColor != Color.Transparent) {
            drawPath(path, fillColor)
        }
        // Border
        drawPath(path, borderColor, style = Stroke(width = strokeWidth))

        // Corner accents along edge directions
        if (accentLen > 0f) {
            drawTiltedCornerAccents(pts, borderColor, strokeWidth * 1.5f, accentLen)
        }

    } else {
        // Fallback: axis-aligned boundingBox
        val rect = box.boundingBox ?: return
        val rf = RectF(
            rect.left.toFloat(), rect.top.toFloat(),
            rect.right.toFloat(), rect.bottom.toFloat()
        ).also { matrix.mapRect(it) }

        if (fillColor != Color.Transparent) {
            drawRect(fillColor, Offset(rf.left, rf.top), Size(rf.width(), rf.height()))
        }
        drawRect(
            color    = borderColor,
            topLeft  = Offset(rf.left, rf.top),
            size     = Size(rf.width(), rf.height()),
            style    = Stroke(width = strokeWidth)
        )
        if (accentLen > 0f) {
            drawAxisCornerAccents(rf.left, rf.top, rf.right, rf.bottom, borderColor, strokeWidth * 1.5f, accentLen)
        }
    }
}

/**
 * Vẽ accent tại 4 góc dọc theo cạnh của polygon nghiêng.
 * pts = [x0,y0, x1,y1, x2,y2, x3,y3] (đã transform sang view coords)
 */
private fun DrawScope.drawTiltedCornerAccents(
    pts: FloatArray,
    color: Color,
    strokeWidth: Float,
    len: Float
) {
    val n = 4
    for (i in 0 until n) {
        val cx = pts[i * 2]
        val cy = pts[i * 2 + 1]

        // Cạnh trước (về phía điểm trước)
        val prevI = (i + n - 1) % n
        val px = pts[prevI * 2]; val py = pts[prevI * 2 + 1]
        val d1 = dist(cx, cy, px, py)
        if (d1 > 0f) {
            val t = len / d1
            drawLine(color, Offset(cx, cy), Offset(cx + (px - cx) * t, cy + (py - cy) * t), strokeWidth)
        }

        // Cạnh sau (về phía điểm sau)
        val nextI = (i + 1) % n
        val nx = pts[nextI * 2]; val ny = pts[nextI * 2 + 1]
        val d2 = dist(cx, cy, nx, ny)
        if (d2 > 0f) {
            val t = len / d2
            drawLine(color, Offset(cx, cy), Offset(cx + (nx - cx) * t, cy + (ny - cy) * t), strokeWidth)
        }
    }
}

/** Axis-aligned corner accents (fallback khi không có cornerPoints) */
private fun DrawScope.drawAxisCornerAccents(
    l: Float, t: Float, r: Float, b: Float,
    color: Color, strokeWidth: Float, len: Float
) {
    drawLine(color, Offset(l, t + len), Offset(l, t), strokeWidth)
    drawLine(color, Offset(l, t), Offset(l + len, t), strokeWidth)

    drawLine(color, Offset(r - len, t), Offset(r, t), strokeWidth)
    drawLine(color, Offset(r, t), Offset(r, t + len), strokeWidth)

    drawLine(color, Offset(l, b - len), Offset(l, b), strokeWidth)
    drawLine(color, Offset(l, b), Offset(l + len, b), strokeWidth)

    drawLine(color, Offset(r - len, b), Offset(r, b), strokeWidth)
    drawLine(color, Offset(r, b), Offset(r, b - len), strokeWidth)
}

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1; val dy = y2 - y1
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

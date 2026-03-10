package com.dinorin.ocrscanner.ui.screen

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dinorin.ocrscanner.ocr.OcrBox
import com.dinorin.ocrscanner.ocr.OcrResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CapturedImageScreen(
    bitmap: Bitmap,
    ocrResult: OcrResult?,
    isProcessing: Boolean,
    onClose: () -> Unit
) {
    var selectedBox   by remember { mutableStateOf<OcrBox?>(null) }
    var selectedBoxes by remember { mutableStateOf<List<OcrBox>>(emptyList()) }
    var imgScale      by remember { mutableFloatStateOf(1f) }
    var imgOffset     by remember { mutableStateOf(Offset.Zero) }
    var isSelectionMode  by remember { mutableStateOf(false) }
    var selectionStartLog by remember { mutableStateOf<Offset?>(null) }
    var selectionEndLog   by remember { mutableStateOf<Offset?>(null) }

    val showPanel = selectedBox != null || selectedBoxes.isNotEmpty()

    BackHandler {
        when {
            showPanel          -> { selectedBox = null; selectedBoxes = emptyList()
                                   selectionStartLog = null; selectionEndLog = null }
            isSelectionMode    -> isSelectionMode = false
            imgScale > 1f      -> { imgScale = 1f; imgOffset = Offset.Zero }
            else               -> onClose()
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        imgScale = (imgScale * zoomChange).coerceIn(1f, 8f)
        imgOffset += panChange
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val viewW = with(density) { maxWidth.toPx() }
            val viewH = with(density) { maxHeight.toPx() }

            val imgW = ocrResult?.imageWidth?.toFloat()  ?: bitmap.width.toFloat()
            val imgH = ocrResult?.imageHeight?.toFloat() ?: bitmap.height.toFloat()
            // Fit (không crop): scale nhỏ nhất để toàn bộ ảnh vừa khung
            val fitScale = min(viewW / imgW, viewH / imgH)
            val fitOffX  = (viewW - imgW * fitScale) / 2f
            val fitOffY  = (viewH - imgH * fitScale) / 2f
            // Clamp pan: chỉ pan khi ảnh lớn hơn màn hình sau zoom
            val maxOffX = max(0f, (imgW * fitScale * imgScale - viewW) / 2f)
            val maxOffY = max(0f, (imgH * fitScale * imgScale - viewH) / 2f)
            imgOffset = Offset(
                imgOffset.x.coerceIn(-maxOffX, maxOffX),
                imgOffset.y.coerceIn(-maxOffY, maxOffY)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = imgScale,
                        scaleY = imgScale,
                        translationX = imgOffset.x,
                        translationY = imgOffset.y
                    )
                    .transformable(state = transformableState, enabled = !isSelectionMode)
                    .pointerInput(isSelectionMode, ocrResult, imgScale, imgOffset) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f

                        // Screen position → logical (pre-graphicsLayer) position
                        fun toLogical(screen: Offset) = Offset(
                            cx + (screen.x - imgOffset.x - cx) / imgScale,
                            cy + (screen.y - imgOffset.y - cy) / imgScale
                        )
                        // Logical position → image pixel position
                        fun toImage(log: Offset) = Offset(
                            (log.x - fitOffX) / fitScale,
                            (log.y - fitOffY) / fitScale
                        )

                        if (isSelectionMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val log = toLogical(offset)
                                    selectionStartLog = log
                                    selectionEndLog   = log
                                    selectedBoxes = emptyList()
                                    selectedBox   = null
                                },
                                onDrag = { change, _ ->
                                    selectionEndLog = toLogical(change.position)
                                },
                                onDragEnd = {
                                    val s = selectionStartLog
                                    val e = selectionEndLog
                                    if (ocrResult != null && s != null && e != null) {
                                        val imgS = toImage(s)
                                        val imgE = toImage(e)
                                        val selRect = android.graphics.Rect(
                                            min(imgS.x, imgE.x).toInt(),
                                            min(imgS.y, imgE.y).toInt(),
                                            max(imgS.x, imgE.x).toInt(),
                                            max(imgS.y, imgE.y).toInt()
                                        )
                                        selectedBoxes = ocrResult.lines.filter { box ->
                                            box.boundingBox?.let {
                                                android.graphics.Rect.intersects(it, selRect)
                                            } == true
                                        }
                                    }
                                    isSelectionMode = false
                                },
                                onDragCancel = {
                                    isSelectionMode   = false
                                    selectionStartLog = null
                                    selectionEndLog   = null
                                }
                            )
                        } else {
                            detectTapGestures(
                                onDoubleTap = {
                                    imgScale  = 1f
                                    imgOffset = Offset.Zero
                                },
                                onTap = { tap ->
                                    if (ocrResult == null || isProcessing || imgW <= 0f) return@detectTapGestures
                                    // Clear region selection on tap outside
                                    if (selectedBoxes.isNotEmpty()) {
                                        selectedBoxes = emptyList()
                                        selectionStartLog = null
                                        selectionEndLog   = null
                                        return@detectTapGestures
                                    }
                                    val log  = toLogical(tap)
                                    val imgP = toImage(log)
                                    val tapped = ocrResult.lines.firstOrNull { box ->
                                        box.boundingBox?.contains(imgP.x.roundToInt(), imgP.y.roundToInt()) == true
                                    }
                                    selectedBox = tapped
                                }
                            )
                        }
                    }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                if (!isProcessing && ocrResult != null && imgW > 0f && imgH > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val matrix = android.graphics.Matrix().apply {
                            postScale(fitScale, fitScale)
                            postTranslate(fitOffX, fitOffY)
                        }
                        for (box in ocrResult.lines) {
                            val inRegion = selectedBoxes.contains(box)
                            drawOcrLine(
                                box,
                                matrix,
                                isSelected = box === selectedBox || inRegion,
                                isRegion   = inRegion
                            )
                        }

                        // Draw selection rectangle
                        val s = selectionStartLog
                        val e = selectionEndLog
                        if (isSelectionMode && s != null && e != null) {
                            val left   = min(s.x, e.x)
                            val top    = min(s.y, e.y)
                            val right  = max(s.x, e.x)
                            val bottom = max(s.y, e.y)
                            drawRect(
                                color   = Color.White.copy(alpha = 0.15f),
                                topLeft = Offset(left, top),
                                size    = Size(right - left, bottom - top)
                            )
                            drawRect(
                                color   = Color.White,
                                topLeft = Offset(left, top),
                                size    = Size(right - left, bottom - top),
                                style   = Stroke(
                                    width      = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Loading overlay
        if (isProcessing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Color.White) }
        }

        // Top-left: close button
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
        }

        // Top-right: region-select toggle (only when OCR ready)
        if (!isProcessing && ocrResult != null) {
            IconButton(
                onClick = {
                    isSelectionMode   = !isSelectionMode
                    selectionStartLog = null
                    selectionEndLog   = null
                    selectedBoxes     = emptyList()
                    selectedBox       = null
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSelectionMode) Color(0xFF007AFF).copy(alpha = 0.8f)
                                     else Color.Black.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.CropFree, contentDescription = "Chọn vùng", tint = Color.White)
            }
        }

        // Top-center: hint text
        if (!isProcessing && ocrResult != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                val hint = when {
                    isSelectionMode -> "Kéo để chọn vùng chữ"
                    imgScale > 1f   -> "Nhấn đúp để reset zoom"
                    else            -> "Nhấn vào chữ để chọn"
                }
                Text(
                    text = hint,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom panel (single box OR region selection)
        AnimatedVisibility(
            visible = showPanel,
            enter = slideInVertically { it },
            exit  = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val panelText = when {
                selectedBoxes.isNotEmpty() -> selectedBoxes.joinToString("\n") { it.text }
                else                       -> selectedBox?.text ?: ""
            }
            Surface(
                color = Color(0xFF1C1C1E),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp).height(4.dp)
                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        )
                    }

                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                setTextIsSelectable(true)
                                setTextColor(AndroidColor.WHITE)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                                val dp = resources.displayMetrics.density
                                setPadding(
                                    (20 * dp).toInt(), (12 * dp).toInt(),
                                    (20 * dp).toInt(), (24 * dp).toInt()
                                )
                                customSelectionActionModeCallback = object : ActionMode.Callback {
                                    override fun onCreateActionMode(mode: ActionMode, menu: Menu) = true
                                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                                        for (i in 0 until menu.size()) {
                                            menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                                        }
                                        return true
                                    }
                                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                                    override fun onDestroyActionMode(mode: ActionMode) {}
                                }
                            }
                        },
                        update = { tv -> tv.text = panelText },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOcrLine(
    box: OcrBox,
    matrix: android.graphics.Matrix,
    isSelected: Boolean,
    isRegion: Boolean = false
) {
    val borderColor = when {
        isRegion   -> Color(0xFF4FC3F7)   // light blue for region selection
        isSelected -> Color(0xFFFFD700)   // gold for single tap
        else       -> Color(0xBB00FFAA)   // green default
    }
    val fillColor = when {
        isRegion   -> Color(0x334FC3F7)
        isSelected -> Color(0x44FFD700)
        else       -> Color(0x1500FFAA)
    }
    val stroke = if (isSelected || isRegion) 2.5.dp.toPx() else 1.5.dp.toPx()

    val pts = box.pts?.copyOf()
    if (pts != null && pts.size == 8) {
        matrix.mapPoints(pts)
        val path = Path().apply {
            moveTo(pts[0], pts[1])
            lineTo(pts[2], pts[3])
            lineTo(pts[4], pts[5])
            lineTo(pts[6], pts[7])
            close()
        }
        drawPath(path, fillColor)
        drawPath(path, borderColor, style = Stroke(width = stroke))
    } else {
        val rect = box.boundingBox ?: return
        val rf = RectF(
            rect.left.toFloat(), rect.top.toFloat(),
            rect.right.toFloat(), rect.bottom.toFloat()
        ).also { matrix.mapRect(it) }
        drawRect(fillColor, Offset(rf.left, rf.top), Size(rf.width(), rf.height()))
        drawRect(
            borderColor,
            Offset(rf.left, rf.top),
            Size(rf.width(), rf.height()),
            style = Stroke(width = stroke)
        )
    }
}

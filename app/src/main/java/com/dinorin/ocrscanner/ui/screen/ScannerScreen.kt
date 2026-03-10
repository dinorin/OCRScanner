package com.dinorin.ocrscanner.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinorin.ocrscanner.camera.CameraPreviewView
import com.dinorin.ocrscanner.utils.RegexExtractor
import com.dinorin.ocrscanner.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = viewModel()
) {
    val recognizedText   by viewModel.recognizedText.collectAsStateWithLifecycle()
    val extractedMatches by viewModel.extractedMatches.collectAsStateWithLifecycle()
    val isTorchOn        by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val capturedBitmap   by viewModel.capturedBitmap.collectAsStateWithLifecycle()
    val isCapturing      by viewModel.isCapturing.collectAsStateWithLifecycle()
    val ocrResult        by viewModel.ocrResult.collectAsStateWithLifecycle()
    val showGrid         by viewModel.showGrid.collectAsStateWithLifecycle()
    val zoomRatio        by viewModel.zoomRatio.collectAsStateWithLifecycle()
    val context          = LocalContext.current
    val scope            = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(context.contentResolver, uri)
                            ) { decoder, _, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            context.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        }
                    }.getOrNull()
                }
                if (bmp != null) viewModel.onBitmapCaptured(bmp)
            }
        }
    }

    // Show zoom indicator briefly after zoom changes
    var zoomIndicatorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(zoomRatio) {
        if (zoomRatio != 1f) {
            zoomIndicatorVisible = true
            delay(1500)
            zoomIndicatorVisible = false
        }
    }

    if (capturedBitmap != null) {
        CapturedImageScreen(
            bitmap      = capturedBitmap!!,
            ocrResult   = ocrResult,
            isProcessing = isCapturing,
            onClose     = viewModel::clearCapture
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewView(
            modifier         = Modifier.fillMaxSize(),
            isScanning       = true,
            onResult         = viewModel::onOcrResult,
            onTorchReady     = { controller -> viewModel.torchController = controller },
            onCaptureReady   = { trigger -> viewModel.captureController = trigger },
            onBitmapCaptured = viewModel::onBitmapCaptured,
            onZoomChanged    = viewModel::onZoomChanged
        )

        // Grid overlay (rule of thirds)
        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val gridColor = Color.White.copy(alpha = 0.35f)
                val stroke = Stroke(width = 1.dp.toPx())
                // Vertical lines
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = stroke.width)
                drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = stroke.width)
                // Horizontal lines
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = stroke.width)
                drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = stroke.width)
            }
        }

        // Dark gradient top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OCR Scanner",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Grid toggle
                IconButton(
                    onClick = viewModel::toggleGrid,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (showGrid) Color.White.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                        contentDescription = "Grid",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Torch toggle
                IconButton(
                    onClick = viewModel::toggleTorch,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isTorchOn) Color.Yellow.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = if (isTorchOn) Color.Yellow else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (recognizedText.isNotBlank()) {
                    IconButton(
                        onClick = viewModel::clearResults,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Zoom indicator
        AnimatedVisibility(
            visible = zoomIndicatorVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "%.1fx".format(zoomRatio),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Regex match chips
            AnimatedVisibility(
                visible = extractedMatches.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(extractedMatches) { match ->
                        MatchChip(
                            match = match,
                            onClick = { copyToClipboard(context, match.value) }
                        )
                    }
                }
            }

            // Raw text preview
            AnimatedVisibility(
                visible = recognizedText.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = recognizedText.take(120).let {
                                if (recognizedText.length > 120) "$it…" else it
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { copyToClipboard(context, recognizedText) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Shutter + gallery row
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery picker
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Thư viện",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Shutter button
                Surface(
                    onClick = viewModel::triggerCapture,
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(72.dp)
                        .padding(bottom = 4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.08f),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Chụp",
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Placeholder to balance the row
                Spacer(modifier = Modifier.size(52.dp))
            }
        }
    }
}

@Composable
private fun MatchChip(
    match: RegexExtractor.ExtractedMatch,
    onClick: () -> Unit
) {
    val (bgColor, textColor) = when (match.type) {
        RegexExtractor.MatchType.PHONE     -> Color(0xFF1B5E20) to Color(0xFF81C784)
        RegexExtractor.MatchType.EMAIL     -> Color(0xFF0D47A1) to Color(0xFF90CAF9)
        RegexExtractor.MatchType.URL       -> Color(0xFF4A148C) to Color(0xFFCE93D8)
        RegexExtractor.MatchType.DATE      -> Color(0xFFE65100) to Color(0xFFFFCC02)
        RegexExtractor.MatchType.ID_NUMBER -> Color(0xFF880E4F) to Color(0xFFF48FB1)
        RegexExtractor.MatchType.NUMBER    -> Color(0xFF37474F) to Color(0xFFB0BEC5)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor.copy(alpha = 0.85f),
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(text = match.type.emoji, fontSize = 14.sp)
            Text(
                text = match.value,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("OCR Result", text))
    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
}

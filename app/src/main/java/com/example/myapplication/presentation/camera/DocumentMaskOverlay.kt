package com.example.myapplication.presentation.camera

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Tính toán vùng khung trắng [Rect] theo pixel của container, đảm bảo vừa tỉ lệ chuẩn
 * của loại giấy tờ (A4 dọc / CCCD ngang) mà không đè lên top bar, text hướng dẫn hoặc bottom bar.
 */
fun calculateDocumentFrameRect(
    containerWidthPx: Float,
    containerHeightPx: Float,
    topReservedPx: Float,
    bottomReservedPx: Float,
    frameType: DocumentFrameType,
    density: Density
): Rect {
    if (containerWidthPx <= 0f || containerHeightPx <= 0f) return Rect.Zero

    val horizontalMarginPx = with(density) { frameType.horizontalMarginDp.dp.toPx() }
    val topGapPx = with(density) { 12.dp.toPx() }
    val guideReservePx = with(density) { 44.dp.toPx() }

    val availableWidth = (containerWidthPx - horizontalMarginPx * 2f).coerceAtLeast(1f)
    val availableTop = topReservedPx + topGapPx
    val availableBottom = (containerHeightPx - bottomReservedPx - guideReservePx)
        .coerceAtLeast(availableTop + 1f)
    val availableHeight = (availableBottom - availableTop).coerceAtLeast(1f)

    val ratio = frameType.aspectRatioWidthOverHeight
    var frameW = availableWidth
    var frameH = frameW / ratio
    if (frameH > availableHeight) {
        frameH = availableHeight
        frameW = frameH * ratio
    }

    val left = (containerWidthPx - frameW) / 2f
    val top = availableTop + (availableHeight - frameH) / 2f
    return Rect(
        offset = Offset(left, top),
        size = Size(frameW, frameH)
    )
}

/**
 * Lớp mặt nạ viewfinder gồm:
 * 1. Vùng ngoài khung: nền camera/ảnh bị làm mờ (blur) + lớp phủ tối bán trong suốt.
 * 2. Vùng trong khung: khoét trong suốt hoàn toàn bằng [BlendMode.Clear] để hiển thị sắc nét 100%.
 * 3. Viền trắng bo góc sắc nét bao quanh khung.
 */
@Composable
fun DocumentMaskOverlay(
    frameRect: Rect,
    cornerRadiusDp: Int,
    blurredPreviewBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color(0x94000000),
    customBlurredBackground: (@Composable BoxScope.() -> Unit)? = null
) {
    if (frameRect == Rect.Zero) return

    Box(modifier = modifier.fillMaxSize()) {
        // Lớp ngoài khung: Blur + Scrim tối, khoét lỗ trong suốt tại frameRect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(color = scrimColor)

                    val cornerPx = cornerRadiusDp.dp.toPx()
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = frameRect.topLeft,
                        size = frameRect.size,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        blendMode = BlendMode.Clear
                    )
                }
        ) {
            if (customBlurredBackground != null) {
                customBlurredBackground()
            } else if (blurredPreviewBitmap != null) {
                val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(radius = 18.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                } else {
                    Modifier
                }
                Image(
                    bitmap = blurredPreviewBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurModifier)
                )
            }
        }

        // Viền trắng bo góc quanh khung chụp
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerPx = cornerRadiusDp.dp.toPx()
            val strokePx = 2.5.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = frameRect.topLeft,
                size = frameRect.size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx)
            )
        }
    }
}

package com.devnet.adbcompanion.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devnet.adbcompanion.domain.model.PingSample
import com.devnet.adbcompanion.presentation.theme.DevNetColors
import kotlin.math.max

@Composable
fun LatencyChart(
    samples: List<PingSample>,
    modifier: Modifier = Modifier
) {
    val colors = DevNetColors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(colors.Background, RoundedCornerShape(12.dp))
            .border(1.dp, colors.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (samples.isEmpty()) {
            Text(
                text = "Chưa có dữ liệu ping — bấm \"Đo ngay\" hoặc \"Theo dõi\"",
                color = colors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        val maxScale = max(80.0, (samples.mapNotNull { it.rttMs }.maxOrNull() ?: 40.0) * 1.15)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val y25 = h - (25.0 / maxScale).toFloat() * h
            if (y25 in 4f..(h - 4f)) {
                drawLine(
                    color = colors.Emerald.copy(alpha = 0.25f),
                    start = Offset(0f, y25),
                    end = Offset(w, y25),
                    strokeWidth = 1.5f,
                    pathEffect = dashEffect
                )
            }

            val y60 = h - (60.0 / maxScale).toFloat() * h
            if (y60 in 4f..(h - 4f)) {
                drawLine(
                    color = colors.Amber.copy(alpha = 0.25f),
                    start = Offset(0f, y60),
                    end = Offset(w, y60),
                    strokeWidth = 1.5f,
                    pathEffect = dashEffect
                )
            }

            val slotCount = 30
            val slotWidth = w / slotCount
            val barWidth = (slotWidth * 0.64f).coerceAtLeast(3f)
            val startIndex = slotCount - samples.size

            samples.forEachIndexed { index, sample ->
                val x = (startIndex + index) * slotWidth + (slotWidth - barWidth) / 2f
                val rtt = sample.rttMs
                if (rtt == null) {
                    drawRoundRect(
                        color = colors.Red,
                        topLeft = Offset(x, h - 8.dp.toPx()),
                        size = Size(barWidth, 8.dp.toPx()),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                } else {
                    val ratio = (rtt / maxScale).toFloat().coerceIn(0.08f, 1f)
                    val barHeight = h * ratio
                    val barColor = when {
                        rtt <= 25.0 -> colors.Emerald
                        rtt <= 60.0 -> colors.Amber
                        else -> colors.Red
                    }
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, h - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }

        Text(
            text = "${maxScale.toInt()}ms",
            color = colors.TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

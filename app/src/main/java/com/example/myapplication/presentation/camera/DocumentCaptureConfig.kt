package com.example.myapplication.presentation.camera

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Loại khung giấy tờ hỗ trợ cho màn hình chụp ảnh.
 *
 * - [A4_PORTRAIT]: Khung dọc tỉ lệ chuẩn A4 (1 : 1.414) cho giấy khai sinh, hợp đồng, giấy tờ A4.
 * - [CCCD_LANDSCAPE]: Khung ngang tỉ lệ chuẩn ID-1 / CCCD (1.586 : 1 = 85.6mm x 53.98mm).
 */
enum class DocumentFrameType(
    val aspectRatioWidthOverHeight: Float,
    val horizontalMarginDp: Int,
    val defaultTitle: String,
    val defaultGuideText: String
) {
    A4_PORTRAIT(
        aspectRatioWidthOverHeight = 1f / 1.414f,
        horizontalMarginDp = 24,
        defaultTitle = "Chụp giấy tờ xác minh quan hệ",
        defaultGuideText = "Đặt giấy tờ giữa khung hình và chụp!"
    ),
    CCCD_LANDSCAPE(
        aspectRatioWidthOverHeight = 1.586f,
        horizontalMarginDp = 20,
        defaultTitle = "Chụp ảnh CCCD/CMND",
        defaultGuideText = "Đặt CCCD vừa khung hình và chụp!"
    )
}

@Immutable
data class DocumentCaptureConfig(
    val frameType: DocumentFrameType = DocumentFrameType.A4_PORTRAIT,
    val title: String = frameType.defaultTitle,
    val guideText: String = frameType.defaultGuideText,
    val enableGallery: Boolean = true,
    val enableFlash: Boolean = true,
    val enableReview: Boolean = true,
    val cornerRadiusDp: Int = 16,
    val maxLongEdgePx: Int = 2400,
    val jpegQuality: Int = 92
)

@Immutable
data class CapturedDocumentResult(
    val file: File,
    val uri: Uri,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long
)

package com.example.myapplication.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bộ xử lý ảnh cho màn hình chụp giấy tờ:
 * - Lấy mẫu khung hình thu nhỏ (1/8) từ TextureView của PreviewView theo đúng ma trận biến đổi
 *   để tạo hiệu ứng viền ngoài mờ mà không ảnh hưởng FPS camera.
 * - Chuẩn hóa EXIF orientation và cắt (crop) ảnh độ phân giải cao theo đúng khung trắng trên màn hình.
 */
object DocumentImageProcessor {

    private const val CACHE_DIR_NAME = "captured_documents"

    fun createTempCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        return File(dir, "raw_doc_${System.currentTimeMillis()}.jpg")
    }

    suspend fun cropCapturedPhotoToFrame(
        context: Context,
        sourceFile: File,
        previewWidth: Int,
        previewHeight: Int,
        frameRect: Rect,
        maxLongEdgePx: Int = 2400,
        jpegQuality: Int = 92
    ): CapturedDocumentResult = withContext(Dispatchers.IO) {
        require(previewWidth > 0 && previewHeight > 0) {
            "Preview dimensions must be positive: ${previewWidth}x$previewHeight"
        }

        val orientation = readExifOrientation(sourceFile.absolutePath)
        val rawBitmap = decodeSampledFile(sourceFile, maxDecodeSidePx = 4096)
            ?: error("Không thể giải mã ảnh vừa chụp.")

        val orientedBitmap = applyExifOrientation(rawBitmap, orientation)
        if (orientedBitmap !== rawBitmap) {
            rawBitmap.recycle()
        }

        val bmpW = orientedBitmap.width
        val bmpH = orientedBitmap.height

        // PreviewView mặc định dùng ScaleType.FILL_CENTER:
        // scale ảnh phủ kín (previewWidth, previewHeight) rồi căn giữa.
        val fillScale = maxOf(
            previewWidth.toFloat() / bmpW.toFloat(),
            previewHeight.toFloat() / bmpH.toFloat()
        )
        val displayedW = bmpW * fillScale
        val displayedH = bmpH * fillScale
        val offsetX = (previewWidth - displayedW) / 2f
        val offsetY = (previewHeight - displayedH) / 2f

        val cropLeft = ((frameRect.left - offsetX) / fillScale).roundToInt().coerceIn(0, bmpW - 1)
        val cropTop = ((frameRect.top - offsetY) / fillScale).roundToInt().coerceIn(0, bmpH - 1)
        val cropRight = ((frameRect.right - offsetX) / fillScale).roundToInt().coerceIn(cropLeft + 1, bmpW)
        val cropBottom = ((frameRect.bottom - offsetY) / fillScale).roundToInt().coerceIn(cropTop + 1, bmpH)

        val cropped = Bitmap.createBitmap(
            orientedBitmap,
            cropLeft,
            cropTop,
            cropRight - cropLeft,
            cropBottom - cropTop
        )
        if (cropped !== orientedBitmap) {
            orientedBitmap.recycle()
        }

        val finalBitmap = downscaleIfNeeded(cropped, maxLongEdgePx)
        if (finalBitmap !== cropped) {
            cropped.recycle()
        }

        val outputFile = saveBitmapAsJpeg(context, finalBitmap, jpegQuality)
        val outW = finalBitmap.width
        val outH = finalBitmap.height
        finalBitmap.recycle()
        sourceFile.delete()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile
        )
        CapturedDocumentResult(
            file = outputFile,
            uri = uri,
            width = outW,
            height = outH,
            fileSizeBytes = outputFile.length()
        )
    }

    suspend fun loadGalleryBitmap(
        context: Context,
        uri: Uri,
        maxLongEdgePx: Int = 2400
    ): Bitmap = withContext(Dispatchers.IO) {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxLongEdgePx)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: error("Không thể đọc ảnh từ thư viện.")

        val oriented = applyExifOrientation(raw, orientation)
        if (oriented !== raw) {
            raw.recycle()
        }
        val finalBitmap = downscaleIfNeeded(oriented, maxLongEdgePx)
        if (finalBitmap !== oriented) {
            oriented.recycle()
        }
        finalBitmap
    }

    suspend fun cropGalleryBitmapToFrame(
        context: Context,
        sourceBitmap: Bitmap,
        frameRect: Rect,
        userScale: Float,
        userOffset: Offset,
        maxLongEdgePx: Int = 2400,
        jpegQuality: Int = 92
    ): CapturedDocumentResult = withContext(Dispatchers.IO) {
        val bmpW = sourceBitmap.width
        val bmpH = sourceBitmap.height

        val baseScale = maxOf(
            frameRect.width / bmpW.toFloat(),
            frameRect.height / bmpH.toFloat()
        )
        val totalScale = baseScale * userScale
        val imgLeft = frameRect.center.x + userOffset.x - (bmpW * totalScale) / 2f
        val imgTop = frameRect.center.y + userOffset.y - (bmpH * totalScale) / 2f

        val cropLeft = ((frameRect.left - imgLeft) / totalScale).roundToInt().coerceIn(0, bmpW - 1)
        val cropTop = ((frameRect.top - imgTop) / totalScale).roundToInt().coerceIn(0, bmpH - 1)
        val cropRight = ((frameRect.right - imgLeft) / totalScale).roundToInt().coerceIn(cropLeft + 1, bmpW)
        val cropBottom = ((frameRect.bottom - imgTop) / totalScale).roundToInt().coerceIn(cropTop + 1, bmpH)

        val cropped = Bitmap.createBitmap(
            sourceBitmap,
            cropLeft,
            cropTop,
            cropRight - cropLeft,
            cropBottom - cropTop
        )
        val finalBitmap = downscaleIfNeeded(cropped, maxLongEdgePx)
        if (finalBitmap !== cropped) {
            cropped.recycle()
        }

        val outputFile = saveBitmapAsJpeg(context, finalBitmap, jpegQuality)
        val outW = finalBitmap.width
        val outH = finalBitmap.height
        finalBitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile
        )
        CapturedDocumentResult(
            file = outputFile,
            uri = uri,
            width = outW,
            height = outH,
            fileSizeBytes = outputFile.length()
        )
    }

    fun createSmallBlurredCopy(source: Bitmap, targetMaxSide: Int = 220, radius: Int = 10): Bitmap {
        val longSide = max(source.width, source.height).coerceAtLeast(1)
        val scale = (targetMaxSide.toFloat() / longSide).coerceAtMost(1f)
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        fastBlurInPlace(small, radius)
        return small
    }

    /**
     * Thuật toán StackBlur nguyên thủy O(N) chạy trực tiếp trên bitmap nhỏ (~100-220px),
     * dùng làm fallback mượt cho Android < 12 (API 24..30) nơi Modifier.blur() chưa hỗ trợ.
     */
    fun fastBlurInPlace(bitmap: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 1 || h <= 1) return

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in dv.indices) {
            dv[i] = i / divsum
        }

        val stack = Array(div) { IntArray(3) }
        val r1 = radius + 1

        var yw = 0
        var yi = 0

        for (y in 0 until h) {
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0

            for (i in -radius..radius) {
                val p = pix[yi + min(wm, max(i, 0))]
                val sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                val rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            var stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                val stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                val p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var yp = -radius * w

            for (i in -radius..radius) {
                yi = max(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                val rbs = r1 - kotlin.math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
            }
            yi = x
            var stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = (0xff shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                val stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                val p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }

    private fun readExifOrientation(filePath: String): Int {
        return try {
            ExifInterface(filePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun decodeSampledFile(file: File, maxDecodeSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDecodeSidePx)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        var inSampleSize = 1
        val longSide = max(width, height)
        if (longSide > maxSidePx) {
            while ((longSide / inSampleSize) > maxSidePx) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxLongEdgePx: Int): Bitmap {
        if (maxLongEdgePx <= 0) return bitmap
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdgePx) return bitmap

        val ratio = maxLongEdgePx.toFloat() / longEdge.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun saveBitmapAsJpeg(context: Context, bitmap: Bitmap, quality: Int): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val outputFile = File(dir, "doc_cropped_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return outputFile
    }
}

/**
 * Lấy mẫu khung hình thu nhỏ (1/8) trực tiếp từ TextureView bên trong PreviewView,
 * tái sử dụng buffer để tránh cấp phát bộ nhớ liên tục và áp dụng đúng ma trận xoay/tỉ lệ FILL_CENTER.
 */
class PreviewBlurSampler {
    private var rawSmallBitmap: Bitmap? = null
    private var outBitmapA: Bitmap? = null
    private var outBitmapB: Bitmap? = null
    private var useBufferA = true
    private var lastSampleTimeMs = 0L

    private val drawMatrix = Matrix()
    private val tvMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun findTextureView(view: View): TextureView? {
        if (view is TextureView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextureView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    fun sampleFrameIfReady(
        previewView: PreviewView,
        textureView: TextureView,
        applySoftwareBlur: Boolean,
        minIntervalMs: Long = 55L
    ): Bitmap? {
        val now = System.currentTimeMillis()
        if (now - lastSampleTimeMs < minIntervalMs) return null

        val pvW = previewView.width
        val pvH = previewView.height
        val tvW = textureView.width
        val tvH = textureView.height
        if (!textureView.isAvailable || pvW <= 0 || pvH <= 0 || tvW <= 0 || tvH <= 0) return null

        lastSampleTimeMs = now

        val scaleFactor = 0.125f
        val rw = (tvW * scaleFactor).roundToInt().coerceIn(32, 240)
        val rh = (tvH * scaleFactor).roundToInt().coerceIn(32, 240)
        val outW = (pvW * scaleFactor).roundToInt().coerceIn(32, 240)
        val outH = (pvH * scaleFactor).roundToInt().coerceIn(32, 240)

        val rawBmp = ensureBitmap(rawSmallBitmap, rw, rh, recycleOld = true).also { rawSmallBitmap = it }
        textureView.getBitmap(rawBmp)

        val targetBmp = if (useBufferA) {
            ensureBitmap(outBitmapA, outW, outH, recycleOld = false).also { outBitmapA = it }
        } else {
            ensureBitmap(outBitmapB, outW, outH, recycleOld = false).also { outBitmapB = it }
        }
        useBufferA = !useBufferA

        drawMatrix.reset()
        drawMatrix.postScale(tvW.toFloat() / rw.toFloat(), tvH.toFloat() / rh.toFloat())
        textureView.getTransform(tvMatrix)
        drawMatrix.postConcat(tvMatrix)
        drawMatrix.postScale(outW.toFloat() / pvW.toFloat(), outH.toFloat() / pvH.toFloat())

        targetBmp.eraseColor(android.graphics.Color.BLACK)
        val canvas = Canvas(targetBmp)
        canvas.drawBitmap(rawBmp, drawMatrix, paint)

        if (applySoftwareBlur) {
            DocumentImageProcessor.fastBlurInPlace(targetBmp, radius = 8)
        }
        return targetBmp
    }

    fun release() {
        rawSmallBitmap?.recycle()
        rawSmallBitmap = null
        outBitmapA = null
        outBitmapB = null
    }

    private fun ensureBitmap(
        existing: Bitmap?,
        width: Int,
        height: Int,
        recycleOld: Boolean
    ): Bitmap {
        if (existing != null && !existing.isRecycled && existing.width == width && existing.height == height) {
            return existing
        }
        if (recycleOld) {
            existing?.recycle()
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}

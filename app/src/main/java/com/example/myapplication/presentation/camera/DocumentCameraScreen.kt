package com.example.myapplication.presentation.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.example.myapplication.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private sealed interface CaptureScreenMode {
    data object Camera : CaptureScreenMode
    data class GalleryCrop(
        val sourceBitmap: Bitmap,
        val blurredBitmap: Bitmap
    ) : CaptureScreenMode

    data class Review(
        val result: CapturedDocumentResult,
        val displayBitmap: Bitmap
    ) : CaptureScreenMode
}

/**
 * Màn hình chụp ảnh giấy tờ (CCCD / A4) sử dụng CameraX + Jetpack Compose:
 * - Vùng bên trong khung trắng sắc nét hoàn toàn, vùng bên ngoài làm mờ (blur) + tối nhẹ.
 * - Hỗ trợ Android 7+ (API 24+): RenderEffect trên API 31+ và StackBlur tối ưu trên API 24-30.
 * - Chụp ảnh độ phân giải cao (CAPTURE_MODE_MAXIMIZE_QUALITY), giữ nguyên độ nét chữ và crop
 *   chính xác theo khung trắng dựa trên ma trận FILL_CENTER + EXIF.
 * - Hỗ trợ chọn ảnh từ thư viện với trình căn chỉnh kéo/thu phóng ngay trong khung.
 */
@Composable
fun DocumentCameraScreen(
    config: DocumentCaptureConfig = DocumentCaptureConfig(),
    onBack: () -> Unit,
    onDocumentConfirmed: (CapturedDocumentResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = remember(view, context) {
        view.findViewTreeLifecycleOwner() ?: (context as? LifecycleOwner)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequestedOnce = true
    }

    // Tự động xin quyền lần đầu & cập nhật lại nếu người dùng vừa bật quyền trong Cài đặt
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (lifecycleOwner != null) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    var screenMode by remember { mutableStateOf<CaptureScreenMode>(CaptureScreenMode.Camera) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Giải phóng bitmap khi rời màn hình
    DisposableEffect(Unit) {
        onDispose {
            when (val mode = screenMode) {
                is CaptureScreenMode.GalleryCrop -> {
                    mode.sourceBitmap.recycle()
                    mode.blurredBitmap.recycle()
                }

                is CaptureScreenMode.Review -> {
                    mode.displayBitmap.recycle()
                }

                CaptureScreenMode.Camera -> Unit
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isProcessing = true
            errorMessage = null
            runCatching {
                val bmp = DocumentImageProcessor.loadGalleryBitmap(
                    context = context,
                    uri = uri,
                    maxLongEdgePx = config.maxLongEdgePx
                )
                val blurred = withContext(Dispatchers.Default) {
                    DocumentImageProcessor.createSmallBlurredCopy(bmp)
                }
                bmp to blurred
            }.onSuccess { (bmp, blurred) ->
                (screenMode as? CaptureScreenMode.GalleryCrop)?.let { prev ->
                    prev.sourceBitmap.recycle()
                    prev.blurredBitmap.recycle()
                }
                screenMode = CaptureScreenMode.GalleryCrop(
                    sourceBitmap = bmp,
                    blurredBitmap = blurred
                )
            }.onFailure { err ->
                errorMessage = err.message ?: "Không thể tải ảnh từ thư viện."
            }
            isProcessing = false
        }
    }

    fun openGalleryPicker() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun showCapturedResult(result: CapturedDocumentResult) {
        if (!config.enableReview) {
            onDocumentConfirmed(result)
            return
        }
        scope.launch {
            val previewBmp = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(result.file.absolutePath)
            }
            if (previewBmp != null) {
                screenMode = CaptureScreenMode.Review(result = result, displayBitmap = previewBmp)
            } else {
                onDocumentConfirmed(result)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val mode = screenMode) {
            is CaptureScreenMode.Camera -> {
                if (hasCameraPermission) {
                    CameraCaptureView(
                        config = config,
                        lifecycleOwner = lifecycleOwner,
                        isProcessing = isProcessing,
                        onProcessingChange = { isProcessing = it },
                        onError = { errorMessage = it },
                        onBack = onBack,
                        onOpenGallery = ::openGalleryPicker,
                        onCaptured = ::showCapturedResult
                    )
                } else {
                    CameraPermissionRequiredView(
                        title = config.title,
                        showOpenSettings = permissionRequestedOnce,
                        onBack = onBack,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        }
                    )
                }
            }

            is CaptureScreenMode.GalleryCrop -> {
                GalleryCropView(
                    config = config,
                    sourceBitmap = mode.sourceBitmap,
                    blurredBitmap = mode.blurredBitmap,
                    isProcessing = isProcessing,
                    onBack = {
                        mode.sourceBitmap.recycle()
                        mode.blurredBitmap.recycle()
                        screenMode = CaptureScreenMode.Camera
                    },
                    onPickAnother = ::openGalleryPicker,
                    onConfirmCrop = { frameRect, userScale, userOffset ->
                        scope.launch {
                            isProcessing = true
                            errorMessage = null
                            runCatching {
                                DocumentImageProcessor.cropGalleryBitmapToFrame(
                                    context = context,
                                    sourceBitmap = mode.sourceBitmap,
                                    frameRect = frameRect,
                                    userScale = userScale,
                                    userOffset = userOffset,
                                    maxLongEdgePx = config.maxLongEdgePx,
                                    jpegQuality = config.jpegQuality
                                )
                            }.onSuccess { result ->
                                mode.sourceBitmap.recycle()
                                mode.blurredBitmap.recycle()
                                showCapturedResult(result)
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Không thể cắt ảnh."
                            }
                            isProcessing = false
                        }
                    }
                )
            }

            is CaptureScreenMode.Review -> {
                DocumentReviewView(
                    title = config.title,
                    result = mode.result,
                    displayBitmap = mode.displayBitmap,
                    onRetake = {
                        mode.displayBitmap.recycle()
                        mode.result.file.delete()
                        screenMode = CaptureScreenMode.Camera
                    },
                    onUsePhoto = {
                        onDocumentConfirmed(mode.result)
                    }
                )
            }
        }

        // Banner thông báo lỗi nhẹ nếu có sự cố chụp / đọc ảnh
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp, start = 20.dp, end = 20.dp)
        ) {
            errorMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE6B3261E))
                        .clickable { errorMessage = null }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đóng",
                        color = Color(0xFFFFDAD6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x77000000))
                    .pointerInput(Unit) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Đang xử lý ảnh sắc nét...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureView(
    config: DocumentCaptureConfig,
    lifecycleOwner: LifecycleOwner?,
    isProcessing: Boolean,
    onProcessingChange: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onCaptured: (CapturedDocumentResult) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    val frameRect = remember(
        containerSize,
        topBarHeightPx,
        bottomBarHeightPx,
        config.frameType
    ) {
        calculateDocumentFrameRect(
            containerWidthPx = containerSize.width.toFloat(),
            containerHeightPx = containerSize.height.toFloat(),
            topReservedPx = topBarHeightPx.toFloat(),
            bottomReservedPx = bottomBarHeightPx.toFloat(),
            frameType = config.frameType,
            density = density
        )
    }

    val previewView = remember {
        PreviewView(context).apply {
            // Bắt buộc dùng COMPATIBLE (TextureView) để đọc được khung hình mượt cho lớp viền blur
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val blurSampler = remember { PreviewBlurSampler() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var blurredImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var focusIndicatorOffset by remember { mutableStateOf<Offset?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            blurSampler.release()
            cameraExecutor.shutdown()
        }
    }

    // Gắn CameraX Preview + ImageCapture độ phân giải cao
    DisposableEffect(lifecycleOwner, previewView) {
        val owner = lifecycleOwner
        if (owner == null) {
            onDispose {}
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            var providerRef: ProcessCameraProvider? = null

            val listener = Runnable {
                val provider = runCatching { cameraProviderFuture.get() }.getOrNull()
                    ?: return@Runnable
                providerRef = provider

                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(rotation)
                    .build()

                runCatching {
                    provider.unbindAll()
                    val boundCamera = provider.bindToLifecycle(
                        owner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    camera = boundCamera
                    imageCapture = capture
                    hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
                    if (isFlashOn && hasFlashUnit) {
                        boundCamera.cameraControl.enableTorch(true)
                    }
                }.onFailure { err ->
                    onError("Không thể khởi động camera: ${err.message}")
                }
            }

            cameraProviderFuture.addListener(listener, mainExecutor)

            onDispose {
                runCatching {
                    camera?.cameraControl?.enableTorch(false)
                    providerRef?.unbindAll()
                }
                camera = null
                imageCapture = null
            }
        }
    }

    // Lấy mẫu nền blur ~16fps không chặn luồng preview chính
    LaunchedEffect(previewView) {
        val needsSoftwareBlur = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        while (true) {
            if (previewView.previewStreamState.value == PreviewView.StreamState.STREAMING) {
                val textureView = blurSampler.findTextureView(previewView)
                if (textureView != null) {
                    val sampled = blurSampler.sampleFrameIfReady(
                        previewView = previewView,
                        textureView = textureView,
                        applySoftwareBlur = needsSoftwareBlur
                    )
                    if (sampled != null) {
                        blurredImageBitmap = sampled.asImageBitmap()
                    }
                }
            }
            delay(60L)
        }
    }

    // Tự ẩn vòng lấy nét sau 900ms
    LaunchedEffect(focusIndicatorOffset) {
        if (focusIndicatorOffset != null) {
            delay(900L)
            focusIndicatorOffset = null
        }
    }

    fun triggerCapture() {
        val capture = imageCapture
        if (capture == null || isProcessing) return
        if (containerSize.width <= 0 || containerSize.height <= 0 || frameRect == Rect.Zero) return

        onProcessingChange(true)
        val rawTempFile = DocumentImageProcessor.createTempCaptureFile(context)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(rawTempFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch {
                        runCatching {
                            DocumentImageProcessor.cropCapturedPhotoToFrame(
                                context = context,
                                sourceFile = rawTempFile,
                                previewWidth = previewView.width,
                                previewHeight = previewView.height,
                                frameRect = frameRect,
                                maxLongEdgePx = config.maxLongEdgePx,
                                jpegQuality = config.jpegQuality
                            )
                        }.onSuccess { result ->
                            onProcessingChange(false)
                            onCaptured(result)
                        }.onFailure { err ->
                            rawTempFile.delete()
                            onProcessingChange(false)
                            onError(err.message ?: "Lỗi xử lý ảnh sau khi chụp.")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    rawTempFile.delete()
                    scope.launch {
                        onProcessingChange(false)
                        onError("Chụp ảnh thất bại: ${exception.message}")
                    }
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(camera) {
                detectTapGestures { tapOffset ->
                    val activeCamera = camera ?: return@detectTapGestures
                    focusIndicatorOffset = tapOffset
                    val point = previewView.meteringPointFactory.createPoint(
                        tapOffset.x,
                        tapOffset.y
                    )
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    )
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    activeCamera.cameraControl.startFocusAndMetering(action)
                }
            }
    ) {
        // 1. Camera Preview sắc nét toàn màn hình
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Lớp mờ + tối bên ngoài khung, khoét trong suốt bên trong khung trắng
        DocumentMaskOverlay(
            frameRect = frameRect,
            cornerRadiusDp = config.cornerRadiusDp,
            blurredPreviewBitmap = blurredImageBitmap
        )

        // Vòng báo chạm lấy nét (Tap-to-Focus)
        focusIndicatorOffset?.let { offset ->
            val sizePx = with(density) { 52.dp.roundToPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (offset.x - sizePx / 2f).roundToInt(),
                            y = (offset.y - sizePx / 2f).roundToInt()
                        )
                    }
                    .size(52.dp)
                    .border(1.5.dp, Color(0xFFFFD54F), CircleShape)
            )
        }

        // 3. Text hướng dẫn ngay dưới khung trắng
        if (frameRect != Rect.Zero) {
            val guideTopDp = with(density) { (frameRect.bottom + 14.dp.toPx()).toDp() }
            Text(
                text = config.guideText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xAA000000),
                        offset = Offset(0f, 2f),
                        blurRadius = 6f
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = guideTopDp, start = 24.dp, end = 24.dp)
            )
        }

        // 4. Top Bar bán trong suốt tràn dưới status bar
        CameraTopBar(
            title = config.title,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeightPx = it.height }
        )

        // 5. Thanh điều khiển đáy: Gallery - Chụp - Flash
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height }
                .fillMaxWidth()
                .background(Color(0xD9121212))
                .navigationBarsPadding()
                .height(104.dp)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (config.enableGallery) {
                IconButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.size(48.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_doc_camera_gallery),
                        contentDescription = "Chọn ảnh từ thư viện",
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Nút chụp ảnh tròn trắng + viền ngoài xám mờ
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(Color(0x55FFFFFF))
                    .clickable(enabled = !isProcessing) { triggerCapture() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            if (config.enableFlash && hasFlashUnit) {
                IconButton(
                    onClick = {
                        val nextState = !isFlashOn
                        isFlashOn = nextState
                        camera?.cameraControl?.enableTorch(nextState)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Image(
                        painter = painterResource(
                            id = if (isFlashOn) {
                                R.drawable.ic_doc_camera_flash_on
                            } else {
                                R.drawable.ic_doc_camera_flash_off
                            }
                        ),
                        contentDescription = if (isFlashOn) "Tắt đèn flash" else "Bật đèn flash",
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun GalleryCropView(
    config: DocumentCaptureConfig,
    sourceBitmap: Bitmap,
    blurredBitmap: Bitmap,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onConfirmCrop: (frameRect: Rect, userScale: Float, userOffset: Offset) -> Unit
) {
    val density = LocalDensity.current
    val sourceImageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
    val blurredImageBitmap = remember(blurredBitmap) { blurredBitmap.asImageBitmap() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    val frameRect = remember(
        containerSize,
        topBarHeightPx,
        bottomBarHeightPx,
        config.frameType
    ) {
        calculateDocumentFrameRect(
            containerWidthPx = containerSize.width.toFloat(),
            containerHeightPx = containerSize.height.toFloat(),
            topReservedPx = topBarHeightPx.toFloat(),
            bottomReservedPx = bottomBarHeightPx.toFloat(),
            frameType = config.frameType,
            density = density
        )
    }

    var userScale by remember(sourceBitmap) { mutableFloatStateOf(1f) }
    var userOffset by remember(sourceBitmap) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(frameRect, sourceBitmap) {
                if (frameRect == Rect.Zero) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (userScale * zoom).coerceIn(1f, 5f)
                    val baseScale = maxOf(
                        frameRect.width / sourceBitmap.width.toFloat(),
                        frameRect.height / sourceBitmap.height.toFloat()
                    )
                    val totalScale = baseScale * newScale
                    val halfImgW = (sourceBitmap.width * totalScale) / 2f
                    val halfImgH = (sourceBitmap.height * totalScale) / 2f
                    val maxOffsetX = (halfImgW - frameRect.width / 2f).coerceAtLeast(0f)
                    val maxOffsetY = (halfImgH - frameRect.height / 2f).coerceAtLeast(0f)

                    val nextOffset = userOffset + pan
                    userScale = newScale
                    userOffset = Offset(
                        x = nextOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                        y = nextOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                    )
                }
            }
    ) {
        if (frameRect != Rect.Zero) {
            val baseScale = maxOf(
                frameRect.width / sourceBitmap.width.toFloat(),
                frameRect.height / sourceBitmap.height.toFloat()
            )
            val totalScale = baseScale * userScale
            val drawW = (sourceBitmap.width * totalScale).roundToInt().coerceAtLeast(1)
            val drawH = (sourceBitmap.height * totalScale).roundToInt().coerceAtLeast(1)
            val imgLeft = (frameRect.center.x + userOffset.x - drawW / 2f).roundToInt()
            val imgTop = (frameRect.center.y + userOffset.y - drawH / 2f).roundToInt()

            // Ảnh gốc độ nét cao
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = sourceImageBitmap,
                    dstOffset = IntOffset(imgLeft, imgTop),
                    dstSize = IntSize(drawW, drawH),
                    filterQuality = FilterQuality.High
                )
            }

            // Lớp mờ ngoài khung đồng bộ vị trí theo ảnh đang kéo/zoom
            DocumentMaskOverlay(
                frameRect = frameRect,
                cornerRadiusDp = config.cornerRadiusDp,
                blurredPreviewBitmap = null,
                customBlurredBackground = {
                    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(radius = 18.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                    } else {
                        Modifier
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier)
                    ) {
                        drawImage(
                            image = blurredImageBitmap,
                            dstOffset = IntOffset(imgLeft, imgTop),
                            dstSize = IntSize(drawW, drawH),
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }
            )

            val guideTopDp = with(density) { (frameRect.bottom + 14.dp.toPx()).toDp() }
            Text(
                text = "Kéo hoặc phóng to để căn giấy tờ vừa khung",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = guideTopDp, start = 24.dp, end = 24.dp)
            )
        }

        CameraTopBar(
            title = config.title,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeightPx = it.height }
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height }
                .fillMaxWidth()
                .background(Color(0xE6121212))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onPickAnother,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(text = "Chọn lại", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { onConfirmCrop(frameRect, userScale, userOffset) },
                enabled = !isProcessing && frameRect != Rect.Zero,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF7921),
                    contentColor = Color.White
                )
            ) {
                Text(text = "Dùng ảnh này", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DocumentReviewView(
    title: String,
    result: CapturedDocumentResult,
    displayBitmap: Bitmap,
    onRetake: () -> Unit,
    onUsePhoto: () -> Unit
) {
    val imageBitmap = remember(displayBitmap) { displayBitmap.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
    ) {
        CameraTopBar(
            title = title,
            onBack = onRetake
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Ảnh giấy tờ đã cắt theo khung",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
            )
        }

        Text(
            text = "Kích thước sau crop: ${result.width} x ${result.height} px • ${result.fileSizeBytes / 1024} KB",
            color = Color(0xFFBDBDBD),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(text = "Chụp lại", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onUsePhoto,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF7921),
                    contentColor = Color.White
                )
            ) {
                Text(text = "Sử dụng ảnh", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CameraTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x99000000))
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_doc_camera_back),
                contentDescription = "Quay lại",
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 52.dp)
        )
    }
}

@Composable
private fun CameraPermissionRequiredView(
    title: String,
    showOpenSettings: Boolean,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
    ) {
        CameraTopBar(title = title, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Cần cấp quyền Camera để chụp giấy tờ",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Ứng dụng sử dụng camera để căn chỉnh và chụp rõ nét thông tin trên CCCD hoặc giấy tờ A4.",
                color = Color(0xFFBDBDBD),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = if (showOpenSettings) onOpenSettings else onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF7921),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = if (showOpenSettings) "Mở Cài đặt quyền" else "Cấp quyền Camera",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

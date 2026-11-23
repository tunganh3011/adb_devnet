package com.devnet.adbcompanion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.devnet.adbcompanion.R
import com.devnet.adbcompanion.data.LanPingEngine
import com.devnet.adbcompanion.data.PreferencesStore
import com.devnet.adbcompanion.data.SystemInspector
import com.devnet.adbcompanion.domain.model.DevNetSnapshot
import com.devnet.adbcompanion.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class DevNetMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: PreferencesStore
    private lateinit var inspector: SystemInspector
    private val pingEngine = LanPingEngine()

    private var monitorJob: Job? = null
    private var hudView: View? = null
    private var hudTextView: TextView? = null
    private var hudDotView: View? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

    private var latestSnapshot: DevNetSnapshot = DevNetSnapshot()
    private var latestLatencyMs: Double? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesStore(this)
        inspector = SystemInspector(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundStarted()

        when (intent?.action) {
            ACTION_COPY_IP -> handleCopyIp()
            ACTION_DISCONNECT_ADB -> handleDisconnectOrToggle()
            ACTION_HIDE_HUD -> {
                prefs.floatingHudEnabled = false
                removeFloatingHud()
                DevNetEventBus.notifyStateChanged()
                if (!prefs.persistentNotificationEnabled) {
                    stopServiceCleanly()
                    return START_NOT_STICKY
                }
            }
            ACTION_STOP_SERVICE -> {
                prefs.persistentNotificationEnabled = false
                prefs.floatingHudEnabled = false
                removeFloatingHud()
                DevNetEventBus.notifyStateChanged()
                stopServiceCleanly()
                return START_NOT_STICKY
            }
            else -> syncModes()
        }

        return START_STICKY
    }

    private fun syncModes() {
        val wantNotif = prefs.persistentNotificationEnabled
        val wantHud = prefs.floatingHudEnabled && canDrawOverlays()

        if (!wantNotif && !wantHud) {
            removeFloatingHud()
            stopServiceCleanly()
            return
        }

        if (wantHud) {
            attachFloatingHudIfNeeded()
        } else {
            removeFloatingHud()
        }

        startPollingLoop()
    }

    private fun startPollingLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                refreshAndRender()
                delay(4000L)
            }
        }
    }

    private suspend fun refreshAndRender() {
        val snapshot = inspector.captureSnapshot()
        latestSnapshot = snapshot

        val host = prefs.targetHost.ifBlank { snapshot.wifiTelemetry.gatewayIp.orEmpty() }
        latestLatencyMs = if (host.isNotBlank()) {
            pingEngine.pingOnce(host).rttMs
        } else {
            null
        }

        updateNotification(snapshot, latestLatencyMs)
        updateFloatingHud(snapshot, latestLatencyMs)
    }

    private fun handleCopyIp() {
        serviceScope.launch {
            val snapshot = inspector.captureSnapshot()
            val endpoint = snapshot.adbStatus.connectionEndpoint(snapshot.wifiTelemetry.ipAddress)
            if (endpoint == null) {
                Toast.makeText(this@DevNetMonitorService, "Chưa có cổng kết nối ADB", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val text = "adb connect $endpoint"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Connect", text))
            Toast.makeText(this@DevNetMonitorService, "Đã copy: $text", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDisconnectOrToggle() {
        serviceScope.launch {
            if (!inspector.hasWriteSecureSettings()) {
                Toast.makeText(
                    this@DevNetMonitorService,
                    "Cần cấp quyền WRITE_SECURE_SETTINGS trước",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val adb = latestSnapshot.adbStatus
            val msg = when {
                adb.adbWifiEnabled -> {
                    inspector.setAdbWifiEnabled(false)
                    "Đã tắt Gỡ lỗi qua Wi-Fi"
                }
                adb.isTcpPortListening -> {
                    inspector.resetAdbConnections()
                    "Đã ngắt & reset kết nối ADB"
                }
                else -> {
                    inspector.setAdbWifiEnabled(true)
                    "Đã bật Gỡ lỗi qua Wi-Fi"
                }
            }
            Toast.makeText(this@DevNetMonitorService, msg, Toast.LENGTH_SHORT).show()
            refreshAndRender()
            DevNetEventBus.notifyStateChanged()
        }
    }

    private fun ensureForegroundStarted() {
        val notification = buildNotification(latestSnapshot, latestLatencyMs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(snapshot: DevNetSnapshot, latencyMs: Double?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(snapshot, latencyMs))
    }

    private fun buildNotification(snapshot: DevNetSnapshot, latencyMs: Double?): Notification {
        val wifi = snapshot.wifiTelemetry
        val adb = snapshot.adbStatus
        val proxy = snapshot.proxyStatus

        val endpoint = adb.connectionEndpoint(wifi.ipAddress)
        val ipPort = endpoint ?: wifi.ipAddress ?: "Chưa kết nối Wi-Fi"

        val adbBadge = when {
            adb.wirelessEndpoint(wifi.ipAddress) != null -> "Wi-Fi Debug ON"
            adb.legacyEndpoint(wifi.ipAddress) != null -> "TCP ${adb.effectivePort}"
            adb.adbWifiEnabled -> "Wi-Fi Debug ON"
            adb.adbEnabled -> "USB Ready"
            else -> "ADB OFF"
        }

        val latencyText = if (latencyMs != null) {
            String.format(Locale.US, "%.0fms", latencyMs)
        } else {
            "--"
        }
        val proxyText = when {
            proxy.isDeadProxyWarning -> "Proxy DEAD!"
            proxy.isConfigured -> "Proxy ON"
            else -> "Proxy OFF"
        }
        val dualWifiWarn = if (wifi.hasDualWifiConflict) " | ⚠️ Wi-Fi kép" else ""

        val title = "$adbBadge • $ipPort"
        val body = "${wifi.bandLabel} | LAN: $latencyText | $proxyText$dualWifiWarn"

        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, DevNetMonitorService::class.java).apply { action = ACTION_COPY_IP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLabel = when {
            adb.adbWifiEnabled -> "Tắt Wi-Fi ADB"
            adb.isTcpPortListening -> "Ngắt ADB"
            else -> "Bật Wi-Fi ADB"
        }
        val toggleIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, DevNetMonitorService::class.java).apply { action = ACTION_DISCONNECT_ADB },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_devnet_tile)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .apply { if (endpoint != null) addAction(0, "Copy adb connect", copyIntent) }
            .addAction(0, toggleLabel, toggleIntent)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachFloatingHudIfNeeded() {
        if (hudView != null || !canDrawOverlays()) return

        val density = resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (7 * density).toInt()
        val dotSize = (8 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F1620"))
                cornerRadius = 20 * density
                setStroke((1 * density).toInt(), Color.parseColor("#334155"))
            }
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginEnd = (7 * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }
        }

        val textView = TextView(this).apply {
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
            text = "DevNet HUD..."
        }

        val closeBtn = TextView(this).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding((10 * density).toInt(), 0, 0, 0)
            text = "✕"
            setOnClickListener {
                startService(Intent(this@DevNetMonitorService, DevNetMonitorService::class.java).apply {
                    action = ACTION_HIDE_HUD
                })
            }
        }

        container.addView(dot)
        container.addView(textView)
        container.addView(closeBtn)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (110 * density).toInt()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { windowManager?.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val openIntent = Intent(this@DevNetMonitorService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(openIntent)
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            windowManager?.addView(container, params)
            hudView = container
            hudTextView = textView
            hudDotView = dot
            hudParams = params
        }
    }

    private fun updateFloatingHud(snapshot: DevNetSnapshot, latencyMs: Double?) {
        val tv = hudTextView ?: return
        val dot = hudDotView ?: return

        val wifi = snapshot.wifiTelemetry
        val adb = snapshot.adbStatus
        val proxy = snapshot.proxyStatus

        val lat = if (latencyMs != null) String.format(Locale.US, "%.0fms", latencyMs) else "--"
        val adbLabel = when {
            adb.adbWifiEnabled -> "ADB:WiFi"
            adb.isTcpPortListening -> "ADB:${adb.effectivePort}"
            adb.adbEnabled -> "ADB:USB"
            else -> "ADB:OFF"
        }
        val proxyLabel = when {
            proxy.isDeadProxyWarning -> "PRX:DEAD"
            proxy.isConfigured -> "PRX:ON"
            else -> "PRX:OFF"
        }
        val dualFlag = if (wifi.hasDualWifiConflict) " !2xWiFi" else ""

        tv.text = "${wifi.bandLabel} | $lat | $adbLabel | $proxyLabel$dualFlag"

        val dotColor = when {
            proxy.isDeadProxyWarning || wifi.hasDualWifiConflict -> "#FF5252"
            adb.isWifiDebugActive -> "#00E676"
            else -> "#FFB300"
        }
        (dot.background as? GradientDrawable)?.setColor(Color.parseColor(dotColor))
    }

    private fun removeFloatingHud() {
        val view = hudView ?: return
        runCatching { windowManager?.removeView(view) }
        hudView = null
        hudTextView = null
        hudDotView = null
        hudParams = null
    }

    private fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun stopServiceCleanly() {
        monitorJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DevNet Trạng thái ADB & Wi-Fi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị IP ADB, độ trễ mạng LAN và trạng thái Proxy"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        removeFloatingHud()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "devnet_status_channel"
        private const val NOTIFICATION_ID = 4041

        const val ACTION_SYNC_STATE = "com.devnet.adbcompanion.ACTION_SYNC_STATE"
        const val ACTION_COPY_IP = "com.devnet.adbcompanion.ACTION_COPY_IP"
        const val ACTION_DISCONNECT_ADB = "com.devnet.adbcompanion.ACTION_DISCONNECT_ADB"
        const val ACTION_HIDE_HUD = "com.devnet.adbcompanion.ACTION_HIDE_HUD"
        const val ACTION_STOP_SERVICE = "com.devnet.adbcompanion.ACTION_STOP_SERVICE"

        fun sync(context: Context) {
            val prefs = PreferencesStore(context)
            val intent = Intent(context, DevNetMonitorService::class.java).apply {
                action = ACTION_SYNC_STATE
            }
            if (prefs.persistentNotificationEnabled || (prefs.floatingHudEnabled && Settings.canDrawOverlays(context))) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(Intent(context, DevNetMonitorService::class.java))
            }
        }
    }
}

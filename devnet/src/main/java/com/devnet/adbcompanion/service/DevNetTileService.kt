package com.devnet.adbcompanion.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.devnet.adbcompanion.data.SystemInspector
import com.devnet.adbcompanion.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DevNetTileService : TileService() {

    private var tileScope: CoroutineScope? = null
    private lateinit var inspector: SystemInspector

    override fun onCreate() {
        super.onCreate()
        inspector = SystemInspector(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        tileScope?.cancel()
        tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        tileScope?.launch {
            refreshTile()
        }
    }

    override fun onStopListening() {
        tileScope?.cancel()
        tileScope = null
        super.onStopListening()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (!inspector.hasWriteSecureSettings()) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    201,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        tileScope?.launch {
            val snapshot = inspector.captureSnapshot()
            val adb = snapshot.adbStatus
            when {
                adb.adbWifiEnabled -> {
                    inspector.setAdbWifiEnabled(false)
                    Toast.makeText(this@DevNetTileService, "Đã tắt ADB Wi-Fi", Toast.LENGTH_SHORT).show()
                }
                adb.isTcpPortListening -> {
                    inspector.resetAdbConnections()
                    Toast.makeText(this@DevNetTileService, "Đã ngắt kết nối ADB TCP/IP", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    inspector.setAdbWifiEnabled(true)
                    Toast.makeText(this@DevNetTileService, "Đã bật ADB Wi-Fi", Toast.LENGTH_SHORT).show()
                }
            }
            refreshTile()
            DevNetEventBus.notifyStateChanged()
        }
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val snapshot = inspector.captureSnapshot()
        val adb = snapshot.adbStatus
        val wifi = snapshot.wifiTelemetry

        tile.state = if (adb.isWifiDebugActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = adb.connectionEndpoint(wifi.ipAddress) ?: "ADB Wi-Fi"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                adb.wirelessEndpoint(wifi.ipAddress) != null -> "${wifi.bandLabel} • Wireless ON"
                adb.legacyEndpoint(wifi.ipAddress) != null -> "${wifi.bandLabel} • TCP ${adb.effectivePort}"
                adb.adbWifiEnabled -> "${wifi.bandLabel} • Wireless ON"
                else -> "Đang tắt"
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        tileScope?.cancel()
        super.onDestroy()
    }
}

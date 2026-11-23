package com.devnet.adbcompanion.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.devnet.adbcompanion.data.PreferencesStore
import com.devnet.adbcompanion.data.SystemInspector
import com.devnet.adbcompanion.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PrivateDnsTileService : TileService() {

    private var tileScope: CoroutineScope? = null
    private lateinit var inspector: SystemInspector
    private lateinit var prefs: PreferencesStore

    override fun onCreate() {
        super.onCreate()
        inspector = SystemInspector(this)
        prefs = PreferencesStore(this)
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
                    202,
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
            val env = snapshot.environment
            if (!env.privateDnsHost.isNullOrBlank()) {
                prefs.savedPrivateDnsHost = env.privateDnsHost
            }
            val turnOn = !env.isCustomPrivateDns
            val host = prefs.savedPrivateDnsHost
            inspector.setPrivateDnsEnabled(turnOn, host)
                .onSuccess {
                    val msg = if (turnOn) "Đã BẬT chặn QC ($host)" else "Đã TẮT Private DNS"
                    Toast.makeText(this@PrivateDnsTileService, msg, Toast.LENGTH_SHORT).show()
                }
                .onFailure { err ->
                    Toast.makeText(
                        this@PrivateDnsTileService,
                        err.message ?: "Không thể đổi Private DNS",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            refreshTile()
            DevNetEventBus.notifyStateChanged()
        }
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val env = inspector.captureSnapshot().environment
        if (!env.privateDnsHost.isNullOrBlank()) {
            prefs.savedPrivateDnsHost = env.privateDnsHost
        }

        val isOn = env.isCustomPrivateDns
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "AdGuard DNS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isOn) {
                env.privateDnsHost?.takeIf { it.isNotBlank() } ?: prefs.savedPrivateDnsHost
            } else {
                "Đã tắt"
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        tileScope?.cancel()
        super.onDestroy()
    }
}

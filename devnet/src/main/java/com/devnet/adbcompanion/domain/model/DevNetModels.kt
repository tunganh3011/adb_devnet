package com.devnet.adbcompanion.domain.model

enum class QualityTier {
    EXCELLENT,
    GOOD,
    WARN,
    POOR
}

data class AdbStatus(
    val adbEnabled: Boolean = false,
    val adbWifiEnabled: Boolean = false,
    val tcpPort: Int? = null,
    val isTcpPortListening: Boolean = false,
    val tlsServerEnabled: Boolean = false,
    val adbInputEnabled: Boolean? = null,
    val adbInstallEnabled: Boolean? = null,
    val hasWriteSecureSettings: Boolean = false,
    val deviceModel: String = "",
    val wirelessPort: Int? = null
) {
    val isWifiDebugActive: Boolean
        get() = adbWifiEnabled || isTcpPortListening

    val effectivePort: Int?
        get() = tcpPort?.takeIf { it in 1..65535 }

    fun wirelessEndpoint(ipAddress: String?): String? {
        val port = wirelessPort?.takeIf { it in 1..65535 } ?: return null
        return ipAddress?.takeIf { it.isNotBlank() && adbWifiEnabled }?.let { "$it:$port" }
    }

    fun legacyEndpoint(ipAddress: String?): String? {
        val port = effectivePort ?: return null
        return ipAddress?.takeIf { it.isNotBlank() && isTcpPortListening }?.let { "$it:$port" }
    }

    fun connectionEndpoint(ipAddress: String?): String? =
        wirelessEndpoint(ipAddress) ?: legacyEndpoint(ipAddress)
}

data class WifiTelemetry(
    val isConnected: Boolean = false,
    val ipAddress: String? = null,
    val gatewayIp: String? = null,
    val ssid: String? = null,
    val bssid: String? = null,
    val frequencyMhz: Int = 0,
    val linkSpeedMbps: Int = 0,
    val rssiDbm: Int = -127,
    val activeWlanInterfaces: List<String> = emptyList()
) {
    val hasDualWifiConflict: Boolean
        get() = activeWlanInterfaces.size > 1

    val bandLabel: String
        get() = when {
            frequencyMhz >= 5925 -> "6 GHz"
            frequencyMhz >= 4900 -> "5 GHz"
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            else -> "--"
        }

    val signalQualityLabel: String
        get() = when {
            !isConnected -> "Mất kết nối"
            rssiDbm >= -55 -> "Rất mạnh"
            rssiDbm >= -67 -> "Tốt"
            rssiDbm >= -78 -> "Trung bình"
            else -> "Yếu"
        }
}

data class ProxyStatus(
    val isConfigured: Boolean = false,
    val host: String? = null,
    val port: Int? = null,
    val isReachable: Boolean? = null
) {
    val displayAddress: String
        get() = if (isConfigured && !host.isNullOrBlank() && (port ?: 0) > 0) {
            "$host:$port"
        } else {
            "OFF"
        }

    val isDeadProxyWarning: Boolean
        get() = isConfigured && isReachable == false
}

data class PingSample(
    val timestampMs: Long,
    val rttMs: Double?
)

data class ScrcpyRecommendation(
    val command: String = "scrcpy -e",
    val title: String = "Mặc định",
    val reason: String = "Đang đo chất lượng kết nối...",
    val tier: QualityTier = QualityTier.GOOD
)

data class LanMonitorState(
    val targetHost: String = "",
    val isMonitoring: Boolean = false,
    val samples: List<PingSample> = emptyList(),
    val lastLatencyMs: Double? = null,
    val avgLatencyMs: Double? = null,
    val minLatencyMs: Double? = null,
    val maxLatencyMs: Double? = null,
    val jitterMs: Double? = null,
    val packetLossPercent: Int = 0
)

data class EnvironmentDiagnostics(
    val vpnActive: Boolean = false,
    val privateDnsMode: String? = null,
    val privateDnsHost: String? = null,
    val locationEnabled: Boolean = true
) {
    val isCustomPrivateDns: Boolean
        get() = privateDnsMode.equals("hostname", ignoreCase = true)
}

enum class DiagnosticSeverity {
    BLOCKER,
    WARNING
}

enum class DiagnosticAction {
    OPEN_WIFI_SETTINGS,
    OPEN_VPN_SETTINGS,
    OPEN_LOCATION_SETTINGS,
    OPEN_NETWORK_SETTINGS,
    OPEN_DEV_OPTIONS,
    RESET_ADB,
    ENABLE_ADB,
    DISABLE_PRIVATE_DNS,
    NONE
}

data class DiagnosticItem(
    val id: String,
    val title: String,
    val detail: String,
    val severity: DiagnosticSeverity,
    val action: DiagnosticAction = DiagnosticAction.NONE,
    val actionLabel: String? = null
)

data class BatteryTelemetry(
    val levelPercent: Int = 0,
    val isCharging: Boolean = false,
    val pluggedSource: String = "Đang dùng pin",
    val temperatureC: Double = 0.0,
    val voltageMv: Int = 0,
    val currentMa: Int? = null,
    val nightCurrentFlag: Int? = null
)

data class DevNetSnapshot(
    val adbStatus: AdbStatus = AdbStatus(),
    val wifiTelemetry: WifiTelemetry = WifiTelemetry(),
    val proxyStatus: ProxyStatus = ProxyStatus(),
    val environment: EnvironmentDiagnostics = EnvironmentDiagnostics(),
    val batteryTelemetry: BatteryTelemetry = BatteryTelemetry(),
    val timestampMs: Long = System.currentTimeMillis()
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class DevNetUiState(
    val snapshot: DevNetSnapshot = DevNetSnapshot(),
    val lanMonitor: LanMonitorState = LanMonitorState(),
    val scrcpyRecommendation: ScrcpyRecommendation = ScrcpyRecommendation(),
    val diagnostics: List<DiagnosticItem> = emptyList(),
    val savedPrivateDnsHost: String = "dns.adguard-dns.com",
    val persistentNotificationEnabled: Boolean = false,
    val floatingHudEnabled: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isResettingAdb: Boolean = false,
    val toastMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val isRecordingDebugSession: Boolean = false,
    val debugSessionReport: String? = null
)

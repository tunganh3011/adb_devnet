package com.devnet.adbcompanion.domain

import com.devnet.adbcompanion.domain.model.AdbStatus
import com.devnet.adbcompanion.domain.model.DiagnosticAction
import com.devnet.adbcompanion.domain.model.DiagnosticItem
import com.devnet.adbcompanion.domain.model.DiagnosticSeverity
import com.devnet.adbcompanion.domain.model.EnvironmentDiagnostics
import com.devnet.adbcompanion.domain.model.WifiTelemetry

object WifiDebugDiagnostics {

    fun evaluate(
        adb: AdbStatus,
        wifi: WifiTelemetry,
        env: EnvironmentDiagnostics
    ): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()

        if (!wifi.isConnected) {
            items += DiagnosticItem(
                id = "no_wifi",
                title = "Chưa kết nối mạng Wi-Fi",
                detail = "Gỡ lỗi qua Wi-Fi không hoạt động trên 4G/5G. Hãy kết nối cùng mạng Wi-Fi với máy tính.",
                severity = DiagnosticSeverity.BLOCKER,
                action = DiagnosticAction.OPEN_WIFI_SETTINGS,
                actionLabel = "Mở Cài đặt Wi-Fi"
            )
        }

        if (wifi.hasDualWifiConflict) {
            items += DiagnosticItem(
                id = "dual_wifi",
                title = "Xung đột Wi-Fi kép Xiaomi (${wifi.activeWlanInterfaces.joinToString(", ")})",
                detail = "Tăng tốc Wi-Fi kép làm đổi BSSID liên tục giữa 2.4GHz và 5GHz, khiến popup \"Cho phép gỡ lỗi qua Wi-Fi\" tự tắt phụt sau 0.05s.",
                severity = DiagnosticSeverity.BLOCKER,
                action = DiagnosticAction.OPEN_WIFI_SETTINGS,
                actionLabel = "Tắt Wi-Fi kép"
            )
        }

        if (!adb.adbEnabled && !adb.adbWifiEnabled) {
            items += DiagnosticItem(
                id = "adb_off",
                title = "Gỡ lỗi USB (ADB) đang TẮT",
                detail = "Một số máy/ROM yêu cầu bật Gỡ lỗi USB trước thì công tắc Gỡ lỗi qua Wi-Fi mới duy trì ổn định.",
                severity = DiagnosticSeverity.BLOCKER,
                action = if (adb.hasWriteSecureSettings) DiagnosticAction.ENABLE_ADB else DiagnosticAction.OPEN_DEV_OPTIONS,
                actionLabel = if (adb.hasWriteSecureSettings) "Bật lại ADB" else "Mở Dev Options"
            )
        }

        if (env.vpnActive) {
            items += DiagnosticItem(
                id = "vpn_active",
                title = "Đang bật VPN",
                detail = "VPN chặn định tuyến LAN nội bộ và mDNS, khiến Android từ chối bật Gỡ lỗi qua Wi-Fi hoặc máy Mac không connect được.",
                severity = DiagnosticSeverity.BLOCKER,
                action = DiagnosticAction.OPEN_VPN_SETTINGS,
                actionLabel = "Mở Cài đặt VPN"
            )
        }

        if (!env.locationEnabled) {
            items += DiagnosticItem(
                id = "location_off",
                title = "Dịch vụ Vị trí (Location) đang TẮT",
                detail = "Android cần đọc thông tin BSSID của mạng Wi-Fi để xác thực popup Gỡ lỗi qua Wi-Fi; nếu Location tắt, popup có thể tự đóng ngay.",
                severity = DiagnosticSeverity.WARNING,
                action = DiagnosticAction.OPEN_LOCATION_SETTINGS,
                actionLabel = "Bật Vị trí"
            )
        }

        if (env.isCustomPrivateDns) {
            val hostHint = env.privateDnsHost?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            items += DiagnosticItem(
                id = "private_dns",
                title = "Đang bật Private DNS tùy chỉnh$hostHint",
                detail = "DNS riêng có thể gây lỗi nhận diện mạng nội bộ hoặc mDNS khi bật Wireless Debugging. Bạn có thể tắt nhanh khi debug rồi bật lại sau.",
                severity = DiagnosticSeverity.WARNING,
                action = if (adb.hasWriteSecureSettings) DiagnosticAction.DISABLE_PRIVATE_DNS else DiagnosticAction.OPEN_NETWORK_SETTINGS,
                actionLabel = if (adb.hasWriteSecureSettings) "Tắt nhanh Private DNS" else "Mở Cài đặt mạng"
            )
        }

        val port = adb.effectivePort
        if (port != null && adb.isTcpPortListening && !adb.adbWifiEnabled) {
            items += DiagnosticItem(
                id = "tcp_port_conflict",
                title = "Cổng TCP $port đang mở song song",
                detail = "Bạn đã có thể dùng \"adb connect\" qua cổng $port mà không cần gạt nút Gỡ lỗi qua Wi-Fi. Nếu vẫn muốn bật Wireless Debugging, hãy reset ADB trước để tránh xung đột.",
                severity = DiagnosticSeverity.WARNING,
                action = if (adb.hasWriteSecureSettings) DiagnosticAction.RESET_ADB else DiagnosticAction.OPEN_DEV_OPTIONS,
                actionLabel = if (adb.hasWriteSecureSettings) "Ngắt & Reset ADB" else "Mở Dev Options"
            )
        }

        return items.sortedBy { it.severity.ordinal }
    }
}

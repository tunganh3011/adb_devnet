package com.devnet.adbcompanion.domain

import com.devnet.adbcompanion.domain.model.AdbStatus
import com.devnet.adbcompanion.domain.model.DevNetSnapshot
import com.devnet.adbcompanion.domain.model.EnvironmentDiagnostics
import com.devnet.adbcompanion.domain.model.PingSample
import com.devnet.adbcompanion.domain.model.WifiTelemetry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugSessionTest {

    @Test
    fun `report orders events and masks network identifiers`() {
        val session = DebugSession(startedAtMs = 1000L)
        val snapshot = DevNetSnapshot(
            timestampMs = 1100L,
            adbStatus = AdbStatus(adbWifiEnabled = true),
            wifiTelemetry = WifiTelemetry(
                isConnected = true,
                ssid = "Office-Wi-Fi",
                bssid = "08:12:34:56:78:90",
                ipAddress = "192.168.1.10",
                gatewayIp = "192.168.1.1",
                frequencyMhz = 5180,
                rssiDbm = -65
            )
        )
        session.recordSnapshot(snapshot)
        session.markIssue(2400L)
        session.recordPing("192.168.1.1", isGateway = true, PingSample(1500L, 12.0))
        session.recordSnapshot(
            snapshot.copy(
                timestampMs = 2500L,
                wifiTelemetry = snapshot.wifiTelemetry.copy(bssid = "08:12:34:56:78:91"),
                environment = EnvironmentDiagnostics(vpnActive = true)
            )
        )
        session.recordPing("my-mac.local", isGateway = false, PingSample(2600L, null))

        val report = session.buildReport(3000L)

        assertTrue(report.contains("Ping #1 (Gateway Wi-Fi): 12.0 ms · RSSI -65 dBm"))
        assertTrue(report.contains("Ping #2 (Đích LAN): không có số đo"))
        assertTrue(report.indexOf("Ping #1") < report.indexOf("Đánh dấu lỗi"))
        assertTrue(report.contains("Đổi điểm phát Wi-Fi"))
        assertTrue(report.contains("VPN: Bật"))
        assertTrue(report.contains("Wireless Debugging: Bật"))
        for (identifier in listOf("192.168.", "08:12:34", "Office-Wi-Fi", "my-mac.local")) {
            assertFalse(report.contains(identifier))
        }
    }

    @Test
    fun `report identifies when old entries were dropped`() {
        val session = DebugSession(startedAtMs = 1000L)
        repeat(601) { index -> session.markIssue(1000L + index) }

        assertTrue(session.buildReport(2000L).contains("Đã lược 1 dòng cũ."))
    }
}

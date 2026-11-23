package com.devnet.adbcompanion.domain

import com.devnet.adbcompanion.domain.model.AdbStatus
import com.devnet.adbcompanion.domain.model.DiagnosticSeverity
import com.devnet.adbcompanion.domain.model.EnvironmentDiagnostics
import com.devnet.adbcompanion.domain.model.WifiTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDebugDiagnosticsTest {

    @Test
    fun `evaluate returns empty list when environment is clean`() {
        val adb = AdbStatus(adbEnabled = true, adbWifiEnabled = true)
        val wifi = WifiTelemetry(
            isConnected = true,
            ipAddress = "192.168.8.232",
            activeWlanInterfaces = listOf("wlan0")
        )
        val env = EnvironmentDiagnostics(
            vpnActive = false,
            privateDnsMode = "opportunistic",
            locationEnabled = true
        )

        val result = WifiDebugDiagnostics.evaluate(adb, wifi, env)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `evaluate detects dual wifi vpn location off custom dns and port 5555 conflict`() {
        val adb = AdbStatus(
            adbEnabled = true,
            adbWifiEnabled = false,
            tcpPort = 5555,
            isTcpPortListening = true
        )
        val wifi = WifiTelemetry(
            isConnected = true,
            ipAddress = "192.168.8.232",
            activeWlanInterfaces = listOf("wlan0", "wlan1")
        )
        val env = EnvironmentDiagnostics(
            vpnActive = true,
            privateDnsMode = "hostname",
            privateDnsHost = "dns.google",
            locationEnabled = false
        )

        val result = WifiDebugDiagnostics.evaluate(adb, wifi, env)
        val ids = result.map { it.id }

        assertEquals(
            listOf("dual_wifi", "vpn_active", "location_off", "private_dns", "tcp_port_conflict"),
            ids
        )
        assertEquals(DiagnosticSeverity.BLOCKER, result[0].severity)
        assertEquals(DiagnosticSeverity.BLOCKER, result[1].severity)
        assertEquals(DiagnosticSeverity.WARNING, result[2].severity)
    }
}

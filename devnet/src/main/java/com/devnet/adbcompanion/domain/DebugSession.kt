package com.devnet.adbcompanion.domain

import com.devnet.adbcompanion.domain.model.DevNetSnapshot
import com.devnet.adbcompanion.domain.model.PingSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class DebugSession(private val startedAtMs: Long) {
    private data class Entry(val timestampMs: Long, val detail: String)

    private val entries = ArrayDeque<Entry>()
    private var droppedEntries = 0
    private var lastSnapshot: DevNetSnapshot? = null
    private val targetNumbers = mutableMapOf<String, Int>()

    fun recordSnapshot(snapshot: DevNetSnapshot) {
        val previous = lastSnapshot
        if (snapshot.timestampMs < startedAtMs ||
            (previous != null && snapshot.timestampMs < previous.timestampMs)
        ) return

        val wifi = snapshot.wifiTelemetry
        val oldWifi = previous?.wifiTelemetry
        val at = snapshot.timestampMs
        if (oldWifi == null || wifi.isConnected != oldWifi.isConnected || wifi.bandLabel != oldWifi.bandLabel) {
            add(at, if (wifi.isConnected) "Wi-Fi kết nối (${wifi.bandLabel})" else "Wi-Fi mất kết nối")
        }
        if (wifi.isConnected && oldWifi?.isConnected == true &&
            wifi.bssid != null && oldWifi.bssid != null && wifi.bssid != oldWifi.bssid
        ) {
            add(at, "Đổi điểm phát Wi-Fi")
        }

        val vpn = snapshot.environment.vpnActive
        if (previous == null || vpn != previous.environment.vpnActive) {
            add(at, "VPN: ${status(vpn)}")
        }

        val adb = snapshot.adbStatus
        if (adb.adbWifiEnabled != previous?.adbStatus?.adbWifiEnabled) {
            add(at, "Wireless Debugging: ${status(adb.adbWifiEnabled)}")
        }
        if (adb.isTcpPortListening != previous?.adbStatus?.isTcpPortListening) {
            add(at, "ADB TCP/IP: ${status(adb.isTcpPortListening)}")
        }
        lastSnapshot = snapshot
    }

    fun recordPing(host: String, isGateway: Boolean, sample: PingSample) {
        if (sample.timestampMs < startedAtMs) return
        val targetNumber = targetNumbers.getOrPut(host) { targetNumbers.size + 1 }
        val target = if (isGateway) "Gateway Wi-Fi" else "Đích LAN"
        val rtt = sample.rttMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "không có số đo"
        val rssi = lastSnapshot?.wifiTelemetry
            ?.takeIf { it.isConnected }?.rssiDbm?.takeIf { it in -126..0 }
        val signal = rssi?.let { " · RSSI $it dBm" }.orEmpty()
        add(sample.timestampMs, "Ping #$targetNumber ($target): $rtt$signal")
    }

    fun markIssue(timestampMs: Long) {
        add(timestampMs, "Đánh dấu lỗi")
    }

    fun buildReport(endedAtMs: Long): String = buildString {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        appendLine("Phiên debug DevNet")
        appendLine("Bắt đầu: ${dateFormat.format(Date(startedAtMs))}")
        appendLine("Kết thúc: ${dateFormat.format(Date(endedAtMs))}")
        if (droppedEntries > 0) appendLine("Đã lược $droppedEntries dòng cũ.")
        for (entry in entries.sortedBy { it.timestampMs }) {
            val seconds = (entry.timestampMs - startedAtMs).coerceAtLeast(0L) / 1000.0
            appendLine(String.format(Locale.US, "+%.1fs  %s", seconds, entry.detail))
        }
    }.trimEnd()

    private fun add(timestampMs: Long, detail: String) {
        if (entries.size == MAX_ENTRIES) {
            entries.removeFirst()
            droppedEntries++
        }
        entries.addLast(Entry(timestampMs, detail))
    }

    private fun status(enabled: Boolean): String = if (enabled) "Bật" else "Tắt"

    private companion object {
        const val MAX_ENTRIES = 600
    }
}

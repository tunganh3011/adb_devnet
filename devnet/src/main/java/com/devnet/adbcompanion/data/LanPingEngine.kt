package com.devnet.adbcompanion.data

import com.devnet.adbcompanion.domain.model.AdbStatus
import com.devnet.adbcompanion.domain.model.LanMonitorState
import com.devnet.adbcompanion.domain.model.PingSample
import com.devnet.adbcompanion.domain.model.QualityTier
import com.devnet.adbcompanion.domain.model.ScrcpyRecommendation
import com.devnet.adbcompanion.domain.model.WifiTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

class LanPingEngine {
    suspend fun pingOnce(host: String): PingSample = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val now = System.currentTimeMillis()
        if (cleanHost.isEmpty() || !VALID_HOST_REGEX.matches(cleanHost)) {
            return@withContext PingSample(timestampMs = now, rttMs = null)
        }

        val rtt = runCatching {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", cleanHost)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                parsePingTimeMs(output)
            } else {
                null
            }
        }.getOrNull()

        PingSample(timestampMs = now, rttMs = rtt)
    }

    fun buildMonitorState(
        targetHost: String,
        isMonitoring: Boolean,
        samples: List<PingSample>
    ): LanMonitorState {
        val validRtts = samples.mapNotNull { it.rttMs }
        val avg = if (validRtts.isNotEmpty()) validRtts.average() else null
        val min = validRtts.minOrNull()
        val max = validRtts.maxOrNull()
        val jitter = if (validRtts.size >= 2) {
            validRtts.zipWithNext { a, b -> abs(b - a) }.average()
        } else {
            null
        }
        val lossPercent = if (samples.isNotEmpty()) {
            val lost = samples.count { it.rttMs == null }
            ((lost * 100.0) / samples.size).toInt()
        } else {
            0
        }

        return LanMonitorState(
            targetHost = targetHost,
            isMonitoring = isMonitoring,
            samples = samples,
            lastLatencyMs = samples.lastOrNull()?.rttMs,
            avgLatencyMs = avg,
            minLatencyMs = min,
            maxLatencyMs = max,
            jitterMs = jitter,
            packetLossPercent = lossPercent
        )
    }

    fun recommendScrcpy(
        adb: AdbStatus,
        wifi: WifiTelemetry,
        lan: LanMonitorState
    ): ScrcpyRecommendation {
        val serialArg = adb.connectionEndpoint(wifi.ipAddress)?.let { "-s $it" } ?: "-e"
        val noControlFlag = if (adb.adbInputEnabled == false) " -n" else ""
        val noControlNote = if (adb.adbInputEnabled == false) {
            " • Đã tự thêm -n vì chưa bật quyền click chuột (adbinput=0)"
        } else {
            ""
        }

        if (!wifi.isConnected) {
            return ScrcpyRecommendation(
                command = "scrcpy -d$noControlFlag",
                title = "Chưa có Wi-Fi (Dùng USB)",
                reason = "Điện thoại chưa kết nối Wi-Fi, dùng -d để chiếu qua cáp USB.$noControlNote",
                tier = QualityTier.WARN
            )
        }

        val avgMs = lan.avgLatencyMs
        val is5Or6Ghz = wifi.frequencyMhz >= 4900
        val highSpeed = wifi.linkSpeedMbps >= 300
        val highLoss = lan.packetLossPercent >= 10

        return when {
            highLoss || (avgMs != null && avgMs > 60.0) || wifi.linkSpeedMbps in 1..72 -> {
                ScrcpyRecommendation(
                    command = "scrcpy $serialArg -b 3M -m 1280 --max-fps=30$noControlFlag",
                    title = "Chế độ Tiết kiệm băng thông",
                    reason = "Mạng LAN đang trễ/yếu (${formatMs(avgMs)}, ${wifi.bandLabel}). Giảm bitrate & độ phân giải để hết giật.$noControlNote",
                    tier = QualityTier.POOR
                )
            }
            !is5Or6Ghz || !highSpeed || (avgMs != null && avgMs > 25.0) -> {
                ScrcpyRecommendation(
                    command = "scrcpy $serialArg -b 6M -m 1600$noControlFlag",
                    title = "Cân bằng ổn định",
                    reason = "${wifi.bandLabel} (${wifi.linkSpeedMbps} Mbps, ${formatMs(avgMs)}). Cấu hình mượt và ít rớt khung hình.$noControlNote",
                    tier = QualityTier.GOOD
                )
            }
            else -> {
                ScrcpyRecommendation(
                    command = "scrcpy $serialArg -b 12M$noControlFlag",
                    title = "Tối đa chất lượng (5GHz+)",
                    reason = "${wifi.bandLabel} tốc độ cao (${wifi.linkSpeedMbps} Mbps, ${formatMs(avgMs)}). Chiếu sắc nét, độ trễ thấp.$noControlNote",
                    tier = QualityTier.EXCELLENT
                )
            }
        }
    }

    private fun parsePingTimeMs(output: String): Double? {
        val match = TIME_REGEX.find(output) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()
    }

    private fun formatMs(ms: Double?): String {
        return if (ms == null) "chưa đo" else String.format(Locale.US, "%.1f ms", ms)
    }

    companion object {
        private val TIME_REGEX = Regex("""time[=<]([\d.]+)\s*ms""")
        private val VALID_HOST_REGEX = Regex("""^[a-zA-Z0-9.\-:]+$""")
    }
}

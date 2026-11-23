package com.devnet.adbcompanion.data

import com.devnet.adbcompanion.domain.model.AdbStatus
import com.devnet.adbcompanion.domain.model.PingSample
import com.devnet.adbcompanion.domain.model.QualityTier
import com.devnet.adbcompanion.domain.model.WifiTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanPingEngineTest {

    private val engine = LanPingEngine()

    @Test
    fun `buildMonitorState calculates average jitter and packet loss accurately`() {
        val samples = listOf(
            PingSample(1000L, 10.0),
            PingSample(2000L, 20.0),
            PingSample(3000L, null),
            PingSample(4000L, 14.0)
        )

        val state = engine.buildMonitorState("192.168.8.159", isMonitoring = true, samples = samples)

        assertEquals(25, state.packetLossPercent)
        assertEquals(14.0, state.lastLatencyMs!!, 0.01)
        assertEquals(10.0, state.minLatencyMs!!, 0.01)
        assertEquals(20.0, state.maxLatencyMs!!, 0.01)
        assertEquals(14.666, state.avgLatencyMs!!, 0.01)
        // Jitter between consecutive valid samples: |20-10|=10, |14-20|=6 -> avg = 8.0
        assertEquals(8.0, state.jitterMs!!, 0.01)
    }

    @Test
    fun `recommendScrcpy suggests high quality on 5GHz and appends -n when adbInput is false`() {
        val adb = AdbStatus(
            adbEnabled = true,
            tcpPort = 5555,
            isTcpPortListening = true,
            adbInputEnabled = false
        )
        val wifi = WifiTelemetry(
            isConnected = true,
            ipAddress = "192.168.8.232",
            frequencyMhz = 5745,
            linkSpeedMbps = 866
        )
        val lan = engine.buildMonitorState(
            "192.168.8.159",
            isMonitoring = false,
            samples = listOf(PingSample(1000L, 12.0))
        )

        val rec = engine.recommendScrcpy(adb, wifi, lan)

        assertEquals(QualityTier.EXCELLENT, rec.tier)
        assertTrue(rec.command.contains("-s 192.168.8.232:5555"))
        assertTrue(rec.command.contains("-b 12M"))
        assertTrue(rec.command.endsWith("-n"))
    }
}

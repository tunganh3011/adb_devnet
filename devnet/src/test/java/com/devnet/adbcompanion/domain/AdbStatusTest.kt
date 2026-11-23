package com.devnet.adbcompanion.domain

import com.devnet.adbcompanion.domain.model.AdbStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbStatusTest {

    private val ip = "192.168.1.20"

    @Test
    fun `wireless debugging can expose its own port with USB debugging off`() {
        val status = AdbStatus(adbWifiEnabled = true, wirelessPort = 37895)

        assertTrue(status.isWifiDebugActive)
        assertEquals("$ip:37895", status.connectionEndpoint(ip))
    }

    @Test
    fun `wireless and legacy endpoints keep their own ports`() {
        val status = AdbStatus(
            adbEnabled = true,
            adbWifiEnabled = true,
            tcpPort = 5555,
            isTcpPortListening = true,
            wirelessPort = 37895
        )

        assertEquals("$ip:37895", status.connectionEndpoint(ip))
        assertEquals("$ip:5555", status.legacyEndpoint(ip))
        assertEquals("$ip:5555", status.copy(wirelessPort = null).connectionEndpoint(ip))
    }

    @Test
    fun `unknown or inactive ports do not produce commands`() {
        val status = AdbStatus(adbWifiEnabled = true, tcpPort = 5555, wirelessPort = 65536)

        assertNull(status.connectionEndpoint(ip))
        assertNull(status.copy(adbWifiEnabled = false, wirelessPort = 37895).connectionEndpoint(ip))
        assertNull(status.copy(wirelessPort = 37895).connectionEndpoint(null))
    }
}

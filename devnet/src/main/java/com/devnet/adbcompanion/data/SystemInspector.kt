package com.devnet.adbcompanion.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.devnet.adbcompanion.domain.model.AdbStatus
import com.devnet.adbcompanion.domain.model.BatteryTelemetry
import com.devnet.adbcompanion.domain.model.DevNetSnapshot
import com.devnet.adbcompanion.domain.model.EnvironmentDiagnostics
import com.devnet.adbcompanion.domain.model.ProxyStatus
import com.devnet.adbcompanion.domain.model.WifiTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.math.abs

class SystemInspector(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    suspend fun captureSnapshot(): DevNetSnapshot = withContext(Dispatchers.IO) {
        val adbStatus = readAdbStatus()
        val wifiTelemetry = readWifiTelemetry()
        val proxyStatus = readProxyStatus()
        val environment = readEnvironmentDiagnostics()
        val batteryTelemetry = readBatteryTelemetry()
        DevNetSnapshot(
            adbStatus = adbStatus,
            wifiTelemetry = wifiTelemetry,
            proxyStatus = proxyStatus,
            environment = environment,
            batteryTelemetry = batteryTelemetry,
            timestampMs = System.currentTimeMillis()
        )
    }

    fun hasWriteSecureSettings(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun setAdbWifiEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasWriteSecureSettings()) { "Chưa có quyền WRITE_SECURE_SETTINGS" }
            Settings.Global.putInt(contentResolver, KEY_ADB_WIFI_ENABLED, if (enabled) 1 else 0)
            Unit
        }
    }

    suspend fun resetAdbConnections(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasWriteSecureSettings()) { "Chưa có quyền WRITE_SECURE_SETTINGS" }
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 0)
            delay(750L)
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 1)
            Unit
        }
    }

    suspend fun killAllAdb(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasWriteSecureSettings()) { "Chưa có quyền WRITE_SECURE_SETTINGS" }
            Settings.Global.putInt(contentResolver, KEY_ADB_WIFI_ENABLED, 0)
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 0)
            Unit
        }
    }

    suspend fun enableAdb(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasWriteSecureSettings()) { "Chưa có quyền WRITE_SECURE_SETTINGS" }
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(contentResolver, KEY_ADB_WIFI_ENABLED, 1)
            Unit
        }
    }

    suspend fun setPrivateDnsEnabled(enabled: Boolean, hostname: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasWriteSecureSettings()) { "Chưa có quyền WRITE_SECURE_SETTINGS" }
            if (enabled) {
                val target = hostname.trim()
                require(target.isNotEmpty()) { "Tên máy chủ Private DNS không được để trống" }
                Settings.Global.putString(contentResolver, "private_dns_specifier", target)
                Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
            } else {
                Settings.Global.putString(contentResolver, "private_dns_mode", "off")
            }
            Unit
        }
    }

    private fun readAdbStatus(): AdbStatus {
        val adbEnabled = readGlobalInt(Settings.Global.ADB_ENABLED) == 1
        val adbWifiEnabled = readGlobalInt(KEY_ADB_WIFI_ENABLED) == 1
        val tcpPort = getSystemProperty("service.adb.tcp.port").toIntOrNull()?.takeIf { it in 1..65535 }
        val isListening = if (adbEnabled && tcpPort != null) {
            isLocalPortOpen(tcpPort)
        } else {
            false
        }
        val tlsEnabled = getSystemProperty("persist.adb.tls_server.enable") == "1"
        val wirelessPort = if (adbWifiEnabled) {
            getSystemProperty("service.adb.tls.port").toIntOrNull()
                ?.takeIf { it in 1..65535 && isLocalPortOpen(it) }
        } else {
            null
        }
        val adbInput = getSystemProperty("persist.security.adbinput").toBooleanFlagOrNull()
        val adbInstall = getSystemProperty("persist.security.adbinstall").toBooleanFlagOrNull()

        return AdbStatus(
            adbEnabled = adbEnabled,
            adbWifiEnabled = adbWifiEnabled,
            tcpPort = tcpPort,
            isTcpPortListening = isListening,
            tlsServerEnabled = tlsEnabled,
            adbInputEnabled = adbInput,
            adbInstallEnabled = adbInstall,
            hasWriteSecureSettings = hasWriteSecureSettings(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            wirelessPort = wirelessPort
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun readWifiTelemetry(): WifiTelemetry {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val hasWifiTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val activeWifiNetwork = activeNetwork?.takeIf { hasWifiTransport }

        val wlanInterfaces = scanActiveWlanInterfaces()
        val primaryIp = readIpFromLinkProperties(activeWifiNetwork)
            ?: wlanInterfaces.firstOrNull { it.first == "wlan0" }?.second
            ?: wlanInterfaces.firstOrNull()?.second

        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (caps?.transportInfo as? WifiInfo) ?: wifiManager.connectionInfo
        } else {
            wifiManager.connectionInfo
        }

        val rawSsid = wifiInfo?.ssid?.trim('"')?.takeUnless {
            it.isBlank() || it.equals("<unknown ssid>", ignoreCase = true)
        }
        val rawBssid = wifiInfo?.bssid?.takeUnless {
            it.isBlank() || it == "02:00:00:00:00:00"
        }

        val isConnected = hasWifiTransport || !primaryIp.isNullOrBlank()
        val gatewayIp = if (isConnected) readGatewayIp(activeWifiNetwork) else null
        return WifiTelemetry(
            isConnected = isConnected,
            ipAddress = primaryIp,
            gatewayIp = gatewayIp,
            ssid = rawSsid,
            bssid = rawBssid,
            frequencyMhz = wifiInfo?.frequency ?: 0,
            linkSpeedMbps = wifiInfo?.linkSpeed ?: 0,
            rssiDbm = wifiInfo?.rssi ?: -127,
            activeWlanInterfaces = wlanInterfaces.map { it.first }.distinct()
        )
    }

    private fun readProxyStatus(): ProxyStatus {
        val defaultProxy = connectivityManager.defaultProxy
        if (defaultProxy != null && !defaultProxy.host.isNullOrBlank() && defaultProxy.port > 0) {
            val reachable = isTcpPortReachable(defaultProxy.host, defaultProxy.port, timeoutMs = 600)
            return ProxyStatus(
                isConfigured = true,
                host = defaultProxy.host,
                port = defaultProxy.port,
                isReachable = reachable
            )
        }

        val rawSetting = Settings.Global.getString(contentResolver, Settings.Global.HTTP_PROXY)
            ?.trim()
            .orEmpty()
        if (rawSetting.isNotBlank() && rawSetting != ":0") {
            val parts = rawSetting.split(":")
            val host = parts.getOrNull(0)?.trim()
            val port = parts.getOrNull(1)?.toIntOrNull()
            if (!host.isNullOrBlank() && port != null && port > 0) {
                val reachable = isTcpPortReachable(host, port, timeoutMs = 600)
                return ProxyStatus(
                    isConfigured = true,
                    host = host,
                    port = port,
                    isReachable = reachable
                )
            }
        }

        return ProxyStatus(isConfigured = false)
    }

    private fun scanActiveWlanInterfaces(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                if (!nif.name.startsWith("wlan")) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { ip ->
                            result.add(nif.name to ip)
                        }
                    }
                }
            }
        }
        return result.sortedBy { it.first }
    }

    private fun readIpFromLinkProperties(network: android.net.Network?): String? {
        if (network == null) return null
        val lp = connectivityManager.getLinkProperties(network) ?: return null
        return lp.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    @Suppress("DEPRECATION")
    private fun readGatewayIp(network: android.net.Network?): String? {
        if (network != null) {
            val lp = connectivityManager.getLinkProperties(network)
            val routeGateway = lp?.routes
                ?.firstOrNull { route ->
                    val hasDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        route.isDefaultRoute
                    } else {
                        route.destination.prefixLength == 0
                    }
                    hasDefault && route.gateway is Inet4Address
                }
                ?.gateway
                ?.hostAddress
            if (!routeGateway.isNullOrBlank()) return routeGateway
        }

        val dhcpGateway = wifiManager.dhcpInfo?.gateway ?: 0
        if (dhcpGateway != 0) {
            return intToIpv4(dhcpGateway)
        }
        return null
    }

    private fun intToIpv4(ipInt: Int): String {
        return "${ipInt and 0xff}.${ipInt shr 8 and 0xff}.${ipInt shr 16 and 0xff}.${ipInt shr 24 and 0xff}"
    }

    private fun readEnvironmentDiagnostics(): EnvironmentDiagnostics {
        @Suppress("DEPRECATION")
        val vpnActive = runCatching {
            connectivityManager.allNetworks.any { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false)

        val privateDnsMode = runCatching {
            Settings.Global.getString(contentResolver, "private_dns_mode")
        }.getOrNull()
        val privateDnsHost = runCatching {
            Settings.Global.getString(contentResolver, "private_dns_specifier")
        }.getOrNull()

        @Suppress("DEPRECATION")
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { locationManager.isLocationEnabled }.getOrDefault(true)
        } else {
            runCatching {
                Settings.Secure.getInt(
                    contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
                ) != Settings.Secure.LOCATION_MODE_OFF
            }.getOrDefault(true)
        }

        return EnvironmentDiagnostics(
            vpnActive = vpnActive,
            privateDnsMode = privateDnsMode,
            privateDnsHost = privateDnsHost,
            locationEnabled = locationEnabled
        )
    }

    private fun readBatteryTelemetry(): BatteryTelemetry {
        val intent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val percent = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 0

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        val pluggedSource = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Không dây"
            isCharging -> "Đang sạc"
            else -> "Dùng pin"
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val rawCurrent = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()?.takeIf { it != Int.MIN_VALUE && it != 0 }
        val currentMa = rawCurrent?.let { if (abs(it) > 20_000) it / 1000 else it }

        val nightCurrentFlag = runCatching {
            Settings.System.getInt(contentResolver, "flag_nightcurrent", -1)
        }.getOrDefault(-1).takeIf { it >= 0 }

        return BatteryTelemetry(
            levelPercent = percent,
            isCharging = isCharging,
            pluggedSource = pluggedSource,
            temperatureC = tempTenths / 10.0,
            voltageMv = voltageMv,
            currentMa = currentMa,
            nightCurrentFlag = nightCurrentFlag
        )
    }

    private fun readGlobalInt(key: String): Int {
        return runCatching {
            Settings.Global.getInt(contentResolver, key, 0)
        }.getOrDefault(0)
    }

    private fun isLocalPortOpen(port: Int): Boolean {
        return isTcpPortReachable("127.0.0.1", port, timeoutMs = 150)
    }

    private fun isTcpPortReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String {
        val reflected = runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, "") as? String
        }.getOrNull()
        if (!reflected.isNullOrEmpty()) return reflected.trim()

        return runCatching {
            val process = ProcessBuilder("/system/bin/getprop", key)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.trim()
        }.getOrDefault("")
    }

    private fun String.toBooleanFlagOrNull(): Boolean? = when (this) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }

    companion object {
        private const val KEY_ADB_WIFI_ENABLED = "adb_wifi_enabled"
    }
}

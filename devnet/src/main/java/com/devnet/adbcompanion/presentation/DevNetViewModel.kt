package com.devnet.adbcompanion.presentation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devnet.adbcompanion.data.LanPingEngine
import com.devnet.adbcompanion.data.PreferencesStore
import com.devnet.adbcompanion.data.SystemInspector
import com.devnet.adbcompanion.domain.DebugSession
import com.devnet.adbcompanion.domain.WifiDebugDiagnostics
import com.devnet.adbcompanion.domain.model.DevNetUiState
import com.devnet.adbcompanion.domain.model.PingSample
import com.devnet.adbcompanion.domain.model.ThemeMode
import com.devnet.adbcompanion.service.DevNetEventBus
import com.devnet.adbcompanion.service.DevNetMonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DevNetViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefs = PreferencesStore(appContext)
    private val inspector = SystemInspector(appContext)
    private val pingEngine = LanPingEngine()

    private val _uiState = MutableStateFlow(
        DevNetUiState(
            savedPrivateDnsHost = prefs.savedPrivateDnsHost,
            persistentNotificationEnabled = prefs.persistentNotificationEnabled,
            floatingHudEnabled = prefs.floatingHudEnabled,
            hasOverlayPermission = Settings.canDrawOverlays(appContext),
            hasNotificationPermission = hasNotificationPermission(),
            themeMode = prefs.themeMode
        )
    )
    val uiState: StateFlow<DevNetUiState> = _uiState.asStateFlow()

    private var snapshotJob: Job? = null
    private var lanMonitorJob: Job? = null
    private val samplesBuffer = ArrayDeque<PingSample>(MAX_SAMPLES)
    private var debugSession: DebugSession? = null
    private var monitorStartedForSession = false

    init {
        viewModelScope.launch {
            DevNetEventBus.stateChanged.collect {
                syncServiceFlagsFromPrefs()
                refreshSnapshot()
            }
        }
        startSnapshotPolling()
    }

    fun onResume() {
        syncServiceFlagsFromPrefs()
        viewModelScope.launch {
            refreshSnapshot()
            if (_uiState.value.lanMonitor.samples.isEmpty()) {
                probeOnce()
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshSnapshot()
            probeOnce()
        }
    }

    fun updateTargetHost(host: String) {
        prefs.targetHost = host
        samplesBuffer.clear()
        val state = _uiState.value
        val monitor = pingEngine.buildMonitorState(
            targetHost = host.trim(),
            isMonitoring = state.lanMonitor.isMonitoring,
            samples = emptyList()
        )
        _uiState.update {
            it.copy(
                lanMonitor = monitor,
                scrcpyRecommendation = pingEngine.recommendScrcpy(
                    state.snapshot.adbStatus,
                    state.snapshot.wifiTelemetry,
                    monitor
                )
            )
        }
    }

    fun useGatewayAsTarget() {
        val gateway = _uiState.value.snapshot.wifiTelemetry.gatewayIp
        if (!gateway.isNullOrBlank()) {
            updateTargetHost(gateway)
            probeOnce()
        } else {
            showToast("Chưa xác định được Gateway Wi-Fi")
        }
    }

    fun probeOnce() {
        val host = resolveTargetHost() ?: return
        viewModelScope.launch {
            measurePing(host)
        }
    }

    fun toggleLanMonitor() {
        if (debugSession != null) return
        if (lanMonitorJob?.isActive == true) {
            stopLanMonitor()
            return
        }

        if (resolveTargetHost() == null) {
            showToast("Hãy nhập IP máy Mac hoặc kết nối Wi-Fi trước")
            return
        }
        startLanMonitor()
    }

    fun toggleDebugSession() {
        val session = debugSession
        if (session == null) {
            val newSession = DebugSession(System.currentTimeMillis())
            debugSession = newSession
            monitorStartedForSession = lanMonitorJob?.isActive != true
            _uiState.update { it.copy(isRecordingDebugSession = true, debugSessionReport = null) }
            if (monitorStartedForSession) startLanMonitor()
            viewModelScope.launch { refreshSnapshot() }
        } else {
            debugSession = null
            if (monitorStartedForSession) stopLanMonitor()
            monitorStartedForSession = false
            val report = session.buildReport(System.currentTimeMillis())
            _uiState.update { it.copy(isRecordingDebugSession = false, debugSessionReport = report) }
        }
    }

    fun markDebugIssue() {
        val session = debugSession ?: return
        session.markIssue(System.currentTimeMillis())
        showToast("Đã đánh dấu lỗi trong phiên")
    }

    private fun startLanMonitor() {
        _uiState.update { it.copy(lanMonitor = it.lanMonitor.copy(isMonitoring = true)) }
        lanMonitorJob = viewModelScope.launch {
            while (isActive) {
                resolveTargetHost()?.let { measurePing(it) }
                delay(1000L)
            }
        }
    }

    private fun stopLanMonitor() {
        lanMonitorJob?.cancel()
        lanMonitorJob = null
        _uiState.update { it.copy(lanMonitor = it.lanMonitor.copy(isMonitoring = false)) }
    }

    private suspend fun measurePing(host: String) {
        val session = debugSession
        val isGateway = host == _uiState.value.snapshot.wifiTelemetry.gatewayIp
        val sample = pingEngine.pingOnce(host)
        if (session === debugSession) session?.recordPing(host, isGateway, sample)
        if (host == resolveTargetHost()) appendPingSample(host, sample)
    }

    fun toggleWifiAdb(enabled: Boolean) {
        viewModelScope.launch {
            inspector.setAdbWifiEnabled(enabled)
                .onSuccess {
                    delay(250L)
                    refreshSnapshot()
                    showToast(if (enabled) "Đã bật Gỡ lỗi qua Wi-Fi" else "Đã tắt Gỡ lỗi qua Wi-Fi")
                }
                .onFailure { err ->
                    showToast(err.message ?: "Không thể thay đổi cài đặt ADB")
                }
        }
    }

    fun resetAdbConnections() {
        if (_uiState.value.isResettingAdb) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResettingAdb = true) }
            inspector.resetAdbConnections()
                .onSuccess {
                    delay(350L)
                    refreshSnapshot()
                    showToast("Đã ngắt & reset lại tiến trình ADB")
                }
                .onFailure { err ->
                    showToast(err.message ?: "Không thể reset ADB")
                }
            _uiState.update { it.copy(isResettingAdb = false) }
        }
    }

    fun killAllAdb() {
        viewModelScope.launch {
            inspector.killAllAdb()
                .onSuccess {
                    delay(250L)
                    refreshSnapshot()
                    showToast("Đã tắt toàn bộ kết nối ADB (Wi-Fi & USB)")
                }
                .onFailure { err ->
                    showToast(err.message ?: "Không thể tắt ADB")
                }
        }
    }

    fun enableAdb() {
        viewModelScope.launch {
            inspector.enableAdb()
                .onSuccess {
                    delay(250L)
                    refreshSnapshot()
                    showToast("Đã bật lại ADB")
                }
                .onFailure { err ->
                    showToast(err.message ?: "Không thể bật ADB")
                }
        }
    }

    fun updatePrivateDnsHost(host: String) {
        if (host.trim().isNotEmpty()) {
            prefs.savedPrivateDnsHost = host
        }
        _uiState.update { it.copy(savedPrivateDnsHost = host) }
    }

    fun togglePrivateDns(enabled: Boolean) {
        viewModelScope.launch {
            val host = _uiState.value.savedPrivateDnsHost.trim().ifEmpty { prefs.savedPrivateDnsHost }
            prefs.savedPrivateDnsHost = host
            inspector.setPrivateDnsEnabled(enabled, host)
                .onSuccess {
                    delay(200L)
                    refreshSnapshot()
                    showToast(
                        if (enabled) "Đã BẬT chặn QC ($host)"
                        else "Đã TẮT Private DNS"
                    )
                }
                .onFailure { err ->
                    showToast(err.message ?: "Không thể thay đổi Private DNS")
                }
        }
    }

    fun setPersistentNotification(enabled: Boolean) {
        prefs.persistentNotificationEnabled = enabled
        _uiState.update { it.copy(persistentNotificationEnabled = enabled) }
        DevNetMonitorService.sync(appContext)
    }

    fun setFloatingHud(enabled: Boolean) {
        prefs.floatingHudEnabled = enabled
        _uiState.update {
            it.copy(
                floatingHudEnabled = enabled,
                hasOverlayPermission = Settings.canDrawOverlays(appContext)
            )
        }
        DevNetMonitorService.sync(appContext)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    private fun startSnapshotPolling() {
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            while (isActive) {
                refreshSnapshot()
                delay(2500L)
            }
        }
    }

    private suspend fun refreshSnapshot() {
        val snapshot = inspector.captureSnapshot()
        if (snapshot.timestampMs < _uiState.value.snapshot.timestampMs) return
        debugSession?.recordSnapshot(snapshot)

        if (snapshot.environment.isCustomPrivateDns && !snapshot.environment.privateDnsHost.isNullOrBlank()) {
            prefs.savedPrivateDnsHost = snapshot.environment.privateDnsHost
        }
        val effectiveHost = resolveTargetHost(snapshot.wifiTelemetry.gatewayIp).orEmpty()
        if (_uiState.value.lanMonitor.targetHost != effectiveHost) samplesBuffer.clear()

        _uiState.update { current ->
            val monitor = if (current.lanMonitor.targetHost == effectiveHost) {
                current.lanMonitor
            } else {
                pingEngine.buildMonitorState(effectiveHost, current.lanMonitor.isMonitoring, emptyList())
            }
            val recommendation = pingEngine.recommendScrcpy(
                adb = snapshot.adbStatus,
                wifi = snapshot.wifiTelemetry,
                lan = monitor
            )
            val diagnostics = WifiDebugDiagnostics.evaluate(
                adb = snapshot.adbStatus,
                wifi = snapshot.wifiTelemetry,
                env = snapshot.environment
            )
            val resolvedDnsHost = if (snapshot.environment.isCustomPrivateDns &&
                !snapshot.environment.privateDnsHost.isNullOrBlank()
            ) {
                snapshot.environment.privateDnsHost
            } else {
                current.savedPrivateDnsHost
            }
            current.copy(
                snapshot = snapshot,
                lanMonitor = monitor,
                scrcpyRecommendation = recommendation,
                diagnostics = diagnostics,
                savedPrivateDnsHost = resolvedDnsHost,
                hasOverlayPermission = Settings.canDrawOverlays(appContext),
                hasNotificationPermission = hasNotificationPermission()
            )
        }
    }

    private fun appendPingSample(host: String, sample: PingSample) {
        if (_uiState.value.lanMonitor.targetHost != host) samplesBuffer.clear()
        if (samplesBuffer.size >= MAX_SAMPLES) {
            samplesBuffer.removeFirst()
        }
        samplesBuffer.addLast(sample)
        val snapshotList = samplesBuffer.toList()

        _uiState.update { current ->
            val monitor = pingEngine.buildMonitorState(
                targetHost = host,
                isMonitoring = current.lanMonitor.isMonitoring,
                samples = snapshotList
            )
            val recommendation = pingEngine.recommendScrcpy(
                adb = current.snapshot.adbStatus,
                wifi = current.snapshot.wifiTelemetry,
                lan = monitor
            )
            current.copy(
                lanMonitor = monitor,
                scrcpyRecommendation = recommendation
            )
        }
    }

    private fun syncServiceFlagsFromPrefs() {
        _uiState.update {
            it.copy(
                persistentNotificationEnabled = prefs.persistentNotificationEnabled,
                floatingHudEnabled = prefs.floatingHudEnabled,
                hasOverlayPermission = Settings.canDrawOverlays(appContext),
                hasNotificationPermission = hasNotificationPermission()
            )
        }
    }

    private fun resolveTargetHost(fallbackGateway: String? = _uiState.value.snapshot.wifiTelemetry.gatewayIp): String? {
        val saved = prefs.targetHost.trim()
        if (saved.isNotEmpty()) return saved
        return fallbackGateway
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onCleared() {
        snapshotJob?.cancel()
        lanMonitorJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_SAMPLES = 30
    }
}

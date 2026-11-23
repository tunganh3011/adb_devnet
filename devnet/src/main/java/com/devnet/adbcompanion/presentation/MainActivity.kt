package com.devnet.adbcompanion.presentation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.devnet.adbcompanion.R
import com.devnet.adbcompanion.domain.model.DevNetUiState
import com.devnet.adbcompanion.domain.model.DiagnosticAction
import com.devnet.adbcompanion.domain.model.DiagnosticSeverity
import com.devnet.adbcompanion.domain.model.ThemeMode
import com.devnet.adbcompanion.presentation.components.AdbControlCard
import com.devnet.adbcompanion.presentation.components.AutomationCard
import com.devnet.adbcompanion.presentation.components.BatteryChargeCard
import com.devnet.adbcompanion.presentation.components.DebugSessionCard
import com.devnet.adbcompanion.presentation.components.DeviceHealthCard
import com.devnet.adbcompanion.presentation.components.LanMonitorCard
import com.devnet.adbcompanion.presentation.components.PrivateDnsCard
import com.devnet.adbcompanion.presentation.components.ProxyWatchdogCard
import com.devnet.adbcompanion.presentation.components.StatusPill
import com.devnet.adbcompanion.presentation.theme.DevNetColors
import com.devnet.adbcompanion.presentation.theme.DevNetTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: DevNetViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setPersistentNotification(true)
        } else {
            viewModel.setPersistentNotification(false)
            viewModel.showToast("Cần quyền Thông báo để ghim thanh trạng thái")
        }
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            viewModel.setFloatingHud(true)
        } else {
            viewModel.setFloatingHud(false)
            viewModel.showToast("Chưa cấp quyền Hiển thị trên ứng dụng khác")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsState()
            val darkTheme = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LaunchedEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            DevNetTheme(darkTheme = darkTheme) {
                LaunchedEffect(state.toastMessage) {
                    val msg = state.toastMessage
                    if (!msg.isNullOrBlank()) {
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        viewModel.consumeToast()
                    }
                }

                DevNetDashboardScreen(
                    state = state,
                    packageName = packageName,
                    onThemeModeChange = viewModel::setThemeMode,
                    onRefresh = { viewModel.refreshNow() },
                    onCopyCommand = { cmd -> copyToClipboard(cmd) },
                    onToggleWifiAdb = { enabled -> viewModel.toggleWifiAdb(enabled) },
                    onResetAdb = { viewModel.resetAdbConnections() },
                    onKillAllAdb = { viewModel.killAllAdb() },
                    onEnableAdb = { viewModel.enableAdb() },
                    onTargetHostChange = { host -> viewModel.updateTargetHost(host) },
                    onUseGateway = { viewModel.useGatewayAsTarget() },
                    onProbeOnce = { viewModel.probeOnce() },
                    onToggleMonitor = { viewModel.toggleLanMonitor() },
                    onToggleDebugSession = viewModel::toggleDebugSession,
                    onMarkDebugIssue = viewModel::markDebugIssue,
                    onCopyDebugReport = { report ->
                        copyToClipboard(report, "Báo cáo phiên debug", "Đã copy báo cáo")
                    },
                    onToggleNotification = { enabled -> handleToggleNotification(enabled) },
                    onToggleFloatingHud = { enabled -> handleToggleFloatingHud(enabled) },
                    onPrivateDnsHostChange = { host -> viewModel.updatePrivateDnsHost(host) },
                    onTogglePrivateDns = { enabled -> viewModel.togglePrivateDns(enabled) },
                    onDiagnosticAction = { action -> handleDiagnosticAction(action) },
                    onOpenDeveloperSettings = { openDeveloperSettings() },
                    onOpenWifiSettings = { openWifiSettings() },
                    onOpenChargeSettings = { openChargeFeatureSettings() },
                    onOpenBatteryOverview = { openBatteryOverviewSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun handleToggleNotification(enabled: Boolean) {
        if (!enabled) {
            viewModel.setPersistentNotification(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.setPersistentNotification(true)
    }

    private fun handleToggleFloatingHud(enabled: Boolean) {
        if (!enabled) {
            viewModel.setFloatingHud(false)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlaySettingsLauncher.launch(intent)
            return
        }
        viewModel.setFloatingHud(true)
    }

    private fun copyToClipboard(
        text: String,
        label: String = "DevNet Command",
        message: String = "Đã copy: $text"
    ) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openSettingsAction(action: String) {
        runCatching {
            startActivity(Intent(action))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun openFirstAvailableIntent(vararg actions: String) {
        for (action in actions) {
            val started = runCatching {
                startActivity(Intent(action))
                true
            }.getOrDefault(false)
            if (started) return
        }
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun openDeveloperSettings() = openSettingsAction(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    private fun openWifiSettings() = openSettingsAction(Settings.ACTION_WIFI_SETTINGS)

    private fun openChargeFeatureSettings() = openFirstAvailableIntent(
        "miui.intent.action.CHARGE_FEATURE_MANAGER",
        "com.miui.powercenter.FAST_CHARGE",
        Intent.ACTION_POWER_USAGE_SUMMARY,
        Settings.ACTION_BATTERY_SAVER_SETTINGS
    )

    private fun openBatteryOverviewSettings() = openFirstAvailableIntent(
        "miui.intent.action.POWER_BATTERY",
        "com.miui.securitycenter.action.POWER_SETTINGS",
        Intent.ACTION_POWER_USAGE_SUMMARY,
        Settings.ACTION_BATTERY_SAVER_SETTINGS
    )

    private fun handleDiagnosticAction(action: DiagnosticAction) {
        when (action) {
            DiagnosticAction.OPEN_WIFI_SETTINGS -> openWifiSettings()
            DiagnosticAction.OPEN_VPN_SETTINGS -> openSettingsAction(Settings.ACTION_VPN_SETTINGS)
            DiagnosticAction.OPEN_LOCATION_SETTINGS -> openSettingsAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            DiagnosticAction.OPEN_NETWORK_SETTINGS -> openSettingsAction(Settings.ACTION_WIRELESS_SETTINGS)
            DiagnosticAction.OPEN_DEV_OPTIONS -> openDeveloperSettings()
            DiagnosticAction.RESET_ADB -> viewModel.resetAdbConnections()
            DiagnosticAction.ENABLE_ADB -> viewModel.enableAdb()
            DiagnosticAction.DISABLE_PRIVATE_DNS -> viewModel.togglePrivateDns(false)
            DiagnosticAction.NONE -> Unit
        }
    }
}

private enum class DevNetSection(val title: String, val subtitle: String) {
    ADB_CONTROL("ADB & Kết nối", "Wireless Debugging, TCP/IP, Ngắt & Reset"),
    DIAGNOSTICS("Chẩn đoán Wi-Fi", "Xung đột Xiaomi, VPN, DNS, Vị trí"),
    LAN_SCRCPY("LAN & scrcpy", "Ping máy Mac, Jitter & tối ưu"),
    PRIVATE_DNS("Private DNS (Chặn QC)", "Bật/tắt AdGuard DNS 1 chạm"),
    BATTERY("Pin & Bảo vệ sạc", "Mức pin, dòng sạc & cài đặt bảo vệ"),
    PROXY_BACKGROUND("Proxy & Chạy nền", "Charles/Proxyman, HUD & thông báo")
}

@Composable
private fun DevNetDashboardScreen(
    state: DevNetUiState,
    packageName: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRefresh: () -> Unit,
    onCopyCommand: (String) -> Unit,
    onToggleWifiAdb: (Boolean) -> Unit,
    onResetAdb: () -> Unit,
    onKillAllAdb: () -> Unit,
    onEnableAdb: () -> Unit,
    onTargetHostChange: (String) -> Unit,
    onUseGateway: () -> Unit,
    onProbeOnce: () -> Unit,
    onToggleMonitor: () -> Unit,
    onToggleDebugSession: () -> Unit,
    onMarkDebugIssue: () -> Unit,
    onCopyDebugReport: (String) -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onToggleFloatingHud: (Boolean) -> Unit,
    onPrivateDnsHostChange: (String) -> Unit,
    onTogglePrivateDns: (Boolean) -> Unit,
    onDiagnosticAction: (DiagnosticAction) -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenChargeSettings: () -> Unit,
    onOpenBatteryOverview: () -> Unit
) {
    val adb = state.snapshot.adbStatus
    val wifi = state.snapshot.wifiTelemetry
    val proxy = state.snapshot.proxyStatus
    val env = state.snapshot.environment
    val battery = state.snapshot.batteryTelemetry

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var currentSection by rememberSaveable { mutableStateOf(DevNetSection.ADB_CONTROL) }

    BackHandler(enabled = drawerState.isOpen || currentSection != DevNetSection.ADB_CONTROL) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else {
            currentSection = DevNetSection.ADB_CONTROL
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DevNetColors.SurfaceCard,
                drawerContentColor = DevNetColors.TextPrimary,
                modifier = Modifier
                    .width(292.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DevNet ADB",
                        color = DevNetColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val ipBadge = adb.connectionEndpoint(wifi.ipAddress) ?: wifi.ipAddress ?: "Offline"
                    StatusPill(
                        text = "$ipBadge • ${battery.levelPercent}%",
                        color = if (adb.isWifiDebugActive) DevNetColors.Emerald else DevNetColors.Cyan
                    )
                    ThemeModeMenu(
                        themeMode = state.themeMode,
                        onThemeModeChange = onThemeModeChange
                    )
                }

                HorizontalDivider(color = DevNetColors.BorderSubtle)
                Spacer(modifier = Modifier.height(8.dp))

                DevNetSection.entries.forEach { section ->
                    val badge = when (section) {
                        DevNetSection.ADB_CONTROL -> when {
                            !adb.adbEnabled -> "OFF" to DevNetColors.Red
                            adb.isTcpPortListening -> "TCP" to DevNetColors.Emerald
                            adb.adbWifiEnabled -> "Wi-Fi" to DevNetColors.Cyan
                            else -> "USB" to DevNetColors.Amber
                        }
                        DevNetSection.DIAGNOSTICS -> {
                            val blockers = state.diagnostics.count { it.severity == DiagnosticSeverity.BLOCKER }
                            when {
                                blockers > 0 -> "$blockers lỗi" to DevNetColors.Red
                                state.diagnostics.isNotEmpty() -> "${state.diagnostics.size} cảnh báo" to DevNetColors.Amber
                                else -> "Sạch" to DevNetColors.Emerald
                            }
                        }
                        DevNetSection.LAN_SCRCPY -> wifi.bandLabel to DevNetColors.Cyan
                        DevNetSection.PRIVATE_DNS -> if (env.isCustomPrivateDns) {
                            "BẬT" to DevNetColors.Emerald
                        } else {
                            "TẮT" to DevNetColors.TextMuted
                        }
                        DevNetSection.BATTERY -> "${battery.levelPercent}%" to if (battery.isCharging) {
                            DevNetColors.Emerald
                        } else {
                            DevNetColors.Cyan
                        }
                        DevNetSection.PROXY_BACKGROUND -> when {
                            proxy.isDeadProxyWarning -> "DEAD" to DevNetColors.Red
                            proxy.isConfigured -> "ON" to DevNetColors.Cyan
                            else -> "OFF" to DevNetColors.TextMuted
                        }
                    }

                    NavigationDrawerItem(
                        selected = currentSection == section,
                        onClick = {
                            currentSection = section
                            coroutineScope.launch { drawerState.close() }
                        },
                        label = {
                            Column {
                                Text(
                                    text = section.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = section.subtitle,
                                    fontSize = 11.sp,
                                    color = DevNetColors.TextSecondary
                                )
                            }
                        },
                        badge = {
                            Text(
                                text = badge.first,
                                color = badge.second,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = DevNetColors.SurfaceElevated,
                            selectedTextColor = DevNetColors.Emerald,
                            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unselectedTextColor = DevNetColors.TextPrimary
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = DevNetColors.Background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DrawerMenuButton(
                        onClick = { coroutineScope.launch { drawerState.open() } }
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSection.title,
                            color = DevNetColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val netSummary = buildString {
                            append(wifi.ssid ?: "Wi-Fi")
                            if (wifi.bandLabel != "--") append(" • ${wifi.bandLabel}")
                            if (!wifi.gatewayIp.isNullOrBlank()) append(" • GW ${wifi.gatewayIp}")
                        }
                        Text(
                            text = netSummary,
                            color = DevNetColors.TextSecondary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = onRefresh,
                        border = BorderStroke(1.dp, DevNetColors.BorderSubtle),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Làm mới", color = DevNetColors.Cyan, fontSize = 12.sp)
                    }
                }

                when (currentSection) {
                    DevNetSection.ADB_CONTROL -> {
                        AdbControlCard(
                            adb = adb,
                            wifi = wifi,
                            isResetting = state.isResettingAdb,
                            packageName = packageName,
                            onCopyCommand = onCopyCommand,
                            onToggleWifiAdb = onToggleWifiAdb,
                            onResetAdb = onResetAdb,
                            onKillAllAdb = onKillAllAdb,
                            onEnableAdb = onEnableAdb,
                            onOpenDeveloperSettings = onOpenDeveloperSettings
                        )
                    }

                    DevNetSection.DIAGNOSTICS -> {
                        DeviceHealthCard(
                            adb = adb,
                            wifi = wifi,
                            environment = env,
                            diagnostics = state.diagnostics,
                            onDiagnosticAction = onDiagnosticAction,
                            onOpenDeveloperSettings = onOpenDeveloperSettings
                        )
                    }

                    DevNetSection.LAN_SCRCPY -> {
                        LanMonitorCard(
                            wifi = wifi,
                            lan = state.lanMonitor,
                            recommendation = state.scrcpyRecommendation,
                            isRecordingDebugSession = state.isRecordingDebugSession,
                            onTargetHostChange = onTargetHostChange,
                            onUseGateway = onUseGateway,
                            onProbeOnce = onProbeOnce,
                            onToggleMonitor = onToggleMonitor,
                            onCopyScrcpy = onCopyCommand
                        )
                        DebugSessionCard(
                            isRecording = state.isRecordingDebugSession,
                            report = state.debugSessionReport,
                            onToggleRecording = onToggleDebugSession,
                            onMarkIssue = onMarkDebugIssue,
                            onCopyReport = onCopyDebugReport
                        )
                    }

                    DevNetSection.PRIVATE_DNS -> {
                        PrivateDnsCard(
                            environment = env,
                            savedHost = state.savedPrivateDnsHost,
                            hasWriteSecureSettings = adb.hasWriteSecureSettings,
                            onHostChange = onPrivateDnsHostChange,
                            onTogglePrivateDns = onTogglePrivateDns
                        )
                    }

                    DevNetSection.BATTERY -> {
                        BatteryChargeCard(
                            battery = battery,
                            onOpenChargeSettings = onOpenChargeSettings,
                            onOpenBatteryOverview = onOpenBatteryOverview
                        )
                    }

                    DevNetSection.PROXY_BACKGROUND -> {
                        ProxyWatchdogCard(
                            proxy = proxy,
                            onOpenWifiSettings = onOpenWifiSettings
                        )
                        AutomationCard(
                            persistentNotificationEnabled = state.persistentNotificationEnabled,
                            floatingHudEnabled = state.floatingHudEnabled,
                            hasOverlayPermission = state.hasOverlayPermission,
                            onToggleNotification = onToggleNotification,
                            onToggleFloatingHud = onToggleFloatingHud
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ThemeModeMenu(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.theme_selection, stringResource(themeMode.labelRes())))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelRes())) },
                    onClick = {
                        onThemeModeChange(mode)
                        expanded = false
                    },
                    modifier = Modifier.semantics { selected = mode == themeMode }
                )
            }
        }
    }
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun DrawerMenuButton(onClick: () -> Unit) {
    val iconColor = DevNetColors.TextPrimary
    val menuDescription = stringResource(R.string.open_navigation_menu)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DevNetColors.SurfaceCard)
            .border(1.dp, DevNetColors.BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = menuDescription }
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val stroke = 2.dp.toPx()
            val yTop = size.height * 0.22f
            val yMid = size.height * 0.50f
            val yBot = size.height * 0.78f
            drawLine(
                color = iconColor,
                start = Offset(0f, yTop),
                end = Offset(size.width, yTop),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = iconColor,
                start = Offset(0f, yMid),
                end = Offset(size.width, yMid),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = iconColor,
                start = Offset(0f, yBot),
                end = Offset(size.width, yBot),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

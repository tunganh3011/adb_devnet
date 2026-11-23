package com.devnet.adbcompanion.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devnet.adbcompanion.domain.model.AdbStatus
import com.devnet.adbcompanion.domain.model.BatteryTelemetry
import com.devnet.adbcompanion.domain.model.DiagnosticAction
import com.devnet.adbcompanion.domain.model.DiagnosticItem
import com.devnet.adbcompanion.domain.model.DiagnosticSeverity
import com.devnet.adbcompanion.domain.model.EnvironmentDiagnostics
import com.devnet.adbcompanion.domain.model.LanMonitorState
import com.devnet.adbcompanion.domain.model.ProxyStatus
import com.devnet.adbcompanion.domain.model.QualityTier
import com.devnet.adbcompanion.domain.model.ScrcpyRecommendation
import com.devnet.adbcompanion.domain.model.WifiTelemetry
import com.devnet.adbcompanion.presentation.theme.DevNetColors
import java.util.Locale

@Composable
fun SectionCard(
    title: String,
    badgeText: String? = null,
    badgeColor: Color = DevNetColors.Emerald,
    borderColor: Color = DevNetColors.BorderSubtle,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = DevNetColors.SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = DevNetColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (badgeText != null) {
                    StatusPill(text = badgeText, color = badgeColor)
                }
            }
            content()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AdbControlCard(
    adb: AdbStatus,
    wifi: WifiTelemetry,
    isResetting: Boolean,
    packageName: String,
    onCopyCommand: (String) -> Unit,
    onToggleWifiAdb: (Boolean) -> Unit,
    onResetAdb: () -> Unit,
    onKillAllAdb: () -> Unit,
    onEnableAdb: () -> Unit,
    onOpenDeveloperSettings: () -> Unit
) {
    val wirelessEndpoint = adb.wirelessEndpoint(wifi.ipAddress)
    val legacyEndpoint = adb.legacyEndpoint(wifi.ipAddress)
    val (badgeText, badgeColor) = when {
        wirelessEndpoint != null -> "WIRELESS DEBUG SẴN SÀNG" to DevNetColors.Emerald
        adb.adbWifiEnabled -> "WIRELESS DEBUG ON" to DevNetColors.Cyan
        legacyEndpoint != null -> "TCP ${adb.effectivePort} SẴN SÀNG" to DevNetColors.Emerald
        adb.adbEnabled -> "CHỈ USB" to DevNetColors.Amber
        else -> "ADB ĐÃ TẮT" to DevNetColors.Red
    }

    SectionCard(
        title = "ADB Connection Center",
        badgeText = badgeText,
        badgeColor = badgeColor
    ) {
        if (adb.adbWifiEnabled) {
            if (wirelessEndpoint != null) {
                AdbEndpointRow("Wireless Debugging", wirelessEndpoint, onCopyCommand)
            }
            Text(
                text = if (wirelessEndpoint == null) {
                    "Mở Tùy chọn nhà phát triển → Gỡ lỗi qua Wi-Fi → xem \"Địa chỉ IP và cổng\"."
                } else {
                    "Máy tính mới cần ghép đôi trước bằng mã hoặc QR trong Gỡ lỗi qua Wi-Fi."
                },
                color = DevNetColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        if (legacyEndpoint != null) {
            AdbEndpointRow("ADB TCP/IP qua USB", legacyEndpoint, onCopyCommand)
        }
        if (!adb.adbWifiEnabled && legacyEndpoint == null) {
            Text(
                text = if (wifi.ipAddress.isNullOrBlank()) "Chưa có IP Wi-Fi" else "Chưa có cổng kết nối ADB",
                color = DevNetColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        OutlinedButton(
            onClick = onOpenDeveloperSettings,
            border = BorderStroke(1.dp, DevNetColors.BorderSubtle),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Mở Tùy chọn nhà phát triển", color = DevNetColors.TextPrimary, fontSize = 13.sp)
        }

        if (!adb.hasWriteSecureSettings) {
            val grantCmd = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DevNetColors.AmberDim, RoundedCornerShape(12.dp))
                    .border(1.dp, DevNetColors.Amber.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { onCopyCommand(grantCmd) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Cấp quyền 1 lần để Bật/Ngắt ADB trực tiếp trên điện thoại",
                    color = DevNetColors.Amber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = grantCmd,
                    color = DevNetColors.TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Chạm vào khung này để copy lệnh chạy trên Mac",
                    color = DevNetColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevNetColors.SurfaceElevated, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gỡ lỗi qua Wi-Fi (Android 11+)",
                        color = DevNetColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Bật/tắt công tắc Wireless Debugging bằng 1 chạm",
                        color = DevNetColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = adb.adbWifiEnabled,
                    onCheckedChange = onToggleWifiAdb,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = DevNetColors.Emerald
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResetAdb,
                    enabled = !isResetting && (adb.adbEnabled || !adb.hasWriteSecureSettings),
                    border = BorderStroke(1.dp, DevNetColors.Amber.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isResetting) "Đang reset..." else "Ngắt & Reset ADB",
                        color = DevNetColors.Amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (adb.adbEnabled || !adb.hasWriteSecureSettings) {
                    Button(
                        onClick = onKillAllAdb,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DevNetColors.RedDim,
                            contentColor = DevNetColors.Red
                        ),
                        border = BorderStroke(1.dp, DevNetColors.Red.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ngắt kết nối All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onEnableAdb,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DevNetColors.EmeraldDim,
                            contentColor = DevNetColors.Emerald
                        ),
                        border = BorderStroke(1.dp, DevNetColors.Emerald.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bật lại ADB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdbEndpointRow(label: String, endpoint: String, onCopyCommand: (String) -> Unit) {
    val command = "adb connect $endpoint"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DevNetColors.Background, RoundedCornerShape(12.dp))
            .border(1.dp, DevNetColors.BorderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Copy lệnh kết nối") { onCopyCommand(command) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = DevNetColors.TextSecondary, fontSize = 12.sp)
        Text(
            text = command,
            color = DevNetColors.Cyan,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Text("Chạm để copy", color = DevNetColors.TextMuted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceHealthCard(
    adb: AdbStatus,
    wifi: WifiTelemetry,
    environment: EnvironmentDiagnostics,
    diagnostics: List<DiagnosticItem>,
    onDiagnosticAction: (DiagnosticAction) -> Unit,
    onOpenDeveloperSettings: () -> Unit
) {
    val blockerCount = diagnostics.count { it.severity == DiagnosticSeverity.BLOCKER }
    val warnCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    val (badgeText, badgeColor, borderColor) = when {
        blockerCount > 0 -> Triple(
            "$blockerCount LỖI CHẶN",
            DevNetColors.Red,
            DevNetColors.Red.copy(alpha = 0.7f)
        )
        warnCount > 0 -> Triple(
            "$warnCount CẢNH BÁO",
            DevNetColors.Amber,
            DevNetColors.Amber.copy(alpha = 0.6f)
        )
        else -> Triple(
            "MÔI TRƯỜNG SẠCH",
            DevNetColors.Emerald,
            DevNetColors.BorderSubtle
        )
    }

    SectionCard(
        title = "Chẩn đoán Gỡ lỗi Wi-Fi & Thiết bị",
        badgeText = badgeText,
        badgeColor = badgeColor,
        borderColor = borderColor
    ) {
        if (diagnostics.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DevNetColors.EmeraldDim, RoundedCornerShape(12.dp))
                    .border(1.dp, DevNetColors.Emerald.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Không phát hiện xung đột (Wi-Fi đơn, không VPN, Location ON, không DNS riêng). Sẵn sàng bật Gỡ lỗi qua Wi-Fi.",
                    color = DevNetColors.Emerald,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                diagnostics.forEach { item ->
                    val itemColor = if (item.severity == DiagnosticSeverity.BLOCKER) {
                        DevNetColors.Red
                    } else {
                        DevNetColors.Amber
                    }
                    val itemBg = if (item.severity == DiagnosticSeverity.BLOCKER) {
                        DevNetColors.RedDim
                    } else {
                        DevNetColors.AmberDim
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(itemBg, RoundedCornerShape(12.dp))
                            .border(1.dp, itemColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = itemColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.detail,
                            color = DevNetColors.TextPrimary,
                            fontSize = 12.sp
                        )
                        if (item.action != DiagnosticAction.NONE && !item.actionLabel.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { onDiagnosticAction(item.action) },
                                border = BorderStroke(1.dp, itemColor.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(item.actionLabel, color = itemColor, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlagBadge(
                label = "USB Debug",
                isOn = adb.adbEnabled
            )
            FlagBadge(
                label = "ADB TCP/IP",
                isOn = adb.isTcpPortListening,
                customValue = if (adb.isTcpPortListening) adb.effectivePort?.toString() else "CLOSED"
            )
            FlagBadge(
                label = "Wi-Fi",
                isOn = !wifi.hasDualWifiConflict && wifi.isConnected,
                customValue = wifi.activeWlanInterfaces.ifEmpty { listOf("none") }.joinToString("+")
            )
            FlagBadge(
                label = "VPN",
                isOn = !environment.vpnActive,
                customValue = if (environment.vpnActive) "ACTIVE" else "OFF"
            )
            FlagBadge(
                label = "Location",
                isOn = environment.locationEnabled
            )
            FlagBadge(
                label = "Private DNS",
                isOn = !environment.isCustomPrivateDns,
                customValue = when (environment.privateDnsMode) {
                    "hostname" -> "CUSTOM"
                    "off" -> "OFF"
                    else -> "AUTO"
                }
            )
            if (adb.adbInputEnabled != null) {
                FlagBadge(
                    label = "scrcpy Click",
                    isOn = adb.adbInputEnabled == true
                )
            }
            if (adb.adbInstallEnabled != null) {
                FlagBadge(
                    label = "Install via USB",
                    isOn = adb.adbInstallEnabled == true
                )
            }
        }

        if (adb.adbInputEnabled == false || adb.adbInstallEnabled == false) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DevNetColors.AmberDim, RoundedCornerShape(10.dp))
                    .clickable { onOpenDeveloperSettings() }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Một số quyền debug Xiaomi đang tắt — chạm để mở Dev Options",
                    color = DevNetColors.Amber,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FlagBadge(
    label: String,
    isOn: Boolean,
    customValue: String? = null
) {
    val color = if (isOn) DevNetColors.Emerald else DevNetColors.TextMuted
    val valueText = customValue ?: if (isOn) "ON" else "OFF"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(DevNetColors.SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$label: ",
            color = DevNetColors.TextSecondary,
            fontSize = 11.sp
        )
        Text(
            text = valueText,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun LanMonitorCard(
    wifi: WifiTelemetry,
    lan: LanMonitorState,
    recommendation: ScrcpyRecommendation,
    isRecordingDebugSession: Boolean,
    onTargetHostChange: (String) -> Unit,
    onUseGateway: () -> Unit,
    onProbeOnce: () -> Unit,
    onToggleMonitor: () -> Unit,
    onCopyScrcpy: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val tierColor = when (recommendation.tier) {
        QualityTier.EXCELLENT -> DevNetColors.Emerald
        QualityTier.GOOD -> DevNetColors.Cyan
        QualityTier.WARN -> DevNetColors.Amber
        QualityTier.POOR -> DevNetColors.Red
    }

    SectionCard(
        title = "LAN Ping & Tối ưu scrcpy",
        badgeText = if (lan.isMonitoring) "ĐANG ĐO 1s" else "${wifi.bandLabel} • ${wifi.linkSpeedMbps} Mbps",
        badgeColor = if (lan.isMonitoring) DevNetColors.Emerald else DevNetColors.Cyan
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = lan.targetHost,
                onValueChange = onTargetHostChange,
                label = { Text("IP máy Mac / Gateway", fontSize = 11.sp) },
                placeholder = { Text("VD: 192.168.8.159", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onProbeOnce()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DevNetColors.Cyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = DevNetColors.TextPrimary,
                    unfocusedTextColor = DevNetColors.TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            if (!wifi.gatewayIp.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onUseGateway,
                    border = BorderStroke(1.dp, DevNetColors.BorderSubtle),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Gateway", color = DevNetColors.TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    onProbeOnce()
                },
                border = BorderStroke(1.dp, DevNetColors.Cyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Đo ngay", color = DevNetColors.Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                enabled = !isRecordingDebugSession,
                onClick = {
                    focusManager.clearFocus()
                    onToggleMonitor()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (lan.isMonitoring) DevNetColors.RedDim else DevNetColors.CyanDim,
                    contentColor = if (lan.isMonitoring) DevNetColors.Red else DevNetColors.Cyan
                ),
                border = BorderStroke(
                    1.dp,
                    if (lan.isMonitoring) DevNetColors.Red.copy(alpha = 0.5f) else DevNetColors.Cyan.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        isRecordingDebugSession -> "Đang đo cho phiên"
                        lan.isMonitoring -> "Dừng đo"
                        else -> "Theo dõi liên tục"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = "Latency",
                value = formatMs(lan.lastLatencyMs),
                accent = when {
                    lan.lastLatencyMs == null -> DevNetColors.TextSecondary
                    lan.lastLatencyMs <= 25.0 -> DevNetColors.Emerald
                    lan.lastLatencyMs <= 60.0 -> DevNetColors.Amber
                    else -> DevNetColors.Red
                },
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Jitter",
                value = formatMs(lan.jitterMs),
                accent = DevNetColors.Cyan,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Loss",
                value = "${lan.packetLossPercent}%",
                accent = if (lan.packetLossPercent > 0) DevNetColors.Red else DevNetColors.Emerald,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "RSSI",
                value = if (wifi.isConnected) "${wifi.rssiDbm} dBm" else "--",
                accent = DevNetColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        LatencyChart(samples = lan.samples)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tierColor.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
                .border(1.dp, tierColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable { onCopyScrcpy(recommendation.command) }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gợi ý scrcpy: ${recommendation.title}",
                    color = tierColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Chạm để copy",
                    color = DevNetColors.TextSecondary,
                    fontSize = 10.5.sp
                )
            }
            Text(
                text = recommendation.command,
                color = DevNetColors.TextPrimary,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = recommendation.reason,
                color = DevNetColors.TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DevNetColors.SurfaceElevated, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = DevNetColors.TextMuted, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
fun ProxyWatchdogCard(
    proxy: ProxyStatus,
    onOpenWifiSettings: () -> Unit
) {
    val (badge, color, detail) = when {
        !proxy.isConfigured -> Triple(
            "DIRECT (NO PROXY)",
            DevNetColors.Emerald,
            "Wi-Fi không bị dính HTTP Proxy. App kết nối mạng trực tiếp bình thường."
        )
        proxy.isReachable == true -> Triple(
            "PROXY ALIVE",
            DevNetColors.Cyan,
            "Đang trỏ Proxy tới ${proxy.displayAddress} và cổng này đang phản hồi (Charles / Proxyman đang bật)."
        )
        else -> Triple(
            "PROXY ĐÃ CHẾT!",
            DevNetColors.Red,
            "Wi-Fi vẫn cấu hình Proxy ${proxy.displayAddress} nhưng máy tính không mở cổng này -> App test sẽ bị mất mạng!"
        )
    }

    SectionCard(
        title = "Proxy Watchdog (Charles / Proxyman)",
        badgeText = badge,
        badgeColor = color,
        borderColor = if (proxy.isDeadProxyWarning) DevNetColors.Red.copy(alpha = 0.7f) else DevNetColors.BorderSubtle
    ) {
        Text(
            text = detail,
            color = if (proxy.isDeadProxyWarning) DevNetColors.TextPrimary else DevNetColors.TextSecondary,
            fontSize = 12.sp
        )
        if (proxy.isConfigured) {
            OutlinedButton(
                onClick = onOpenWifiSettings,
                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Mở Cài đặt Wi-Fi để chỉnh/tắt Proxy", color = color, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PrivateDnsCard(
    environment: EnvironmentDiagnostics,
    savedHost: String,
    hasWriteSecureSettings: Boolean,
    onHostChange: (String) -> Unit,
    onTogglePrivateDns: (Boolean) -> Unit
) {
    val isCustomActive = environment.isCustomPrivateDns
    val (badgeText, badgeColor) = when {
        isCustomActive -> "ĐANG BẬT (CHẶN QC)" to DevNetColors.Emerald
        environment.privateDnsMode.equals("off", ignoreCase = true) -> "ĐÃ TẮT" to DevNetColors.TextMuted
        else -> "TỰ ĐỘNG" to DevNetColors.Cyan
    }

    SectionCard(
        title = "Private DNS (Chặn quảng cáo)",
        badgeText = badgeText,
        badgeColor = badgeColor
    ) {
        ToggleRow(
            title = "Bật Private DNS chặn quảng cáo",
            subtitle = if (hasWriteSecureSettings) {
                "Gạt 1 chạm để chuyển nhanh giữa DNS riêng và Tắt (khi cần debug Wi-Fi)."
            } else {
                "Cần cấp quyền WRITE_SECURE_SETTINGS để gạt trực tiếp."
            },
            checked = isCustomActive,
            onCheckedChange = onTogglePrivateDns
        )

        val focusManager = LocalFocusManager.current
        OutlinedTextField(
            value = savedHost,
            onValueChange = onHostChange,
            label = { Text("Hostname Private DNS", fontSize = 11.sp) },
            placeholder = { Text("dns.adguard-dns.com", fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (isCustomActive) {
                        onTogglePrivateDns(true)
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DevNetColors.Emerald,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = DevNetColors.TextPrimary,
                unfocusedTextColor = DevNetColors.TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BatteryChargeCard(
    battery: BatteryTelemetry,
    onOpenChargeSettings: () -> Unit,
    onOpenBatteryOverview: () -> Unit
) {
    val badgeText = if (battery.isCharging) {
        "${battery.levelPercent}% • Sạc ${battery.pluggedSource}"
    } else {
        "${battery.levelPercent}% • Dùng pin"
    }
    val badgeColor = when {
        battery.isCharging -> DevNetColors.Emerald
        battery.levelPercent <= 20 -> DevNetColors.Red
        else -> DevNetColors.Cyan
    }

    SectionCard(
        title = "Pin & Bảo vệ sạc",
        badgeText = badgeText,
        badgeColor = badgeColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = "Pin",
                value = "${battery.levelPercent}%",
                accent = badgeColor,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Nhiệt độ",
                value = String.format(Locale.US, "%.1f°C", battery.temperatureC),
                accent = if (battery.temperatureC >= 40.0) DevNetColors.Red else DevNetColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Điện áp",
                value = if (battery.voltageMv > 0) String.format(Locale.US, "%.2fV", battery.voltageMv / 1000.0) else "--",
                accent = DevNetColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Dòng sạc",
                value = battery.currentMa?.let { "${it}mA" } ?: "--",
                accent = DevNetColors.Cyan,
                modifier = Modifier.weight(1f)
            )
        }

        if (battery.nightCurrentFlag != null) {
            Text(
                text = "Xiaomi khóa quyền mở thẳng trang \"Bảo vệ pin\" (exported=false); bấm nút dưới để nhảy tới màn Pin chính (chạm 1 lần vào Bảo vệ pin là tới).",
                color = DevNetColors.TextSecondary,
                fontSize = 11.5.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenBatteryOverview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DevNetColors.EmeraldDim,
                    contentColor = DevNetColors.Emerald
                ),
                border = BorderStroke(1.dp, DevNetColors.Emerald.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Mở Pin (→ Bảo vệ pin)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onOpenChargeSettings,
                border = BorderStroke(1.dp, DevNetColors.BorderSubtle),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Tính năng sạc", color = DevNetColors.TextPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AutomationCard(
    persistentNotificationEnabled: Boolean,
    floatingHudEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onToggleNotification: (Boolean) -> Unit,
    onToggleFloatingHud: (Boolean) -> Unit
) {
    SectionCard(
        title = "Tiện ích chạy nền (Zero-Friction)",
        badgeText = "Ít thao tác",
        badgeColor = DevNetColors.Cyan
    ) {
        ToggleRow(
            title = "Ghim thông báo trạng thái",
            subtitle = "Luôn hiện IP:Port, Ping LAN kèm nút Copy & Ngắt ADB trên thanh thông báo.",
            checked = persistentNotificationEnabled,
            onCheckedChange = onToggleNotification
        )

        ToggleRow(
            title = "Mini HUD nổi trên góc màn hình",
            subtitle = if (hasOverlayPermission) {
                "Hiện thanh thông số nhỏ (5GHz | ms | ADB | Proxy) khi đang debug app khác."
            } else {
                "Cần cấp quyền Hiển thị trên ứng dụng khác khi gạt bật."
            },
            checked = floatingHudEnabled,
            onCheckedChange = onToggleFloatingHud
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevNetColors.SurfaceElevated, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "Quick Settings Tiles: \"ADB Wi-Fi\" & \"AdGuard DNS\"",
                color = DevNetColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Kéo Control Center xuống -> bấm Sửa -> thêm 2 ô \"ADB Wi-Fi\" và \"AdGuard DNS\" để bật/tắt nhanh từ mọi màn hình mà không cần mở app.",
                color = DevNetColors.TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DevNetColors.SurfaceElevated, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = DevNetColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = DevNetColors.TextSecondary,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = DevNetColors.Emerald
            )
        )
    }
}

private fun formatMs(ms: Double?): String {
    return if (ms == null) "--" else String.format(Locale.US, "%.0fms", ms)
}

package com.devnet.adbcompanion.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devnet.adbcompanion.presentation.theme.DevNetColors

@Composable
fun DebugSessionCard(
    isRecording: Boolean,
    report: String?,
    onToggleRecording: () -> Unit,
    onMarkIssue: () -> Unit,
    onCopyReport: (String) -> Unit
) {
    var previewOpen by remember { mutableStateOf(false) }

    SectionCard(
        title = "Ghi phiên debug",
        badgeText = if (isRecording) "ĐANG GHI" else null,
        badgeColor = DevNetColors.Emerald
    ) {
        Text(
            text = if (isRecording) {
                "Đánh dấu khi thấy giật hình hoặc mất kết nối."
            } else {
                "Ghi ping, tín hiệu Wi-Fi và thay đổi VPN, ADB trong lúc tái hiện lỗi."
            },
            color = DevNetColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onToggleRecording, modifier = Modifier.weight(1f)) {
                Text(if (isRecording) "Dừng ghi" else "Bắt đầu ghi")
            }
            if (isRecording) {
                OutlinedButton(onClick = onMarkIssue, modifier = Modifier.weight(1f)) {
                    Text("Đánh dấu lỗi")
                }
            } else if (report != null) {
                OutlinedButton(onClick = { previewOpen = true }, modifier = Modifier.weight(1f)) {
                    Text("Xem báo cáo")
                }
            }
        }
    }

    if (previewOpen && report != null) {
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            title = { Text("Báo cáo phiên debug") },
            text = {
                Text(
                    text = report,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 4.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCopyReport(report)
                    previewOpen = false
                }) {
                    Text("Copy báo cáo")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewOpen = false }) { Text("Đóng") }
            }
        )
    }
}

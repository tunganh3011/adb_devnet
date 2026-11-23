package com.example.myapplication

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.presentation.camera.CapturedDocumentResult
import com.example.myapplication.presentation.camera.DocumentCameraScreen
import com.example.myapplication.presentation.camera.DocumentCaptureConfig
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DocumentCaptureDemoHost()
            }
        }
    }
}

@Composable
private fun DocumentCaptureDemoHost() {
    var isCameraOpen by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<CapturedDocumentResult?>(null) }

    if (isCameraOpen) {
        BackHandler {
            isCameraOpen = false
        }
        DocumentCameraScreen(
            config = DocumentCaptureConfig(),
            onBack = { isCameraOpen = false },
            onDocumentConfirmed = { result ->
                lastResult?.file?.delete()
                lastResult = result
                isCameraOpen = false
            }
        )
        return
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Chụp giấy tờ khung dọc (Crop + Viền mờ)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Mở camera dọc với khung trắng lớn chuẩn A4. Vùng ngoài khung được làm mờ & tối nhẹ, vùng trong khung giữ nguyên độ nét và tự động crop khớp khung sau khi chụp.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { isCameraOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7921))
            ) {
                Text("Mở Camera Chụp Giấy Tờ", fontWeight = FontWeight.SemiBold)
            }

            lastResult?.let { result ->
                val resultBitmap = remember(result.file.absolutePath) {
                    BitmapFactory.decodeFile(result.file.absolutePath)?.asImageBitmap()
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kết quả ảnh sau khi crop",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Kích thước: ${result.width} x ${result.height} px • Dung lượng: ${result.fileSizeBytes / 1024} KB\nFile: ${result.file.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (resultBitmap != null) {
                            Image(
                                bitmap = resultBitmap,
                                contentDescription = "Ảnh đã crop",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

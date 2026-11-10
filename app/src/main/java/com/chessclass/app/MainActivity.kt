package com.chessclass.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var engineMessage by remember { mutableStateOf("Henüz test edilmedi") }
            var engineOk by remember { mutableStateOf<Boolean?>(null) }
            var overlayMessage by remember { mutableStateOf("Henüz etkinleştirilmedi") }
            var overlayOk by remember { mutableStateOf<Boolean?>(null) }
            var captureMessage by remember { mutableStateOf("Ekran yakalama henüz başlatılmadı") }
            var captureOk by remember { mutableStateOf<Boolean?>(null) }
            var isAnalyzing by remember { mutableStateOf(false) }

            val captureLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val serviceIntent = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                    else startService(serviceIntent)
                    captureOk = true
                    captureMessage = "Ekran yakalama izni verildi ve yakalama başlatıldı"
                } else {
                    captureOk = false
                    captureMessage = "İzin verilmedi; ekran yakalama başlamadı"
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE6B85C))) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF101B2D)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("CHESS CLASS", color = Color(0xFFE6B85C), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(12.dp))
                    Text("Ana Panel", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))

                    Button(
                        enabled = !isAnalyzing,
                        onClick = {
                            isAnalyzing = true
                            engineOk = null
                            engineMessage = "Test ediliyor..."
                            lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { StockfishEngine(this@MainActivity).analyzeStartPosition() }
                                        .getOrElse { "Hata: ${it.message}" }
                                }
                                engineMessage = result
                                engineOk = result.startsWith("bestmove")
                                isAnalyzing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isAnalyzing) "Stockfish test ediliyor..." else "Stockfish'i Test Et") }
                    StatusLine(engineOk, engineMessage)

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            captureLauncher.launch(manager.createScreenCaptureIntent())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ekranı Algılamayı Başlat") }
                    StatusLine(captureOk, captureMessage)
                    Text("Android ekran paylaşımı izni soracak. Onay verirsen ekran yakalama bildirimi görünür. İstediğinde Android bildirimlerinden CHESS CLASS'ı durdurabilirsin.",
                        color = Color.LightGray, fontSize = 12.sp)

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                startActivity(intent)
                                overlayOk = false
                                overlayMessage = "İzin ekranından izin verip tekrar bas"
                            } else {
                                startService(Intent(this@MainActivity, OverlayService::class.java))
                                overlayOk = true
                                overlayMessage = "Yüzen yardımcı başlatıldı"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Yüzen Yardımcıyı Etkinleştir") }
                    StatusLine(overlayOk, overlayMessage)

                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { startService(Intent(this@MainActivity, OverlayService::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ana Paneli Yenile") }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean?, message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok == true) "✓" else if (ok == false) "✕" else "•",
            color = if (ok == true) Color(0xFF70D69A) else if (ok == false) Color(0xFFFF7777) else Color.LightGray,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(message, color = Color(0xFFFFD166), fontSize = 12.sp)
    }
}

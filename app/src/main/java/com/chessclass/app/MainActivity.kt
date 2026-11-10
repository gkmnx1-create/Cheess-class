package com.chessclass.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            var isAnalyzing by remember { mutableStateOf(false) }

            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE6B85C))) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF101B2D)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("CHESS CLASS", color = Color(0xFFE6B85C), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(12.dp))
                    Text("Ana Panel", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(32.dp))

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (engineOk == true) "✓" else if (engineOk == false) "✕" else "•",
                            color = if (engineOk == true) Color(0xFF70D69A) else if (engineOk == false) Color(0xFFFF7777) else Color.LightGray,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(engineMessage, color = Color(0xFFFFD166), fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(28.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (overlayOk == true) "✓" else if (overlayOk == false) "!" else "•",
                            color = if (overlayOk == true) Color(0xFF70D69A) else if (overlayOk == false) Color(0xFFFFD166) else Color.LightGray,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(overlayMessage, color = Color(0xFFFFD166), fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(28.dp))
                    OutlinedButton(
                        onClick = { startService(Intent(this@MainActivity, OverlayService::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ana Paneli Yenile") }
                }
            }
        }
    }
}

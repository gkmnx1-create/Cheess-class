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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE6B85C))) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF101B2D)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("CHESS CLASS", color = Color(0xFFE6B85C), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(12.dp))
                    Text("Diğer uygulamaların üzerinde açılan satranç yardımcısı", color = Color.White)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"))
                            startActivity(intent)
                        } else {
                            startService(Intent(this@MainActivity, OverlayService::class.java))
                        }
                    }) { Text("Yüzen düğmeyi etkinleştir") }
                    Spacer(Modifier.height(12.dp))
                    Text("İzin ekranında CHESS CLASS için 'Diğer uygulamaların üzerinde göster' seçeneğini aç. Sonra uygulamaya dönüp düğmeye tekrar bas.", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Text("Bu sürüm yalnızca yüzen düğme ve paneli içerir. Tahta tanıma ve Stockfish henüz eklenmedi.", color = Color(0xFFFFD166), fontSize = 12.sp)
                }
            }
        }
    }
}
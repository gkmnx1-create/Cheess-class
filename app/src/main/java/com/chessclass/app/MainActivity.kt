package com.chessclass.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
            var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var imageMessage by remember { mutableStateOf("Henüz tahta fotoğrafı seçilmedi.") }
            var engineMessage by remember { mutableStateOf("Stockfish analizi henüz çalıştırılmadı.") }
            var isAnalyzing by remember { mutableStateOf(false) }
            val imagePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    lifecycleScope.launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.use { input ->
                                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                                BitmapFactory.decodeStream(input, null, options)
                            }
                        }
                        selectedBitmap = bitmap
                        imageMessage = if (bitmap != null) {
                            "Fotoğraf yüklendi. Tahta tanıma özelliği sonraki geliştirme aşamasında."
                        } else {
                            "Fotoğraf açılamadı. Başka bir görüntü seç."
                        }
                    }
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE6B85C))) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF101B2D))
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CHESS CLASS", color = Color(0xFFE6B85C), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(10.dp))
                    Text("Satranç tahtası fotoğrafı", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tahtanın tamamı görünecek şekilde bir ekran görüntüsü veya fotoğraf seç.", color = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { imagePicker.launch("image/*") }) {
                        Text("Galeriden tahta fotoğrafı seç")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(imageMessage, color = Color(0xFFFFD166), fontSize = 13.sp)
                    selectedBitmap?.let { bitmap ->
                        Spacer(Modifier.height(12.dp))
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Seçilen satranç tahtası",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        enabled = !isAnalyzing,
                        onClick = {
                            isAnalyzing = true
                            engineMessage = "Stockfish başlangıç konumunu analiz ediyor..."
                            lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { StockfishEngine(this@MainActivity).analyzeStartPosition() }
                                        .getOrElse { "Motor başlatılamadı: ${it.message}" }
                                }
                                engineMessage = result
                                isAnalyzing = false
                            }
                        }
                    ) { Text(if (isAnalyzing) "Analiz ediliyor..." else "Stockfish'i dene") }
                    Text(engineMessage, color = Color(0xFFFFD166), fontSize = 12.sp)
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider(color = Color.DarkGray)
                    Spacer(Modifier.height(18.dp))
                    Text("Yüzen yardımcı", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"))
                            startActivity(intent)
                        } else {
                            startService(Intent(this@MainActivity, OverlayService::class.java))
                        }
                    }) { Text("Yüzen düğmeyi etkinleştir") }
                    Spacer(Modifier.height(10.dp))
                    Text("İzin ekranında CHESS CLASS için 'Diğer uygulamaların üzerinde göster' seçeneğini aç. Sonra uygulamaya dönüp düğmeye tekrar bas.", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("Not: Bu sürüm fotoğraf seçip görüntüleyebilir. Stockfish motoru başlangıç konumunda test ediliyor. Seçilen fotoğrafın taşlarını tanıma ve fotoğrafı motora aktarma henüz eklenmedi.", color = Color(0xFFFFD166), fontSize = 12.sp)
                }
            }
        }
    }
}
package com.chessclass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
        setContent { ChessClassScreen() }
    }
}

private val Navy = Color(0xFF101B2D)
private val Cream = Color(0xFFF0D9B5)
private val Brown = Color(0xFFB58863)
private val Gold = Color(0xFFE6B85C)

@Composable
fun ChessClassScreen() {
    val pieces = arrayOf(
        arrayOf("♜","♞","♝","♛","♚","♝","♞","♜"),
        Array(8) { "♟" },
        Array(8) { "" }, Array(8) { "" }, Array(8) { "" }, Array(8) { "" },
        Array(8) { "♙" },
        arrayOf("♖","♘","♗","♕","♔","♗","♘","♖")
    )
    var status by remember { mutableStateOf("Beyazın sırası") }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, background = Navy, surface = Color(0xFF1B2A40),
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(
            Modifier.fillMaxSize().background(Navy).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("CHESS CLASS", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Satranç • Öğren • Geliş", color = Color.LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RAKİP: YAPAY ZEKÂ", color = Color.White, fontWeight = FontWeight.Bold)
                Text("SEVİYE 1", color = Gold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(status, color = Gold)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().aspectRatio(1f).border(3.dp, Gold)) {
                for (r in 0..7) {
                    Row(Modifier.weight(1f)) {
                        for (c in 0..7) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if ((r + c) % 2 == 0) Cream else Brown),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(pieces[r][c], fontSize = 30.sp,
                                    color = if (r < 2) Color(0xFF202020) else Color.White)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { status = "Yeni oyun hazırlanıyor" }, modifier = Modifier.weight(1f)) {
                    Text("Yeni Oyun")
                }
                OutlinedButton(onClick = { status = "Hamle analizi motor entegrasyonuyla eklenecek" },
                    modifier = Modifier.weight(1f)) { Text("Hamle Analizi") }
            }
            Spacer(Modifier.weight(1f))
            Text("Çevrimdışı satranç uygulaması • İlk taslak", color = Color.LightGray, fontSize = 11.sp)
        }
    }
}

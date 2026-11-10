package com.chessclass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

private fun isWhite(piece: String) = piece in listOf("♙","♖","♘","♗","♕","♔")

private fun initialBoard(): List<List<String>> = listOf(
    listOf("♜","♞","♝","♛","♚","♝","♞","♜"),
    List(8) { "♟" }, List(8) { "" }, List(8) { "" },
    List(8) { "" }, List(8) { "" }, List(8) { "♙" },
    listOf("♖","♘","♗","♕","♔","♗","♘","♖")
)

private fun legalShape(board: List<List<String>>, fromR: Int, fromC: Int, toR: Int, toC: Int): Boolean {
    val piece = board[fromR][fromC]
    if (piece.isEmpty() || (fromR == toR && fromC == toC)) return false
    val target = board[toR][toC]
    if (target.isNotEmpty() && isWhite(target) == isWhite(piece)) return false
    val dr = toR - fromR
    val dc = toC - fromC
    val ar = kotlin.math.abs(dr)
    val ac = kotlin.math.abs(dc)
    fun clearPath(): Boolean {
        val sr = dr.compareTo(0)
        val sc = dc.compareTo(0)
        var r = fromR + sr
        var c = fromC + sc
        while (r != toR || c != toC) {
            if (board[r][c].isNotEmpty()) return false
            r += sr
            c += sc
        }
        return true
    }
    return when (piece) {
        "♙", "♟" -> {
            val direction = if (isWhite(piece)) -1 else 1
            val start = if (isWhite(piece)) 6 else 1
            if (dc == 0 && target.isEmpty() && dr == direction) true
            else if (dc == 0 && target.isEmpty() && fromR == start && dr == 2 * direction &&
                board[fromR + direction][fromC].isEmpty()) true
            else ac == 1 && dr == direction && target.isNotEmpty()
        }
        "♘", "♞" -> (ar == 2 && ac == 1) || (ar == 1 && ac == 2)
        "♖", "♜" -> (dr == 0 || dc == 0) && clearPath()
        "♗", "♝" -> ar == ac && clearPath()
        "♕", "♛" -> ((dr == 0 || dc == 0) || ar == ac) && clearPath()
        "♔", "♚" -> ar <= 1 && ac <= 1
        else -> false
    }
}

@Composable
fun ChessClassScreen() {
    var board by remember { mutableStateOf(initialBoard()) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var whiteTurn by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Beyazın sırası") }

    fun tapSquare(r: Int, c: Int) {
        val current = selected
        if (current == null) {
            val piece = board[r][c]
            if (piece.isNotEmpty() && isWhite(piece) == whiteTurn) {
                selected = r to c
                status = "Hedef kareyi seç"
            } else {
                status = if (whiteTurn) "Beyaz bir taş seç" else "Siyah bir taş seç"
            }
            return
        }
        val (fr, fc) = current
        if (fr == r && fc == c) {
            selected = null
            status = if (whiteTurn) "Beyazın sırası" else "Siyahın sırası"
            return
        }
        if (board[r][c].isNotEmpty() && isWhite(board[r][c]) == whiteTurn) {
            selected = r to c
            status = "Taş seçildi; hedef kareyi seç"
            return
        }
        if (legalShape(board, fr, fc, r, c)) {
            val next = board.map { it.toMutableList() }
            next[r][c] = next[fr][fc]
            next[fr][fc] = ""
            board = next.map { it.toList() }
            whiteTurn = !whiteTurn
            status = if (whiteTurn) "Beyazın sırası" else "Siyahın sırası"
        } else {
            status = "Geçersiz hamle; tekrar dene"
        }
        selected = null
    }

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
                Text("OYUNCU: BEYAZ", color = Color.White, fontWeight = FontWeight.Bold)
                Text("İKİ KİŞİLİK DENEME", color = Gold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(status, color = Gold)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().aspectRatio(1f).border(3.dp, Gold)) {
                for (r in 0..7) {
                    Row(Modifier.weight(1f)) {
                        for (c in 0..7) {
                            val active = selected == (r to c)
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if (active) Gold else if ((r + c) % 2 == 0) Cream else Brown)
                                    .clickable { tapSquare(r, c) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(board[r][c], fontSize = 30.sp,
                                    color = if (r < 2) Color(0xFF202020) else Color.White)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    board = initialBoard()
                    selected = null
                    whiteTurn = true
                    status = "Beyazın sırası"
                }, modifier = Modifier.weight(1f)) { Text("Yeni Oyun") }
                OutlinedButton(onClick = { status = "Tam analiz ve yapay zekâ sonraki sürümde" },
                    modifier = Modifier.weight(1f)) { Text("Hamle Analizi") }
            }
            Spacer(Modifier.weight(1f))
            Text("Taşa, sonra hedef kareye dokun • İlk hareketli sürüm", color = Color.LightGray, fontSize = 11.sp)
        }
    }
}

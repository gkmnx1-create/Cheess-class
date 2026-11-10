package com.chessclass.app

import android.graphics.Bitmap

object BoardVision {
    data class ScanResult(val summary: String, val fen: String?, val recognized: Int)

    private val fenPieces = mapOf(
        "sK" to 'k', "sV" to 'q', "sF" to 'b', "sR" to 'r', "sN" to 'n', "sP" to 'p',
        "bK" to 'K', "bV" to 'Q', "bF" to 'B', "bR" to 'R', "bN" to 'N', "bP" to 'P'
    )

    fun inspect(bitmap: Bitmap): String = scan(bitmap).summary

    fun scan(bitmap: Bitmap): ScanResult {
        if (bitmap.width < 300 || bitmap.height < 500)
            return ScanResult("Ekran boyutu uygun değil", null, 0)

        // Board bounds for the portrait chess-game layout currently being tested.
        val left = (bitmap.width * 0.012).toInt()
        val right = (bitmap.width * 0.988).toInt()
        val top = (bitmap.height * 0.268).toInt()
        val bottom = (bitmap.height * 0.714).toInt()
        val boardWidth = right - left
        val boardHeight = bottom - top
        if (boardWidth <= 0 || boardHeight <= 0)
            return ScanResult("Tahta alanı bulunamadı", null, 0)

        var recognized = 0
        val rows = ArrayList<String>()
        val names = linkedMapOf<String, Int>()
        for (rank in 0 until 8) {
            val row = StringBuilder()
            var empty = 0
            for (file in 0 until 8) {
                val x0 = left + file * boardWidth / 8
                val x1 = left + (file + 1) * boardWidth / 8
                val y0 = top + rank * boardHeight / 8
                val y1 = top + (rank + 1) * boardHeight / 8
                val type = PieceTemplateClassifier.classify(bitmap, x0, y0, x1, y1)
                val piece = type?.let { fenPieces[it] }
                if (piece != null) {
                    if (empty > 0) { row.append(empty); empty = 0 }
                    row.append(piece)
                    recognized++
                    names[type] = (names[type] ?: 0) + 1
                } else {
                    empty++
                }
            }
            if (empty > 0) row.append(empty)
            rows.add(row.toString())
        }

        val detail = if (names.isEmpty()) "eşleşme yok" else names.entries.joinToString(", ") {
            "${it.key}: ${it.value}"
        }
        val fen = if (recognized >= 20) rows.joinToString("/") + " w - - 0 1" else null
        val summary = "Taş eşleşmesi: $recognized/64 • $detail" +
            if (fen == null) " • Analiz için daha fazla taş tanınmalı" else " • Tahta hazır"
        return ScanResult(summary, fen, recognized)
    }
}

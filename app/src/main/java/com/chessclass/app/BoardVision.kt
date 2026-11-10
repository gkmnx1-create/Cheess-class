package com.chessclass.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object BoardVision {
    data class Result(val occupied: Int, val whiteLike: Int, val blackLike: Int)

    fun inspect(bitmap: Bitmap): String {
        if (bitmap.width < 300 || bitmap.height < 500) return "Ekran boyutu uygun değil"
        val left = (bitmap.width * 0.012).toInt()
        val right = (bitmap.width * 0.988).toInt()
        val top = (bitmap.height * 0.268).toInt()
        val bottom = (bitmap.height * 0.714).toInt()
        val boardWidth = right - left
        val boardHeight = bottom - top
        if (boardWidth <= 0 || boardHeight <= 0) return "Tahta alanı bulunamadı"

        val result = detect(bitmap, left, top, boardWidth, boardHeight)
        val names = mapOf(
            "sK" to "Siyah şah", "sV" to "Siyah vezir", "sF" to "Siyah fil",
            "sR" to "Siyah kale", "sN" to "Siyah at", "sP" to "Siyah piyon",
            "bK" to "Beyaz şah", "bV" to "Beyaz vezir", "bF" to "Beyaz fil",
            "bR" to "Beyaz kale", "bN" to "Beyaz at", "bP" to "Beyaz piyon"
        )
        val counts = linkedMapOf<String, Int>()
        var recognized = 0
        for (rank in 0 until 8) for (file in 0 until 8) {
            val x0 = left + file * boardWidth / 8
            val x1 = left + (file + 1) * boardWidth / 8
            val y0 = top + rank * boardHeight / 8
            val y1 = top + (rank + 1) * boardHeight / 8
            val type = PieceTemplateClassifier.classify(bitmap, x0, y0, x1, y1)
            if (type != null) {
                recognized++
                val label = names[type] ?: type
                counts[label] = (counts[label] ?: 0) + 1
            }
        }
        val detail = if (counts.isEmpty()) "eşleşme yok" else
            counts.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        return "Tahta: ${result.occupied}/64 dolu • Taş eşleşmesi: $recognized • $detail"
    }

    private fun detect(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Result {
        var occupied = 0
        var white = 0
        var black = 0
        for (rank in 0 until 8) for (file in 0 until 8) {
            val x0 = left + file * width / 8
            val x1 = left + (file + 1) * width / 8
            val y0 = top + rank * height / 8
            val y1 = top + (rank + 1) * height / 8
            val squareW = x1 - x0
            val squareH = y1 - y0
            val samples = ArrayList<Int>()
            for (sy in 15..85 step 10) for (sx in 15..85 step 10) {
                if (sx in 30..70 && sy in 30..70) continue
                samples.add(luma(bitmap.getPixel(x0 + squareW * sx / 100, y0 + squareH * sy / 100)))
            }
            if (samples.isEmpty()) continue
            samples.sort()
            val background = samples[samples.size / 2]
            var contrast = 0
            var bright = 0
            var dark = 0
            for (sy in 30..70 step 5) for (sx in 30..70 step 5) {
                val value = luma(bitmap.getPixel(x0 + squareW * sx / 100, y0 + squareH * sy / 100))
                if (abs(value - background) > 58) {
                    contrast++
                    if (value > background) bright++ else dark++
                }
            }
            if (contrast >= 5) {
                occupied++
                if (bright >= dark) white++ else black++
            }
        }
        return Result(occupied, white, black)
    }

    private fun luma(color: Int): Int =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
}
package com.chessclass.app

import android.graphics.Bitmap
import com.chessclass.app.vision.BoardDetector
import com.chessclass.app.vision.FenBuilder
import com.chessclass.app.vision.MoveAnalyzer
import com.chessclass.app.vision.PieceClassifier
import com.chessclass.app.vision.Side

object BoardVision {
    data class ScanResult(val summary: String, val fen: String?, val recognized: Int)
    private val classifier = PieceClassifier()
    private var lastGrid: Array<CharArray>? = null
    private var sideToMove = Side.WHITE

    @Synchronized fun reset() {
        classifier.reset()
        lastGrid = null
        sideToMove = Side.WHITE
    }

    fun inspect(bitmap: Bitmap): String = scan(bitmap).summary

    @Synchronized fun scan(bitmap: Bitmap): ScanResult {
        if (bitmap.width < 300 || bitmap.height < 300)
            return ScanResult("Ekran boyutu uygun değil", null, 0)

        val board = BoardDetector.locate(bitmap)
            ?: return ScanResult("Satranç tahtası aranıyor…", null, 0)

        if (!classifier.isTrained) {
            val trained = classifier.tryBootstrap(bitmap, board)
            return if (trained) {
                // Bootstrap görüntüsü başlangıç konumu olduğundan ilk kareyi referans al.
                // Böylece kalibrasyondan hemen sonra yapılan ilk hamle kaybolmaz.
                val initialGrid = classifier.classifyBoard(bitmap, board)
                lastGrid = initialGrid?.let { g -> Array(8) { r -> g[r].clone() } }
                sideToMove = Side.WHITE
                ScanResult("Tahta bulundu • Kalibrasyon tamamlandı (${board.confidence.toInt()}) • Konum okunuyor", null, 0)
            } else {
                ScanResult("Tahta bulundu • Kalibrasyon için başlangıç dizilimini göster", null, 0)
            }
        }

        val grid = classifier.classifyBoard(bitmap, board)
            ?: return ScanResult("Tahta örneklenemedi", null, 0)

        val previous = lastGrid
        if (previous != null) {
            val move = MoveAnalyzer.analyze(previous, grid)
            if (move.kind == MoveAnalyzer.Kind.UNKNOWN) {
                // Gürültü veya birden fazla belirsiz değişiklik varsa bu kareyi
                // onaylama; sonraki taramada aynı hamleyi yeniden değerlendirelim.
                return ScanResult("Tahta bulundu • Hamle doğrulanıyor…", null, countPieces(grid))
            }
            if (move.mover != null) {
                if (move.mover != sideToMove) {
                    return ScanResult("Tahta bulundu • Hamle sırası doğrulanıyor…", null, countPieces(grid))
                }
                sideToMove = if (move.mover == Side.WHITE) Side.BLACK else Side.WHITE
            }
        }

        val fen = FenBuilder.build(grid, classifier.bootstrapSide(), sideToMove)
        if (fen == null) {
            return ScanResult("Tahta bulundu • Taşlar okunuyor, konum henüz doğrulanamadı", null, countPieces(grid))
        }

        classifier.commitConfirmed(grid, bitmap, board)
        lastGrid = Array(8) { r -> grid[r].clone() }
        val recognized = countPieces(grid)
        val turn = if (sideToMove == Side.WHITE) "Beyaz" else "Siyah"
        return ScanResult("Tahta hazır • " + recognized + " taş • Sıra: " + turn, fen, recognized)
    }

    private fun countPieces(grid: Array<CharArray>): Int =
        grid.sumOf { row -> row.count { it != '.' && it != 0.toChar() } }
}

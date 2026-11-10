package com.chessclass.app

import android.content.Context
import android.graphics.Bitmap
import com.chessclass.app.vision.BoardDetector
import com.chessclass.app.vision.FenBuilder
import com.chessclass.app.vision.LiteRtSquareClassifier
import com.chessclass.app.vision.MoveAnalyzer
import com.chessclass.app.vision.PieceClassifier
import com.chessclass.app.vision.Side

object BoardVision {
    data class ScanResult(val summary: String, val fen: String?, val recognized: Int, val ai: Boolean = false)

    private val fallbackClassifier = PieceClassifier()
    private var aiClassifier: LiteRtSquareClassifier? = null
    private var lastGrid: Array<CharArray>? = null
    private var sideToMove = Side.WHITE
    private var lastFen: String? = null

    @Synchronized
    fun initialize(context: Context) {
        if (aiClassifier == null) {
            aiClassifier = LiteRtSquareClassifier(context.applicationContext)
        }
    }

    @Synchronized fun reset() {
        fallbackClassifier.reset()
        lastGrid = null
        sideToMove = Side.WHITE
        lastFen = null
    }

    fun inspect(bitmap: Bitmap): String = scan(bitmap).summary

    @Synchronized fun scan(bitmap: Bitmap): ScanResult {
        if (bitmap.width < 300 || bitmap.height < 300)
            return ScanResult("Ekran boyutu uygun değil", null, 0)

        val board = BoardDetector.locate(bitmap)
            ?: return ScanResult("Satranç tahtası aranıyor…", null, 0)

        val model = aiClassifier
        if (model?.isModelReady == true) {
            val grid = model.classifyBoard(bitmap, board.left, board.top, board.side)
                ?: return ScanResult("Yapay zekâ bazı karelerden emin değil; yeniden taranıyor…", null, 0, true)
            return acceptGrid(grid, true)
        }

        // Keep the legacy path available until a trained .tflite model has been
        // supplied. It still requires a standard starting-position bootstrap.
        if (!fallbackClassifier.isTrained) {
            val trained = fallbackClassifier.tryBootstrap(bitmap, board)
            return if (trained) {
                val initialGrid = fallbackClassifier.classifyBoard(bitmap, board)
                lastGrid = initialGrid?.let { g -> Array(8) { r -> g[r].clone() } }
                sideToMove = Side.WHITE
                lastFen = null
                ScanResult("Eski tanıma kalibre edildi • AI modeli henüz eklenmedi", null, 0)
            } else {
                ScanResult("Tahta bulundu • AI modeli yok; eski kalibrasyon başlangıç dizilimi bekliyor", null, 0)
            }
        }

        val grid = fallbackClassifier.classifyBoard(bitmap, board)
            ?: return ScanResult("Tahta örneklenemedi", null, 0)
        val result = acceptGrid(grid, false)
        if (result.fen != null) fallbackClassifier.commitConfirmed(grid, bitmap, board)
        return result
    }

    private fun acceptGrid(grid: Array<CharArray>, ai: Boolean): ScanResult {
        val previous = lastGrid
        var nextSideToMove = sideToMove
        if (previous != null) {
            val move = MoveAnalyzer.analyze(previous, grid)
            if (move.kind == MoveAnalyzer.Kind.UNKNOWN) {
                return ScanResult("Tahta bulundu • Hamle doğrulanıyor…", null, countPieces(grid), ai)
            }
            if (move.mover != null) {
                if (move.mover != sideToMove) {
                    return ScanResult("Tahta bulundu • Hamle sırası doğrulanıyor…", null, countPieces(grid), ai)
                }
                nextSideToMove = if (move.mover == Side.WHITE) Side.BLACK else Side.WHITE
            }
        }

        val validation = FenBuilder.validate(grid)
        if (!validation.ok) {
            return ScanResult(
                if (ai) "AI konumu okudu fakat satranç doğrulamasından geçmedi: ${validation.reason}"
                else "Taşlar okunuyor • Konum doğrulanamadı: ${validation.reason}",
                null, countPieces(grid), ai
            )
        }

        // The legacy classifier may be calibrated with black at the bottom.
        // Normalize that orientation before creating FEN. The AI model currently
        // assumes the standard white-at-bottom board orientation.
        val boardOrientation = if (ai) Side.WHITE
            else (fallbackClassifier.bootstrapSide() ?: Side.WHITE)
        val fen = FenBuilder.build(grid, boardOrientation, nextSideToMove)
        if (fen == null) return ScanResult("Konum FEN'e çevrilemedi", null, countPieces(grid), ai)

        // Commit turn/state only after the position has passed validation.
        sideToMove = nextSideToMove
        lastGrid = Array(8) { r -> grid[r].clone() }
        lastFen = fen
        return ScanResult(
            (if (ai) "AI tanıdı" else "Eski tanıma") + " • " + countPieces(grid) +
                " taş • Sıra: " + if (sideToMove == Side.WHITE) "Beyaz" else "Siyah",
            fen, countPieces(grid), ai
        )
    }

    private fun countPieces(grid: Array<CharArray>): Int =
        grid.sumOf { row -> row.count { it != '.' && it != 0.toChar() } }
}

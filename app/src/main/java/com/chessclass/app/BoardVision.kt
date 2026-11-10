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
    // Unknown until a real move is observed. A static mid-game screenshot does not
    // contain enough information to determine whose turn it is.
    private var sideToMove: Side? = null
    private var lastFen: String? = null

    @Synchronized
    fun initialize(context: Context) {
        if (aiClassifier == null) aiClassifier = LiteRtSquareClassifier(context.applicationContext)
    }

    @Synchronized fun reset() {
        fallbackClassifier.reset()
        lastGrid = null
        sideToMove = null
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
            // Calibrate from the first stable image, including games that start mid-position.
            if (lastGrid == null) {
                val validation = FenBuilder.validate(grid)
                if (!validation.ok) return ScanResult("AI konumu doğrulayamadı: ${validation.reason}", null, countPieces(grid), true)
                lastGrid = copyGrid(grid)
                return ScanResult("Tahta tanındı • İlk hamle bekleniyor", null, countPieces(grid), true)
            }
            return acceptGrid(grid, true)
        }

        // Legacy visual prototypes still need a standard starting-position bootstrap.
        if (!fallbackClassifier.isTrained) {
            val trained = fallbackClassifier.tryBootstrap(bitmap, board)
            return if (trained) {
                val initialGrid = fallbackClassifier.classifyBoard(bitmap, board)
                if (initialGrid == null) {
                    ScanResult("Kalibrasyon tamamlandı fakat taşlar okunamadı", null, 0)
                } else {
                    lastGrid = copyGrid(initialGrid)
                    sideToMove = null
                    lastFen = null
                    ScanResult("Eski tanıma kalibre edildi • İlk hamle bekleniyor", null, 0)
                }
            } else {
                ScanResult("Tahta bulundu • Eğitilmiş AI modeli yok; eski kalibrasyon başlangıç dizilimi bekliyor", null, 0)
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
            if (move.kind == MoveAnalyzer.Kind.NONE) {
                if (sideToMove == null) {
                    return ScanResult("Tahta tanındı • İlk hamle bekleniyor", null, countPieces(grid), ai)
                }
            } else if (move.kind == MoveAnalyzer.Kind.UNKNOWN || move.mover == null) {
                return ScanResult("Tahta bulundu • Hamle doğrulanıyor…", null, countPieces(grid), ai)
            } else {
                // The moving piece's colour determines the next side; do not assume
                // that the player at the bottom is always White.
                nextSideToMove = if (move.mover == Side.WHITE) Side.BLACK else Side.WHITE
            }
        } else {
            return ScanResult("Tahta tanındı • İlk hamle bekleniyor", null, countPieces(grid), ai)
        }

        val validation = FenBuilder.validate(grid)
        if (!validation.ok) {
            return ScanResult(
                if (ai) "AI konumu okudu fakat doğrulamadan geçmedi: ${validation.reason}"
                else "Taşlar okunuyor • Konum doğrulanamadı: ${validation.reason}",
                null, countPieces(grid), ai
            )
        }

        // BoardDetector returns screen top-left to bottom-right. The fallback
        // classifier records whether the board is rotated relative to standard FEN.
        val boardOrientation = if (ai) Side.WHITE
            else (fallbackClassifier.bootstrapSide() ?: Side.WHITE)
        val turn = nextSideToMove
            ?: return ScanResult("Hamle sırası henüz bilinmiyor", null, countPieces(grid), ai)
        val fen = FenBuilder.build(grid, boardOrientation, turn)
            ?: return ScanResult("Konum FEN'e çevrilemedi", null, countPieces(grid), ai)

        sideToMove = turn
        lastGrid = copyGrid(grid)
        lastFen = fen
        return ScanResult(
            (if (ai) "AI tanıdı" else "Eski tanıma") + " • " + countPieces(grid) +
                " taş • Sıra: " + if (turn == Side.WHITE) "Beyaz" else "Siyah",
            fen, countPieces(grid), ai
        )
    }

    private fun copyGrid(grid: Array<CharArray>): Array<CharArray> =
        Array(8) { r -> grid[r].clone() }

    private fun countPieces(grid: Array<CharArray>): Int =
        grid.sumOf { row -> row.count { it != '.' && it != 0.toChar() } }
}
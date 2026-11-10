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
    private var lastFen: String? = null
    // Unknown until a real move is observed. A static mid-game screenshot does not
    // contain enough information to determine whose turn it is.
    private var sideToMove: Side? = null
    private var aiBottomSide: Side? = null
    private var castlingRights: String? = null
    private var enPassantTarget: String = "-"

    @Synchronized
    fun initialize(context: Context) {
        if (aiClassifier == null) aiClassifier = LiteRtSquareClassifier(context.applicationContext)
    }

    @Synchronized fun reset() {
        fallbackClassifier.reset()
        lastGrid = null
        sideToMove = null
        aiBottomSide = null
        castlingRights = null
        enPassantTarget = "-"
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
                aiBottomSide = detectBottomSide(grid)
                castlingRights = "-" // Mid-game screenshots cannot prove historical castling rights.
                // A static screenshot does not reveal the real side to move. Still
                // provide an explicitly provisional analysis for the side at the
                // bottom so the overlay is not silent until the next board change.
                val provisionalFen = FenBuilder.build(
                    grid, aiBottomSide ?: Side.WHITE, aiBottomSide ?: Side.WHITE, "-", "-"
                )
                return ScanResult(
                    "Tahta tanındı • Geçici öneri (hamle sırası henüz doğrulanmadı)",
                    provisionalFen, countPieces(grid), true
                )
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
                    castlingRights = "KQkq"
                    enPassantTarget = "-"
                    sideToMove = null
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
        var observedMove = false
        if (previous != null) {
            val move = MoveAnalyzer.analyze(previous, grid)
            if (move.kind == MoveAnalyzer.Kind.NONE) {
                if (sideToMove == null) {
                    return ScanResult("Tahta tanındı • İlk hamle bekleniyor", null, countPieces(grid), ai)
                }
            } else if (move.kind == MoveAnalyzer.Kind.UNKNOWN || move.mover == null) {
                return ScanResult("Tahta bulundu • Hamle doğrulanıyor…", null, countPieces(grid), ai)
            } else {
                // Once a turn has been established, the same side cannot move twice
                // in succession. Reject animation noise or a misclassified board
                // instead of feeding a false position to Stockfish.
                if (sideToMove != null && move.mover != sideToMove) {
                    return ScanResult("Tahta değişti • Hamle sırası doğrulanıyor…", null, countPieces(grid), ai)
                }
                observedMove = true
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
        val boardOrientation = if (ai) (aiBottomSide ?: detectBottomSide(grid))
            else (fallbackClassifier.bootstrapSide() ?: Side.WHITE)
        val turn = nextSideToMove
            ?: return ScanResult("Hamle sırası henüz bilinmiyor", null, countPieces(grid), ai)
        if (observedMove) updateFenMetadata(previous, grid, boardOrientation)

        // Only calculate a move for the person whose pieces occupy the bottom half.
        // If the opponent just moved, commit that board state and wait for the player's turn.
        val playerSide = if (ai) (aiBottomSide ?: detectBottomSide(grid))
            else (fallbackClassifier.bootstrapSide() ?: detectBottomSide(grid))
        if (turn != playerSide) {
            sideToMove = turn
            lastGrid = copyGrid(grid)
            lastFen = null
            return ScanResult("Rakibin hamlesi algılandı • Senin sıran bekleniyor", null, countPieces(grid), ai)
        }

        val fen = FenBuilder.build(grid, boardOrientation, turn, castlingRights ?: "-", enPassantTarget)
            ?: return ScanResult("Konum FEN'e çevrilemedi", null, countPieces(grid), ai)

        sideToMove = turn
        lastGrid = copyGrid(grid)
        return ScanResult(
            (if (ai) "AI tanıdı" else "Eski tanıma") + " • " + countPieces(grid) +
                " taş • Sıra: " + if (turn == Side.WHITE) "Beyaz" else "Siyah",
            fen, countPieces(grid), ai
        )
    }



    private fun updateFenMetadata(previous: Array<CharArray>?, current: Array<CharArray>, orientation: Side) {
        if (previous == null) return
        val before = if (orientation == Side.BLACK) FenBuilder.flip(previous) else previous
        val after = if (orientation == Side.BLACK) FenBuilder.flip(current) else current
        var rights = castlingRights ?: "-"
        fun remove(token: String) { rights = rights.replace(token, "") }
        // A king leaving its original square permanently removes both rights.
        if (before[7][4] == 'K' && after[7][4] != 'K') { remove("K"); remove("Q") }
        if (before[0][4] == 'k' && after[0][4] != 'k') { remove("k"); remove("q") }
        // Moving or capturing an original corner rook removes that side's right.
        if (before[7][0] == 'R' && after[7][0] != 'R') remove("Q")
        if (before[7][7] == 'R' && after[7][7] != 'R') remove("K")
        if (before[0][0] == 'r' && after[0][0] != 'r') remove("q")
        if (before[0][7] == 'r' && after[0][7] != 'r') remove("k")
        castlingRights = rights.ifEmpty { "-" }

        enPassantTarget = "-"
        for (r in 0..7) for (file in 0..7) {
            val piece = before[r][file]
            if (piece == 'P' && after[r][file] == '.' && r - 2 >= 0 &&
                after[r - 2][file] == 'P' && before[r - 1][file] == '.') {
                enPassantTarget = "" + ('a' + file) + (8 - (r - 1))
                return
            }
            if (piece == 'p' && after[r][file] == '.' && r + 2 < 8 &&
                after[r + 2][file] == 'p' && before[r + 1][file] == '.') {
                enPassantTarget = "" + ('a' + file) + (8 - (r + 1))
                return
            }
        }
    }

    // Screen rows 4..7 are always the user's half. The majority piece colour
    // there determines whether the on-screen board is white-bottom or black-bottom.
    private fun detectBottomSide(grid: Array<CharArray>): Side {
        var white = 0
        var black = 0
        for (r in 4..7) for (f in 0..7) {
            val piece = grid[r][f]
            if (piece == '.' || piece == 0.toChar()) continue
            if (piece.isUpperCase()) white++ else black++
        }
        return if (black > white) Side.BLACK else Side.WHITE
    }

    private fun copyGrid(grid: Array<CharArray>): Array<CharArray> =
        Array(8) { r -> grid[r].clone() }

    private fun countPieces(grid: Array<CharArray>): Int =
        grid.sumOf { row -> row.count { it != '.' && it != 0.toChar() } }
}
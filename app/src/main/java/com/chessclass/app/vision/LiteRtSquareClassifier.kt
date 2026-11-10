package com.chessclass.app.vision

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * FENShot chess-tiles-v2 ONNX adapter.
 * Uses per-square probabilities and FENShot-style alignment arbitration:
 * several nearby board crops are classified and the strongest coherent read wins.
 */
class LiteRtSquareClassifier(context: Context) : AutoCloseable {
    data class BoardRead(
        val grid: Array<CharArray>,
        val confidences: FloatArray,
        val meanConfidence: Float,
        val minConfidence: Float,
        val offsetX: Int,
        val offsetY: Int
    )

    companion object {
        private const val MODEL_FILE = "chess-tiles-v2.onnx"
        private const val TILE_SIZE = 32
        private const val TILE_PIXELS = TILE_SIZE * TILE_SIZE
        private const val LABELS = "1KQRBNPkqrbnp"
        private const val MIN_USABLE_CONFIDENCE = 0.20f
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession? = try {
        context.assets.open(MODEL_FILE).use { input ->
            val bytes = input.readBytes()
            env.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
        }
    } catch (_: Exception) { null }

    val isModelReady: Boolean get() = session != null

    @Synchronized
    fun classifyBoard(bitmap: Bitmap, boardLeft: Int, boardTop: Int, boardSide: Int): BoardRead? {
        if (session == null || boardSide <= 0) return null
        val tile = boardSide / 8
        val shift = (tile / 4f).roundToInt().coerceAtLeast(1)
        // Center plus four small alignment corrections. The model's mean tile
        // confidence arbitrates between the crops, as in FENShot's recognizer.
        val offsets = arrayOf(0 to 0, -shift to 0, shift to 0, 0 to -shift, 0 to shift)
        var best: BoardRead? = null
        for ((dx, dy) in offsets) {
            val left = boardLeft + dx
            val top = boardTop + dy
            if (left < 0 || top < 0 || left + boardSide > bitmap.width || top + boardSide > bitmap.height) continue
            val read = classifyCrop(bitmap, left, top, boardSide, dx, dy) ?: continue
            val kings = read.grid.sumOf { row -> row.count { it == 'K' } } == 1 &&
                read.grid.sumOf { row -> row.count { it == 'k' } } == 1
            val bestKings = best?.let { candidate ->
                candidate.grid.sumOf { row -> row.count { it == 'K' } } == 1 &&
                    candidate.grid.sumOf { row -> row.count { it == 'k' } } == 1
            } ?: false
            val score = read.meanConfidence + if (kings) 0.04f else 0f
            val bestScore = best?.let { it.meanConfidence + if (bestKings) 0.04f else 0f } ?: -1f
            if (score > bestScore) best = read
        }
        return best?.takeIf { it.minConfidence >= MIN_USABLE_CONFIDENCE }
    }

    private fun classifyCrop(
        bitmap: Bitmap, left: Int, top: Int, side: Int, dx: Int, dy: Int
    ): BoardRead? {
        val model = session ?: return null
        val source = try { Bitmap.createBitmap(bitmap, left, top, side, side) } catch (_: Exception) { return null }
        val scaled = try { Bitmap.createScaledBitmap(source, 256, 256, true) } finally { source.recycle() }
        val pixels = IntArray(256 * 256)
        scaled.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        scaled.recycle()

        val input = FloatArray(64 * TILE_PIXELS)
        for (rank in 0 until 8) for (file in 0 until 8) {
            val tileIndex = rank * 8 + file
            val sourceY = (7 - rank) * TILE_SIZE
            for (y in 0 until TILE_SIZE) for (x in 0 until TILE_SIZE) {
                val p = pixels[(sourceY + y) * 256 + file * TILE_SIZE + x]
                val gray = (0.299f * ((p shr 16) and 255) +
                    0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) / 255f
                input[tileIndex * TILE_PIXELS + y * TILE_SIZE + x] = gray
            }
        }

        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(64, TILE_PIXELS.toLong()))
        try {
            model.run(mapOf(model.inputNames.first() to tensor)).use { result ->
                val raw = result[model.outputNames.first()].get().value
                val rows = raw as? Array<*> ?: return null
                if (rows.size != 64) return null
                val grid = Array(8) { CharArray(8) { '.' } }
                val confidences = FloatArray(64)
                for (rank in 0 until 8) for (file in 0 until 8) {
                    val index = rank * 8 + file
                    val scores = rows[index] as? FloatArray ?: return null
                    if (scores.size != LABELS.length || scores.any { !it.isFinite() }) return null
                    var bestIndex = 0
                    for (i in 1 until scores.size) if (scores[i] > scores[bestIndex]) bestIndex = i
                    confidences[index] = scores[bestIndex]
                    val label = LABELS[bestIndex]
                    grid[7 - rank][file] = if (label == '1') '.' else label
                }
                return BoardRead(
                    grid, confidences,
                    confidences.average().toFloat(),
                    confidences.minOrNull() ?: 0f, dx, dy
                )
            }
        } finally { tensor.close() }
    }

    override fun close() { session?.close() }
}

package com.chessclass.app.vision

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Android adapter for Fenshot chess-tiles-v2.onnx.
 * Model input: float32 [64,1024], grayscale tiles in A1..H8 order.
 * Model output: probs [64,13], labels "1KQRBNPkqrbnp".
 */
class LiteRtSquareClassifier(context: Context) : AutoCloseable {
    data class Prediction(val piece: Char, val confidence: Float)

    companion object {
        private const val MODEL_FILE = "chess-tiles-v2.onnx"
        private const val TILE_SIZE = 32
        private const val TILE_PIXELS = TILE_SIZE * TILE_SIZE
        private const val LABELS = "1KQRBNPkqrbnp"
        private const val CONFIDENCE_THRESHOLD = 0.55f
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession? = try {
        context.assets.open(MODEL_FILE).use { input ->
            val bytes = input.readBytes()
            env.createSession(bytes, OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            })
        }
    } catch (_: Exception) { null }

    val isModelReady: Boolean get() = session != null

    @Synchronized
    fun classifyBoard(bitmap: Bitmap, boardLeft: Int, boardTop: Int, boardSide: Int): Array<CharArray>? {
        val model = session ?: return null
        if (boardSide <= 0 || boardLeft < 0 || boardTop < 0 ||
            boardLeft + boardSide > bitmap.width || boardTop + boardSide > bitmap.height) return null

        val pixels = IntArray(256 * 256)
        val scaled = Bitmap.createBitmap(bitmap, boardLeft, boardTop, boardSide, boardSide)
            .let { source ->
                val resized = Bitmap.createScaledBitmap(source, 256, 256, true)
                if (resized !== source) source.recycle()
                resized
            }
        scaled.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        scaled.recycle()

        val input = FloatArray(64 * TILE_PIXELS)
        for (rank in 0 until 8) for (file in 0 until 8) {
            val tile = rank * 8 + file
            val sourceY = (7 - rank) * TILE_SIZE
            for (y in 0 until TILE_SIZE) for (x in 0 until TILE_SIZE) {
                val sx = file * TILE_SIZE + x
                val sy = sourceY + y
                val p = pixels[sy * 256 + sx]
                val gray = (0.299f * ((p shr 16) and 255) +
                    0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) / 255f
                input[tile * TILE_PIXELS + y * TILE_SIZE + x] = gray
            }
        }

        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(64, TILE_PIXELS.toLong()))
        try {
            val result = model.run(mapOf(model.inputNames.first() to tensor))
            result.use {
                val raw = it[model.outputNames.first()].get().value
                val rows = raw as? Array<*> ?: return null
                if (rows.size != 64) return null
                val grid = Array(8) { CharArray(8) { '.' } }
                for (rank in 0 until 8) for (file in 0 until 8) {
                    val modelIndex = rank * 8 + file
                    val scores = rows[modelIndex] as? FloatArray ?: return null
                    if (scores.size != LABELS.length || scores.any { !it.isFinite() }) return null
                    var best = 0
                    for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
                    if (scores[best] < CONFIDENCE_THRESHOLD) return null
                    val label = LABELS[best]
                    grid[7 - rank][file] = if (label == '1') '.' else label
                }
                return grid
            }
        } finally { tensor.close() }
    }

    override fun close() { session?.close() }
}

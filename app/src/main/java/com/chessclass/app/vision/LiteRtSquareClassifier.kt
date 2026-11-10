package com.chessclass.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * On-device 64-square classifier.
 *
 * Expected asset: assets/gx_chess_64.tflite
 * Input:  [1, 64, 64, 3] float32 RGB, values in [0, 1]
 * Output: [1, 13] float32 probabilities/logits in LABELS order.
 *
 * The trained model is intentionally not bundled until it has been trained
 * and validated on real Chess Clash screenshots.
 */
class LiteRtSquareClassifier(context: Context) : AutoCloseable {
    data class Prediction(val piece: Char, val confidence: Float)

    companion object {
        val LABELS = charArrayOf('.', 'P', 'N', 'B', 'R', 'Q', 'K',
            'p', 'n', 'b', 'r', 'q', 'k')
        private const val MODEL_FILE = "gx_chess_64.tflite"
        private const val INPUT_SIZE = 64
        private const val CLASS_COUNT = 13
        private const val CONFIDENCE_THRESHOLD = 0.72f
    }

    private val interpreter: Interpreter? = try {
        val fd = context.assets.openFd(MODEL_FILE)
        fd.use {
            FileInputStream(it.fileDescriptor).channel.use { channel ->
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
                Interpreter(mapped, Interpreter.Options().apply { setNumThreads(4) })
            }
        }
    } catch (_: Exception) {
        null
    }

    val isModelReady: Boolean get() = interpreter != null

    @Synchronized
    fun classifyBoard(bitmap: Bitmap, boardLeft: Int, boardTop: Int, boardSide: Int): Array<CharArray>? {
        val model = interpreter ?: return null
        if (boardSide <= 0) return null
        val grid = Array(8) { CharArray(8) { '.' } }
        val input = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val output = Array(1) { FloatArray(CLASS_COUNT) }

        for (rank in 0 until 8) {
            for (file in 0 until 8) {
                val left = boardLeft + file * boardSide / 8
                val top = boardTop + rank * boardSide / 8
                val right = boardLeft + (file + 1) * boardSide / 8
                val bottom = boardTop + (rank + 1) * boardSide / 8
                // Exclude a small border so square highlights and grid lines
                // have less influence on the piece classifier.
                val insetX = ((right - left) * 0.08f).toInt()
                val insetY = ((bottom - top) * 0.08f).toInt()
                val crop = Rect(left + insetX, top + insetY, right - insetX, bottom - insetY)
                if (crop.left < 0 || crop.top < 0 || crop.right > bitmap.width ||
                    crop.bottom > bitmap.height || crop.width() < 2 || crop.height() < 2) return null

                val square = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
                val scaled = Bitmap.createScaledBitmap(square, INPUT_SIZE, INPUT_SIZE, true)
                if (scaled !== square) square.recycle()
                val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
                scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
                scaled.recycle()

                input.clear()
                for (pixel in pixels) {
                    input.putFloat(((pixel shr 16) and 0xFF) / 255f)
                    input.putFloat(((pixel shr 8) and 0xFF) / 255f)
                    input.putFloat((pixel and 0xFF) / 255f)
                }
                input.rewind()
                output[0].fill(0f)
                model.run(input, output)

                var best = 0
                for (i in 1 until CLASS_COUNT) if (output[0][i] > output[0][best]) best = i
                val confidence = output[0][best]
                // Never invent a piece when the model is unsure.
                if (!confidence.isFinite() || confidence < CONFIDENCE_THRESHOLD) return null
                grid[rank][file] = LABELS[best]
            }
        }
        return grid
    }

    override fun close() {
        interpreter?.close()
    }
}

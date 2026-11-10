package com.chessclass.app

import android.content.Context
import java.io.File

class StockfishEngine(private val context: Context) {
    fun analyzeStartPosition(depth: Int = 12): String {
        val engineFile = File(context.filesDir, "stockfish")
        if (!engineFile.exists()) {
            context.assets.open("stockfish").use { input ->
                engineFile.outputStream().use { output -> input.copyTo(output) }
            }
            engineFile.setExecutable(true, true)
        }

        val process = ProcessBuilder(engineFile.absolutePath)
            .directory(context.filesDir)
            .redirectErrorStream(true)
            .start()

        return try {
            val writer = process.outputStream.bufferedWriter()
            val reader = process.inputStream.bufferedReader()
            writer.write("uci\n")
            writer.flush()

            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line == "uciok") break
            }

            writer.write("isready\n")
            writer.flush()
            while (true) {
                line = reader.readLine() ?: break
                if (line == "readyok") break
            }

            writer.write("position startpos\n")
            writer.write("go depth $depth\n")
            writer.flush()

            var bestMove = "Hamle bulunamadı."
            while (true) {
                line = reader.readLine() ?: break
                if (line.startsWith("info depth")) {
                    bestMove = line
                }
                if (line.startsWith("bestmove")) {
                    bestMove = line
                    break
                }
            }
            writer.write("quit\n")
            writer.flush()
            bestMove
        } finally {
            process.destroy()
        }
    }
}

package com.chessclass.app

import android.content.Context
import java.io.File

class StockfishEngine(private val context: Context) {
    fun analyzeStartPosition(depth: Int = 12): String {
        val engineFile = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (!engineFile.exists() || !engineFile.canExecute()) {
            return "Stockfish dosyası bulunamadı veya çalıştırma izni yok: ${engineFile.absolutePath}"
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

package com.chessclass.app

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class StockfishEngine(private val context: Context) {
    fun analyzeStartPosition(depth: Int = 12): String =
        analyzeFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", depth)

    fun analyzeFen(fen: String, depth: Int = 12): String {
        val engineFile = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (!engineFile.exists() || !engineFile.canExecute()) {
            return "Hata: Stockfish dosyası bulunamadı veya çalıştırma izni yok"
        }

        var process: Process? = null
        val timedOut = AtomicBoolean(false)
        var watchdog: Thread? = null
        return try {
            process = ProcessBuilder(engineFile.absolutePath)
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .start()
            val activeProcess = process
            watchdog = Thread {
                try {
                    Thread.sleep(12_000)
                    if (activeProcess.isAlive) {
                        timedOut.set(true)
                        activeProcess.destroyForcibly()
                    }
                } catch (_: InterruptedException) {
                    // Normal completion cancels the watchdog.
                }
            }.apply { isDaemon = true; start() }

            val writer = activeProcess.outputStream.bufferedWriter()
            val reader = activeProcess.inputStream.bufferedReader()
            writer.write("uci\n")
            writer.flush()
            var ready = false
            while (true) {
                val line = reader.readLine() ?: break
                if (line == "uciok") { ready = true; break }
            }

            if (!ready) {
                "Hata: Stockfish UCI başlatılamadı"
            } else {
                writer.write("setoption name Threads value 1\n")
                writer.write("setoption name Hash value 64\n")
                writer.write("isready\n")
                writer.flush()
                ready = false
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == "readyok") { ready = true; break }
                }

                if (!ready) {
                    "Hata: Stockfish hazır duruma gelemedi"
                } else {
                    writer.write("position fen $fen\n")
                    writer.write("go movetime 1400\n")
                    writer.flush()
                    var bestMove: String? = null
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("bestmove")) {
                            bestMove = line
                            break
                        }
                    }
                    when {
                        timedOut.get() -> "Hata: Stockfish analiz zaman aşımına uğradı"
                        bestMove != null -> bestMove
                        else -> "Hata: Stockfish hamle üretemedi"
                    }
                }
            }
        } catch (error: Exception) {
            "Hata: ${error.message ?: "Stockfish çalıştırılamadı"}"
        } finally {
            watchdog?.interrupt()
            process?.destroy()
            if (process?.isAlive == true) process?.destroyForcibly()
        }
    }
}
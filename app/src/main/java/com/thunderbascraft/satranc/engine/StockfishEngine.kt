package com.thunderbascraft.satranc.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException

/** Runs the bundled Stockfish binary as a separate process and talks UCI to it. */
class StockfishEngine(private val executable: File) : ChessEngine {

    constructor(context: Context) : this(File(context.applicationInfo.nativeLibraryDir, "libstockfish.so"))

    private val mutex = Mutex()
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null

    private fun send(cmd: String) {
        val w = input ?: throw IOException("engine not running")
        w.write(cmd); w.newLine(); w.flush()
    }

    private fun readUntil(prefix: String): String {
        val r = output ?: throw IOException("engine not running")
        while (true) {
            val line = r.readLine() ?: throw IOException("engine exited")
            if (line.startsWith(prefix)) return line
        }
    }

    private fun ensureStarted() {
        if (process?.isAlive == true) return
        val p = ProcessBuilder(executable.absolutePath).redirectErrorStream(true).start()
        process = p
        input = p.outputStream.bufferedWriter()
        output = p.inputStream.bufferedReader()
        send("uci")
        readUntil("uciok")
        send("setoption name Threads value 1")
        send("setoption name Hash value 16")
        send("isready")
        readUntil("readyok")
    }

    override suspend fun bestMove(startFen: String, moves: List<String>, level: Level): String? =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    ensureStarted()
                    send("ucinewgame")
                    send("setoption name Skill Level value ${level.skill}")
                    val pos = buildString {
                        append("position fen ").append(startFen)
                        if (moves.isNotEmpty()) append(" moves ").append(moves.joinToString(" "))
                    }
                    send(pos)
                    send("isready")
                    readUntil("readyok")
                    send("go depth ${level.depth} movetime ${level.moveTimeMs}")
                    val best = readUntil("bestmove").split(" ").getOrNull(1)
                    if (best == null || best == "(none)") null else best
                } catch (e: IOException) {
                    shutdown()
                    throw e
                }
            }
        }

    private fun shutdown() {
        runCatching { send("quit") }
        runCatching { input?.close() }
        process?.destroy()
        process = null; input = null; output = null
    }

    override fun close() = shutdown()
}

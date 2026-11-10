package com.thunderbascraft.satranc.engine

import android.content.Context
import com.thunderbascraft.satranc.chess.Evaluation
import com.thunderbascraft.satranc.chess.UciParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException

/** Runs the bundled Stockfish binary as a separate process and talks UCI to it. */
class StockfishEngine(
    private val executable: File,
    /** NNUE network for engines built without an embedded one (Stockfish 19). */
    private val evalFile: File? = null,
) : ChessEngine {

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
        evalFile?.let { send("setoption name EvalFile value ${it.absolutePath}") }
        send("isready")
        readUntil("readyok")
    }

    /** Reads lines up to and including the `bestmove` line. */
    private fun readSearch(): List<String> {
        val r = output ?: throw IOException("engine not running")
        val lines = ArrayList<String>()
        while (true) {
            val line = r.readLine() ?: throw IOException("engine exited")
            if (line.startsWith("info") && " score " in line || line.startsWith("bestmove")) lines.add(line)
            if (line.startsWith("bestmove")) return lines
        }
    }

    private fun positionCommand(startFen: String, moves: List<String>) = buildString {
        append("position fen ").append(startFen)
        if (moves.isNotEmpty()) append(" moves ").append(moves.joinToString(" "))
    }

    /** Runs [block] on the engine process under the mutex, restarting it after I/O errors. */
    private suspend fun <T> withEngine(block: () -> T): T =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    ensureStarted()
                    block()
                } catch (e: IOException) {
                    shutdown()
                    throw e
                }
            }
        }

    override suspend fun bestMove(startFen: String, moves: List<String>, level: Level): String? = withEngine {
        send("ucinewgame")
        send("setoption name Skill Level value ${level.skill}")
        send(positionCommand(startFen, moves))
        send("isready")
        readUntil("readyok")
        send("go depth ${level.depth} movetime ${level.moveTimeMs}")
        UciParser.parseBestMove(readUntil("bestmove"))
    }

    override suspend fun evaluate(startFen: String, moves: List<String>, moveTimeMs: Long): Evaluation = withEngine {
        send("setoption name Skill Level value 20")
        send(positionCommand(startFen, moves))
        send("isready")
        readUntil("readyok")
        send("go movetime $moveTimeMs")
        UciParser.parseSearch(readSearch())
    }

    private fun shutdown() {
        runCatching { send("quit") }
        runCatching { input?.close() }
        process?.destroy()
        process = null; input = null; output = null
    }

    override fun close() = shutdown()
}

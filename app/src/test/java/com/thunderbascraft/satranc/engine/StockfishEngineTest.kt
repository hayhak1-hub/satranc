package com.thunderbascraft.satranc.engine

import com.thunderbascraft.satranc.chess.Game
import com.thunderbascraft.satranc.chess.Position
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Talks to a host build of the bundled Stockfish. Runs only when the
 * STOCKFISH_PATH environment variable points to such a binary.
 */
class StockfishEngineTest {
    private val exe = System.getenv("STOCKFISH_PATH")?.let(::File)

    @Test fun playsLegalMovesAtEveryLevel() = runBlocking {
        assumeTrue(exe?.canExecute() == true)
        val engine = StockfishEngine(exe!!)
        try {
            for (level in Level.entries) {
                val game = Game()
                repeat(6) {
                    val uci = engine.bestMove(Position.START_FEN, game.moves.map { it.move.uci }, level)
                    assertTrue("$level returned illegal $uci", uci != null && game.playUci(uci))
                }
            }
        } finally {
            engine.close()
        }
    }

    @Test fun findsMateInOneAtTopLevel() = runBlocking {
        assumeTrue(exe?.canExecute() == true)
        val engine = StockfishEngine(exe!!)
        try {
            val fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1"
            val game = Game(fen)
            assertTrue(game.playUci(engine.bestMove(fen, emptyList(), Level.MASTER)!!))
            assertTrue(game.result != null)
        } finally {
            engine.close()
        }
    }

    @Test fun returnsNullWhenMated() = runBlocking {
        assumeTrue(exe?.canExecute() == true)
        val engine = StockfishEngine(exe!!)
        try {
            assertNull(engine.bestMove("4R2k/6pp/8/8/8/8/8/6K1 b - - 0 1", emptyList(), Level.CASUAL))
        } finally {
            engine.close()
        }
    }
}

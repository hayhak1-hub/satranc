package com.thunderbascraft.satranc.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisTest {

    @Test fun classifiesByLoss() {
        assertEquals(MoveQuality.GOOD, Analysis.classify(0))
        assertEquals(MoveQuality.GOOD, Analysis.classify(49))
        assertEquals(MoveQuality.INACCURACY, Analysis.classify(50))
        assertEquals(MoveQuality.INACCURACY, Analysis.classify(99))
        assertEquals(MoveQuality.MISTAKE, Analysis.classify(100))
        assertEquals(MoveQuality.MISTAKE, Analysis.classify(299))
        assertEquals(MoveQuality.BLUNDER, Analysis.classify(300))
    }

    @Test fun convertsToWhitePointOfView() {
        assertEquals(35, Analysis.whitePov(Evaluation(35, null, null), Side.WHITE))
        assertEquals(-35, Analysis.whitePov(Evaluation(35, null, null), Side.BLACK))
        assertEquals(1500, Analysis.whitePov(Evaluation(4000, null, null), Side.WHITE))
        assertEquals(9997, Analysis.whitePov(Evaluation(null, 3, null), Side.WHITE))
        assertEquals(-9997, Analysis.whitePov(Evaluation(null, 3, null), Side.BLACK))
        assertEquals(-9998, Analysis.whitePov(Evaluation(null, -2, null), Side.WHITE))
        assertEquals(-10000, Analysis.whitePov(Evaluation(null, 0, null), Side.WHITE))
        assertEquals(0, Analysis.whitePov(Evaluation(null, null, null), Side.WHITE))
    }

    private fun game(vararg san: String): Game = Game().also { g -> san.forEach { require(g.playSan(it)) } }

    @Test fun flagsBlunderForTheMover() {
        // 1. e4 e5 2. Qh5 Ke7?? (engine evals are made up, side-to-move point of view).
        val g = game("e4", "e5", "Qh5", "Ke7")
        val evals = listOf(
            Evaluation(20, null, "e2e4"),   // start, white to move
            Evaluation(-25, null, "e7e5"),  // after e4, black to move
            Evaluation(30, null, "g1f3"),   // after e5, white to move
            Evaluation(-10, null, "b8c6"),  // after Qh5, black to move: white is slightly better
            Evaluation(600, null, "h5e5"),  // after Ke7, white to move: white is winning
        )
        val a = Analysis.analyse(g.positionsHistory, g.moves, evals)
        assertEquals(listOf(20, 25, 30, 10, 600), a.evals)
        assertEquals(MoveQuality.BEST, a.moves[0].quality)
        assertEquals(MoveQuality.BEST, a.moves[1].quality)
        assertEquals(MoveQuality.GOOD, a.moves[2].quality) // 30 -> 10: lost 20
        val ke7 = a.moves[3]
        assertEquals(Side.BLACK, ke7.mover)
        assertEquals(590, ke7.loss) // black went from -10 to -600
        assertEquals(MoveQuality.BLUNDER, ke7.quality)
        assertEquals("Nc6", ke7.bestMoveSan)

        val black = a.summary(Side.BLACK)
        assertEquals(SideSummary(inaccuracies = 0, mistakes = 0, blunders = 1, averageLoss = 298), black)
        assertEquals(0, a.summary(Side.WHITE).blunders)
    }

    @Test fun neverFlagsTheEnginesBestMove() {
        val g = game("e4")
        val evals = listOf(Evaluation(50, null, "e2e4"), Evaluation(400, null, "e7e5"))
        val m = Analysis.analyse(g.positionsHistory, g.moves, evals).moves.single()
        assertEquals(MoveQuality.BEST, m.quality)
    }

    @Test fun doesNotFlagSlowerWinInDecidedPosition() {
        val g = game("e4")
        // White had mate in 2 but is still +12 after the move: not a blunder.
        val evals = listOf(Evaluation(null, 2, "d1h5"), Evaluation(-1200, null, null))
        val m = Analysis.analyse(g.positionsHistory, g.moves, evals).moves.single()
        assertEquals(MoveQuality.GOOD, m.quality)
    }

    @Test fun partialEvaluationsOnlyClassifyCompletedMoves() {
        val g = game("e4", "e5", "Nf3")
        val evals = listOf(Evaluation(20, null, "e2e4"), Evaluation(-20, null, "e7e5"))
        val a = Analysis.analyse(g.positionsHistory, g.moves, evals)
        assertEquals(2, a.evals.size)
        assertEquals(1, a.moves.size)
    }

    @Test fun terminalPositions() {
        val mate = Position.fromFen("4R2k/6pp/8/8/8/8/8/6K1 b - - 0 1")
        assertEquals(Evaluation(null, 0, null), Analysis.terminalEvaluation(mate))
        val stalemate = Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(Evaluation(0, null, null), Analysis.terminalEvaluation(stalemate))
        assertNull(Analysis.terminalEvaluation(Position.start()))
    }

    @Test fun parsesStockfishInfoLines() {
        assertEquals(34 to null, UciParser.parseScore(
            "info depth 12 seldepth 16 multipv 1 score cp 34 nodes 120394 nps 900000 tbhits 0 time 133 pv e2e4 e7e5"))
        assertEquals(null to -3, UciParser.parseScore("info depth 20 score mate -3 nodes 1 pv h7h6"))
        assertNull(UciParser.parseScore("info depth 9 score cp 50 lowerbound nodes 1000"))
        assertNull(UciParser.parseScore("info string NNUE disabled"))
        assertNull(UciParser.parseScore("readyok"))
        assertEquals("g1f3", UciParser.parseBestMove("bestmove g1f3 ponder d7d5"))
        assertNull(UciParser.parseBestMove("bestmove (none)"))
    }

    @Test fun parsesWholeSearch() {
        val out = listOf(
            "info depth 1 seldepth 1 multipv 1 score cp 90 nodes 20 pv e2e4",
            "info depth 2 seldepth 2 multipv 1 score cp 40 lowerbound nodes 60 pv d2d4",
            "info depth 2 seldepth 2 multipv 1 score cp 35 nodes 80 pv d2d4 d7d5",
            "info depth 3 currmove e2e4 currmovenumber 1",
            "bestmove d2d4 ponder d7d5",
        )
        assertEquals(Evaluation(35, null, "d2d4"), UciParser.parseSearch(out))
        val mated = listOf("info depth 0 score mate 0", "bestmove (none)")
        assertEquals(Evaluation(null, 0, null), UciParser.parseSearch(mated))
    }
}
